/*
 * Copyright 2019 The Android Open Source Project
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
package androidx.navigation

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.DEFAULT_ARGS_KEY
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.SAVED_STATE_REGISTRY_OWNER_KEY
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.SavedStateViewModelFactory
import androidx.lifecycle.VIEW_MODEL_STORE_OWNER_KEY
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.enableSavedStateHandles
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.navigation.serialization.decodeArguments
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import java.util.UUID
import kotlinx.serialization.serializer

/**
 * Representation of an entry in the back stack of a [androidx.navigation.NavController]. The
 * [Lifecycle], [ViewModelStore], and [SavedStateRegistry] provided via this object are valid for
 * the lifetime of this destination on the back stack: when this destination is popped off the back
 * stack, the lifecycle will be destroyed, state will no longer be saved, and ViewModels will be
 * cleared.
 */
public class NavBackStackEntry
private constructor(
    private val context: Context?,
    /**
     * The destination associated with this entry
     *
     * @return The destination that is currently visible to users
     */
    @set:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) public var destination: NavDestination,
    private val immutableArgs: Bundle? = null,
    private var hostLifecycleState: Lifecycle.State = Lifecycle.State.CREATED,
    private val viewModelStoreProvider: NavViewModelStoreProvider? = null,
    /**
     * The unique ID that serves as the identity of this entry
     *
     * @return the unique ID of this entry
     */
    public val id: String = UUID.randomUUID().toString(),
    private val savedState: Bundle? = null
) :
    LifecycleOwner,
    ViewModelStoreOwner,
    HasDefaultViewModelProviderFactory,
    SavedStateRegistryOwner {

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    constructor(
        entry: NavBackStackEntry,
        arguments: Bundle? = entry.arguments
    ) : this(
        entry.context,
        entry.destination,
        arguments,
        entry.hostLifecycleState,
        entry.viewModelStoreProvider,
        entry.id,
        entry.savedState
    ) {
        hostLifecycleState = entry.hostLifecycleState
        maxLifecycle = entry.maxLifecycle
    }

    public companion object {
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public fun create(
            context: Context?,
            destination: NavDestination,
            arguments: Bundle? = null,
            hostLifecycleState: Lifecycle.State = Lifecycle.State.CREATED,
            viewModelStoreProvider: NavViewModelStoreProvider? = null,
            id: String = UUID.randomUUID().toString(),
            savedState: Bundle? = null
        ): NavBackStackEntry =
            NavBackStackEntry(
                context,
                destination,
                arguments,
                hostLifecycleState,
                viewModelStoreProvider,
                id,
                savedState
            )
    }

    private var _lifecycle = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private var savedStateRegistryAttached = false
    private val defaultFactory by lazy {
        SavedStateViewModelFactory((context?.applicationContext as? Application), this, arguments)
    }

    /**
     * The arguments used for this entry. Note that the arguments of a NavBackStackEntry are
     * immutable and defined when you `navigate()` to the destination - changes you make to this
     * Bundle will not be reflected in future calls to this property.
     *
     * @return The arguments used when this entry was created
     */
    public val arguments: Bundle?
        get() =
            if (immutableArgs == null) {
                null
            } else {
                Bundle(immutableArgs)
            }

    /** The [SavedStateHandle] for this entry. */
    @get:MainThread
    public val savedStateHandle: SavedStateHandle by lazy {
        check(savedStateRegistryAttached) {
            "You cannot access the NavBackStackEntry's SavedStateHandle until it is added to " +
                "the NavController's back stack (i.e., the Lifecycle of the NavBackStackEntry " +
                "reaches the CREATED state)."
        }
        check(lifecycle.currentState != Lifecycle.State.DESTROYED) {
            "You cannot access the NavBackStackEntry's SavedStateHandle after the " +
                "NavBackStackEntry is destroyed."
        }
        ViewModelProvider(this, NavResultSavedStateFactory(this))
            .get(SavedStateViewModel::class.java)
            .handle
    }

    /**
     * {@inheritDoc}
     *
     * If the [androidx.navigation.NavHost] has not called
     * [androidx.navigation.NavHostController.setLifecycleOwner], the Lifecycle will be capped at
     * [Lifecycle.State.CREATED].
     */
    override val lifecycle: Lifecycle
        get() = _lifecycle

    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @set:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public var maxLifecycle: Lifecycle.State = Lifecycle.State.INITIALIZED
        set(maxState) {
            field = maxState
            updateState()
        }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun handleLifecycleEvent(event: Lifecycle.Event) {
        hostLifecycleState = event.targetState
        updateState()
    }

    /** Update the state to be the lower of the two constraints: */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun updateState() {
        if (!savedStateRegistryAttached) {
            savedStateRegistryController.performAttach()
            savedStateRegistryAttached = true
            if (viewModelStoreProvider != null) {
                enableSavedStateHandles()
            }
            // Perform the restore just once, the first time updateState() is called
            // and specifically *before* we move up the Lifecycle
            savedStateRegistryController.performRestore(savedState)
        }
        if (hostLifecycleState.ordinal < maxLifecycle.ordinal) {
            _lifecycle.currentState = hostLifecycleState
        } else {
            _lifecycle.currentState = maxLifecycle
        }
    }

    public override val viewModelStore: ViewModelStore
        /**
         * {@inheritDoc}
         *
         * @throws IllegalStateException if called before the [lifecycle] has moved to
         *   [Lifecycle.State.CREATED] or before the [androidx.navigation.NavHost] has called
         *   [androidx.navigation.NavHostController.setViewModelStore].
         */
        get() {
            check(savedStateRegistryAttached) {
                "You cannot access the NavBackStackEntry's ViewModels until it is added to " +
                    "the NavController's back stack (i.e., the Lifecycle of the " +
                    "NavBackStackEntry reaches the CREATED state)."
            }
            check(lifecycle.currentState != Lifecycle.State.DESTROYED) {
                "You cannot access the NavBackStackEntry's ViewModels after the " +
                    "NavBackStackEntry is destroyed."
            }
            checkNotNull(viewModelStoreProvider) {
                "You must call setViewModelStore() on your NavHostController before " +
                    "accessing the ViewModelStore of a navigation graph."
            }
            return viewModelStoreProvider.getViewModelStore(id)
        }

    override val defaultViewModelProviderFactory: ViewModelProvider.Factory = defaultFactory

    override val defaultViewModelCreationExtras: CreationExtras
        get() {
            val extras = MutableCreationExtras()
            (context?.applicationContext as? Application)?.let { application ->
                extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] = application
            }
            extras[SAVED_STATE_REGISTRY_OWNER_KEY] = this
            extras[VIEW_MODEL_STORE_OWNER_KEY] = this
            arguments?.let { args -> extras[DEFAULT_ARGS_KEY] = args }
            return extras
        }

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun saveState(outBundle: Bundle) {
        savedStateRegistryController.performSave(outBundle)
    }

    @Suppress("DEPRECATION")
    override fun equals(other: Any?): Boolean {
        if (other == null || other !is NavBackStackEntry) return false
        return id == other.id &&
            destination == other.destination &&
            lifecycle == other.lifecycle &&
            savedStateRegistry == other.savedStateRegistry &&
            (immutableArgs == other.immutableArgs ||
                immutableArgs?.keySet()?.all {
                    immutableArgs.get(it) == other.immutableArgs?.get(it)
                } == true)
    }

    @Suppress("DEPRECATION")
    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + destination.hashCode()
        immutableArgs?.keySet()?.forEach { result = 31 * result + immutableArgs.get(it).hashCode() }
        result = 31 * result + lifecycle.hashCode()
        result = 31 * result + savedStateRegistry.hashCode()
        return result
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append(javaClass.simpleName)
        sb.append("($id)")
        sb.append(" destination=")
        sb.append(destination)
        return sb.toString()
    }

    /** Used to create the {SavedStateViewModel} */
    private class NavResultSavedStateFactory(owner: SavedStateRegistryOwner) :
        AbstractSavedStateViewModelFactory(owner, null) {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(
            key: String,
            modelClass: Class<T>,
            handle: SavedStateHandle
        ): T {
            return SavedStateViewModel(handle) as T
        }
    }

    private class SavedStateViewModel(val handle: SavedStateHandle) : ViewModel()
}

/**
 * Returns route as an object of type [T]
 *
 * Extrapolates arguments from [NavBackStackEntry.arguments] and recreates object [T]
 *
 * @param [T] the entry's [NavDestination.route] as a [KClass]
 * @return A new instance of this entry's [NavDestination.route] as an object of type [T]
 */
public inline fun <reified T> NavBackStackEntry.toRoute(): T {
    val bundle = arguments ?: Bundle()
    val typeMap = destination.arguments.mapValues { it.value.type }
    return serializer<T>().decodeArguments(bundle, typeMap)
}
