/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.camera.camera2.pipe.testing

import android.hardware.camera2.CaptureRequest
import android.view.Surface
import androidx.annotation.GuardedBy
import androidx.camera.camera2.pipe.CameraId
import androidx.camera.camera2.pipe.CaptureSequence.CaptureSequenceListener
import androidx.camera.camera2.pipe.CaptureSequenceProcessor
import androidx.camera.camera2.pipe.Metadata
import androidx.camera.camera2.pipe.Request
import androidx.camera.camera2.pipe.RequestMetadata
import androidx.camera.camera2.pipe.RequestNumber
import androidx.camera.camera2.pipe.RequestTemplate
import androidx.camera.camera2.pipe.StreamId
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout

/**
 * Fake implementation of a [CaptureSequenceProcessor] that passes events to a [Channel].
 *
 * This allows kotlin tests to check sequences of interactions that dispatch in the background
 * without blocking between events.
 */
public class FakeCaptureSequenceProcessor(
    private val cameraId: CameraId = CameraId("test-camera"),
    private val defaultTemplate: RequestTemplate = RequestTemplate(1)
) : CaptureSequenceProcessor<Request, FakeCaptureSequence> {
    private val lock = Any()
    private val sequenceIds = atomic(0)
    private val eventChannel = Channel<Event>(Channel.UNLIMITED)
    private val requestCounter = atomic(0L)

    @GuardedBy("lock") private var pendingSequence: CompletableDeferred<FakeCaptureSequence>? = null

    @GuardedBy("lock") private val queue: MutableList<FakeCaptureSequence> = mutableListOf()

    @GuardedBy("lock") private var repeatingRequestSequence: FakeCaptureSequence? = null

    @GuardedBy("lock") private var _rejectRequests = false

    public var rejectRequests: Boolean
        get() = synchronized(lock) { _rejectRequests }
        set(value) {
            synchronized(lock) { _rejectRequests = value }
        }

    private var _surfaceMap: Map<StreamId, Surface> = emptyMap()
    public var surfaceMap: Map<StreamId, Surface>
        get() = synchronized(lock) { _surfaceMap }
        set(value) =
            synchronized(lock) {
                _surfaceMap = value
                println("Configured surfaceMap for $this")
            }

    override fun build(
        isRepeating: Boolean,
        requests: List<Request>,
        defaultParameters: Map<*, Any?>,
        requiredParameters: Map<*, Any?>,
        listeners: List<Request.Listener>,
        sequenceListener: CaptureSequenceListener
    ): FakeCaptureSequence? {
        return buildFakeCaptureSequence(
            repeating = isRepeating,
            requests,
            defaultParameters,
            requiredParameters,
            listeners
        )
    }

    override fun submit(captureSequence: FakeCaptureSequence): Int {
        println("submit $captureSequence")
        synchronized(lock) {
            if (rejectRequests) {
                check(
                    eventChannel
                        .trySend(Event(requestSequence = captureSequence, rejected = true))
                        .isSuccess
                )
                return -1
            }
            queue.add(captureSequence)
            if (captureSequence.repeating) {
                repeatingRequestSequence = captureSequence
            }
            check(
                eventChannel
                    .trySend(Event(requestSequence = captureSequence, submit = true))
                    .isSuccess
            )
            // If there is a non-null pending sequence, make sure we complete it here.
            pendingSequence?.also {
                pendingSequence = null
                it.complete(captureSequence)
            }
            return sequenceIds.incrementAndGet()
        }
    }

    override fun abortCaptures() {
        val requestSequencesToAbort: List<FakeCaptureSequence>
        synchronized(lock) {
            requestSequencesToAbort = queue.toList()
            queue.clear()
            check(eventChannel.trySend(Event(abort = true)).isSuccess)
        }
        for (sequence in requestSequencesToAbort) {
            sequence.invokeOnSequenceAborted()
        }
    }

    override fun stopRepeating() {
        val requestSequence =
            synchronized(lock) {
                check(eventChannel.trySend(Event(stop = true)).isSuccess)
                repeatingRequestSequence.also { repeatingRequestSequence = null }
            }
        requestSequence?.invokeOnSequenceAborted()
    }

    override fun close() {
        synchronized(lock) {
            rejectRequests = true
            check(eventChannel.trySend(Event(close = true)).isSuccess)
        }
    }

    /** Get the next event from queue with an option to specify a timeout for tests. */
    public suspend fun nextEvent(timeMillis: Long = 500): Event =
        withTimeout(timeMillis) { eventChannel.receive() }

    public suspend fun nextRequestSequence(): FakeCaptureSequence {
        while (true) {
            val pending: Deferred<FakeCaptureSequence>
            synchronized(lock) {
                var sequence = queue.removeFirstOrNull()
                if (sequence == null) {
                    sequence = repeatingRequestSequence
                }
                if (sequence != null) {
                    return sequence
                }

                if (pendingSequence == null) {
                    pendingSequence = CompletableDeferred()
                }
                pending = pendingSequence!!
            }

            pending.await()
        }
    }

    private fun buildFakeCaptureSequence(
        repeating: Boolean,
        requests: List<Request>,
        defaultParameters: Map<*, Any?>,
        requiredParameters: Map<*, Any?>,
        defaultListeners: List<Request.Listener>,
    ): FakeCaptureSequence? {
        val surfaceMap = surfaceMap
        if (surfaceMap.isEmpty()) {
            println("No surfaces configured for $this! Cannot build CaptureSequence for $requests")
            return null
        }

        val requestInfoMap = mutableMapOf<Request, RequestMetadata>()
        val requestInfoList = mutableListOf<RequestMetadata>()
        for (request in requests) {
            val captureParameters = mutableMapOf<CaptureRequest.Key<*>, Any?>()
            val metadataParameters = mutableMapOf<Metadata.Key<*>, Any?>()
            for ((k, v) in defaultParameters) {
                if (k != null) {
                    if (k is CaptureRequest.Key<*>) {
                        captureParameters[k] = v
                    } else if (k is Metadata.Key<*>) {
                        metadataParameters[k] = v
                    }
                }
            }
            for ((k, v) in request.parameters) {
                captureParameters[k] = v
            }
            for ((k, v) in requiredParameters) {
                if (k != null) {
                    if (k is CaptureRequest.Key<*>) {
                        captureParameters[k] = v
                    } else if (k is Metadata.Key<*>) {
                        metadataParameters[k] = v
                    }
                }
            }

            val requestNumber = RequestNumber(requestCounter.incrementAndGet())
            val streamMap = mutableMapOf<StreamId, Surface>()
            var hasSurface = false
            for (stream in request.streams) {
                val surface = surfaceMap[stream]
                if (surface == null) {
                    println("Failed to find surface for $stream on $request")
                    continue
                }
                hasSurface = true
                streamMap[stream] = surface
            }

            if (!hasSurface) {
                println("No surfaces configured for $request! Cannot build CaptureSequence.")
                return null
            }

            val requestMetadata =
                FakeRequestMetadata(
                    request = request,
                    requestParameters = captureParameters,
                    metadata = metadataParameters,
                    template = request.template ?: defaultTemplate,
                    streams = streamMap,
                    repeating = repeating,
                    requestNumber = requestNumber
                )
            requestInfoList.add(requestMetadata)
            requestInfoMap[request] = requestMetadata
        }

        // Copy maps / lists for tests.
        return FakeCaptureSequence(
            repeating = repeating,
            cameraId = cameraId,
            captureRequestList = requests.toList(),
            captureMetadataList = requestInfoList,
            requestMetadata = requestInfoMap,
            defaultParameters = defaultParameters.toMap(),
            requiredParameters = requiredParameters.toMap(),
            listeners = defaultListeners.toList(),
            sequenceListener = FakeCaptureSequenceListener(),
            sequenceNumber = -1
        )
    }

    /** TODO: It's probably better to model this as a sealed class. */
    public data class Event(
        val requestSequence: FakeCaptureSequence? = null,
        val rejected: Boolean = false,
        val abort: Boolean = false,
        val close: Boolean = false,
        val stop: Boolean = false,
        val submit: Boolean = false
    )

    public companion object {
        public suspend fun FakeCaptureSequenceProcessor.awaitEvent(
            request: Request? = null,
            filter: (event: Event) -> Boolean
        ): Event {

            var event: Event
            var loopCount = 0
            while (loopCount < 10) {
                loopCount++
                event = this.nextEvent()

                if (request != null) {
                    val contains =
                        event.requestSequence?.captureRequestList?.contains(request) ?: false
                    if (filter(event) && contains) {
                        return event
                    }
                } else if (filter(event)) {
                    return event
                }
            }

            throw IllegalStateException("Failed to observe a submit event containing $request")
        }
    }
}
