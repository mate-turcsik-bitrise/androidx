/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.room

import androidx.lifecycle.LiveData
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteConnection
import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A LiveData implementation that closely works with [InvalidationTracker] to implement database
 * drive [androidx.lifecycle.LiveData] queries that are strongly hold as long as they are active.
 *
 * We need this extra handling for [androidx.lifecycle.LiveData] because when they are observed
 * forever, there is no [androidx.lifecycle.Lifecycle] that will keep them in memory but they should
 * stay. We cannot add-remove observer in [LiveData.onActive], [LiveData.onInactive] because that
 * would mean missing changes in between or doing an extra query on every UI rotation.
 *
 * This [LiveData] keeps a weak observer to the [InvalidationTracker] but it is hold strongly by the
 * [InvalidationTracker] as long as it is active.
 */
internal class RoomTrackingLiveData<T>(
    private val database: RoomDatabase,
    private val container: InvalidationLiveDataContainer,
    private val inTransaction: Boolean,
    private val callableFunction: Callable<T?>?,
    private val lambdaFunction: ((SQLiteConnection) -> T?)?,
    tableNames: Array<out String>
) : LiveData<T>() {
    private val observer: InvalidationTracker.Observer =
        object : InvalidationTracker.Observer(tableNames) {
            override fun onInvalidated(tables: Set<String>) {
                database.getCoroutineScope().launch { invalidated() }
            }
        }
    private val invalid = AtomicBoolean(true)
    private val computing = AtomicBoolean(false)
    private val registeredObserver = AtomicBoolean(false)

    private suspend fun refresh() {
        if (registeredObserver.compareAndSet(false, true)) {
            database.invalidationTracker.subscribe(
                InvalidationTracker.WeakObserver(
                    database.invalidationTracker,
                    database.getCoroutineScope(),
                    observer
                )
            )
        }
        var computed: Boolean
        do {
            computed = false
            // compute can happen only in 1 thread but no reason to lock others.
            if (computing.compareAndSet(false, true)) {
                // as long as it is invalid, keep computing.
                try {
                    var value: T? = null
                    while (invalid.compareAndSet(true, false)) {
                        computed = true
                        try {
                            value =
                                if (callableFunction != null) {
                                    withContext(
                                        if (inTransaction) {
                                            database.getTransactionContext()
                                        } else {
                                            database.getQueryContext()
                                        }
                                    ) {
                                        callableFunction.call()
                                    }
                                } else if (lambdaFunction != null) {
                                    performSuspending(database, true, inTransaction, lambdaFunction)
                                } else {
                                    error("Both callable and lambda functions are null")
                                }
                        } catch (e: Exception) {
                            throw RuntimeException(
                                "Exception while computing database live data.",
                                e
                            )
                        }
                    }
                    if (computed) {
                        postValue(value)
                    }
                } finally {
                    // release compute lock
                    computing.set(false)
                }
            }
            // check invalid after releasing compute lock to avoid the following scenario.
            // Thread A runs compute()
            // Thread A checks invalid, it is false
            // Main thread sets invalid to true
            // Thread B runs, fails to acquire compute lock and skips
            // Thread A releases compute lock
            // We've left invalid in set state. The check below recovers.
        } while (computed && invalid.get())
    }

    private suspend fun invalidated() {
        val isActive = hasActiveObservers()
        if (invalid.compareAndSet(false, true)) {
            if (isActive) {
                refresh()
            }
        }
    }

    override fun onActive() {
        super.onActive()
        container.onActive(this)
        database.getCoroutineScope().launch { refresh() }
    }

    override fun onInactive() {
        super.onInactive()
        container.onInactive(this)
    }
}
