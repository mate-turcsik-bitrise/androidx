/*
 * Copyright 2024 The Android Open Source Project
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

package androidx.compose.material3.adaptive.layout

import androidx.annotation.FloatRange
import androidx.annotation.VisibleForTesting
import androidx.collection.IntList
import androidx.collection.MutableIntList
import androidx.compose.animation.core.animate
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.gestures.DragScope
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.PaneExpansionState.Companion.Unspecified
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope

/**
 * Interface that provides [PaneExpansionStateKey] to remember and retrieve [PaneExpansionState]
 * with [rememberPaneExpansionState].
 */
@ExperimentalMaterial3AdaptiveApi
@Stable
sealed interface PaneExpansionStateKeyProvider {
    /** The key that represents the unique state of the provider to index [PaneExpansionState]. */
    val paneExpansionStateKey: PaneExpansionStateKey
}

/**
 * Interface that serves as keys to remember and retrieve [PaneExpansionState] with
 * [rememberPaneExpansionState].
 */
@ExperimentalMaterial3AdaptiveApi
@Immutable
sealed interface PaneExpansionStateKey {
    private class DefaultImpl : PaneExpansionStateKey {
        override fun equals(other: Any?): Boolean {
            return this === other
        }

        override fun hashCode(): Int {
            return System.identityHashCode(this)
        }
    }

    companion object {
        /**
         * The default [PaneExpansionStateKey]. If you want to always share the same
         * [PaneExpansionState] no matter what current scaffold state is, this key can be used. For
         * example if the default key is used and a user drag the list-detail layout to a 50-50
         * split, when the layout switches to, say, detail-extra, it will remain the 50-50 split
         * instead of using a different (default or user-set) split for it.
         */
        val Default: PaneExpansionStateKey = DefaultImpl()
    }
}

/**
 * Remembers and returns a [PaneExpansionState] associated to a given
 * [PaneExpansionStateKeyProvider].
 *
 * Note that the remembered [PaneExpansionState] with all keys that have been used will be
 * persistent through the associated pane scaffold's lifecycles.
 *
 * @param keyProvider the provider of [PaneExpansionStateKey]
 * @param anchors the anchor list of the returned [PaneExpansionState]
 */
@ExperimentalMaterial3AdaptiveApi
@Composable
fun rememberPaneExpansionState(
    keyProvider: PaneExpansionStateKeyProvider,
    anchors: List<PaneExpansionAnchor> = emptyList()
): PaneExpansionState = rememberPaneExpansionState(keyProvider.paneExpansionStateKey, anchors)

/**
 * Remembers and returns a [PaneExpansionState] associated to a given [PaneExpansionStateKey].
 *
 * Note that the remembered [PaneExpansionState] with all keys that have been used will be
 * persistent through the associated pane scaffold's lifecycles.
 *
 * @param key the key of [PaneExpansionStateKey]
 * @param anchors the anchor list of the returned [PaneExpansionState]
 */
@ExperimentalMaterial3AdaptiveApi
@Composable
fun rememberPaneExpansionState(
    key: PaneExpansionStateKey = PaneExpansionStateKey.Default,
    anchors: List<PaneExpansionAnchor> = emptyList()
): PaneExpansionState {
    // TODO(conradchen): Implement this as saveables
    val dataMap = remember { mutableStateMapOf<PaneExpansionStateKey, PaneExpansionStateData>() }
    val expansionState = remember {
        val defaultData = PaneExpansionStateData()
        dataMap[PaneExpansionStateKey.Default] = defaultData
        PaneExpansionState(defaultData)
    }
    return expansionState.apply {
        this.data = dataMap[key] ?: PaneExpansionStateData().also { dataMap[key] = it }
        this.anchors = anchors
    }
}

/**
 * This class manages the pane expansion state for pane scaffolds. By providing and modifying an
 * instance of this class, you can specify the expanded panes' expansion width or percentage when
 * pane scaffold is displaying a dual-pane layout.
 *
 * This class also serves as the [DraggableState] of pane expansion handle. When a handle
 * implementation is provided to the associated pane scaffold, the scaffold will use
 * [PaneExpansionState] to store and manage dragging and anchoring of the handle, and thus the pane
 * expansion state.
 */
@ExperimentalMaterial3AdaptiveApi
@Stable
class PaneExpansionState
internal constructor(
    // TODO(conradchen): Handle state change during dragging and settling
    data: PaneExpansionStateData = PaneExpansionStateData(),
    anchors: List<PaneExpansionAnchor> = emptyList()
) : DraggableState {

    internal val firstPaneWidth
        get() =
            if (maxExpansionWidth == Unspecified || data.firstPaneWidthState == Unspecified) {
                Unspecified
            } else {
                data.firstPaneWidthState.coerceIn(0, maxExpansionWidth)
            }

    internal val firstPaneProportion: Float
        get() = data.firstPaneProportionState

    internal var currentDraggingOffset
        get() = data.currentDraggingOffsetState
        private set(value) {
            val coercedValue = value.coerceIn(0, maxExpansionWidth)
            if (coercedValue == data.currentDraggingOffsetState) {
                return
            }
            data.currentDraggingOffsetState = coercedValue
            currentMeasuredDraggingOffset = coercedValue
        }

    internal var data by mutableStateOf(data)

    internal var isDragging by mutableStateOf(false)
        private set

    internal var isSettling by mutableStateOf(false)
        private set

    internal val isDraggingOrSettling
        get() = isDragging || isSettling

    @VisibleForTesting
    internal var maxExpansionWidth by mutableIntStateOf(Unspecified)
        private set

    // Use this field to store the dragging offset decided by measuring instead of dragging to
    // prevent redundant re-composition.
    @VisibleForTesting
    internal var currentMeasuredDraggingOffset = Unspecified
        private set

    internal var anchors: List<PaneExpansionAnchor> by mutableStateOf(anchors)

    private lateinit var measuredDensity: Density

    private val dragScope: DragScope =
        object : DragScope {
            override fun dragBy(pixels: Float): Unit = dispatchRawDelta(pixels)
        }

    private val dragMutex = MutatorMutex()

    /** Returns `true` if none of [firstPaneWidth] or [firstPaneProportion] has been set. */
    fun isUnspecified(): Boolean =
        firstPaneWidth == Unspecified &&
            firstPaneProportion.isNaN() &&
            currentDraggingOffset == Unspecified

    override fun dispatchRawDelta(delta: Float) {
        if (currentMeasuredDraggingOffset == Unspecified) {
            return
        }
        currentDraggingOffset = (currentMeasuredDraggingOffset + delta).toInt()
    }

    override suspend fun drag(dragPriority: MutatePriority, block: suspend DragScope.() -> Unit) =
        coroutineScope {
            isDragging = true
            dragMutex.mutateWith(dragScope, dragPriority, block)
            isDragging = false
        }

    /**
     * Set the width of the first expanded pane in the layout. When the set value gets applied, it
     * will be coerced within the range of `[0, the full displayable width of the layout]`.
     *
     * Note that setting this value will reset the first pane proportion previously set via
     * [setFirstPaneProportion] or the current dragging result if there's any. Also if user drags
     * the pane after setting the first pane width, the user dragging result will take the priority
     * over this set value when rendering panes, but the set value will be saved.
     */
    fun setFirstPaneWidth(firstPaneWidth: Int) {
        data.firstPaneProportionState = Float.NaN
        data.currentDraggingOffsetState = Unspecified
        data.firstPaneWidthState = firstPaneWidth
    }

    /**
     * Set the proportion of the first expanded pane in the layout. The set value needs to be within
     * the range of `[0f, 1f]`, otherwise the setter throws.
     *
     * Note that setting this value will reset the first pane width previously set via
     * [setFirstPaneWidth] or the current dragging result if there's any. Also if user drags the
     * pane after setting the first pane proportion, the user dragging result will take the priority
     * over this set value when rendering panes, but the set value will be saved.
     */
    fun setFirstPaneProportion(@FloatRange(0.0, 1.0) firstPaneProportion: Float) {
        require(firstPaneProportion in 0f..1f) { "Proportion value needs to be in [0f, 1f]" }
        data.firstPaneWidthState = Unspecified
        data.currentDraggingOffsetState = Unspecified
        data.firstPaneProportionState = firstPaneProportion
    }

    /**
     * Clears any previously set [firstPaneWidth] or [firstPaneProportion], as well as the user
     * dragging result.
     */
    fun clear() {
        data.firstPaneWidthState = Unspecified
        data.firstPaneProportionState = Float.NaN
        data.currentDraggingOffsetState = Unspecified
    }

    internal fun onMeasured(measuredWidth: Int, density: Density) {
        if (measuredWidth == maxExpansionWidth) {
            return
        }
        maxExpansionWidth = measuredWidth
        measuredDensity = density
        // To re-coerce the value
        if (currentDraggingOffset != Unspecified) {
            currentDraggingOffset = currentDraggingOffset
        }
    }

    internal fun onExpansionOffsetMeasured(measuredOffset: Int) {
        currentMeasuredDraggingOffset = measuredOffset
    }

    internal suspend fun settleToAnchorIfNeeded(velocity: Float) {
        val currentAnchorPositions = anchors.toPositions(maxExpansionWidth, measuredDensity)
        if (currentAnchorPositions.isEmpty()) {
            return
        }

        // TODO(conradchen): Figure out how to use lookahead here to avoid repeating measuring
        dragMutex.mutate(MutatePriority.PreventUserInput) {
            isSettling = true
            // TODO(conradchen): Use the right animation spec here.
            animate(
                currentMeasuredDraggingOffset.toFloat(),
                currentAnchorPositions
                    .getPositionOfTheClosestAnchor(currentMeasuredDraggingOffset, velocity)
                    .toFloat(),
                velocity,
            ) { value, _ ->
                currentDraggingOffset = value.toInt()
            }
            isSettling = false
        }
    }

    private fun IntList.getPositionOfTheClosestAnchor(currentPosition: Int, velocity: Float): Int =
        minBy(
            when {
                velocity >= AnchoringVelocityThreshold -> {
                    { anchorPosition: Int ->
                        val delta = anchorPosition - currentPosition
                        if (delta < 0) Int.MAX_VALUE else delta
                    }
                }
                velocity <= -AnchoringVelocityThreshold -> {
                    { anchorPosition: Int ->
                        val delta = currentPosition - anchorPosition
                        if (delta < 0) Int.MAX_VALUE else delta
                    }
                }
                else -> {
                    { anchorPosition: Int -> abs(currentPosition - anchorPosition) }
                }
            }
        )

    companion object {
        /** The constant value used to denote the pane expansion is not specified. */
        const val Unspecified = -1

        private const val AnchoringVelocityThreshold = 200F
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
internal class PaneExpansionStateData {
    var firstPaneWidthState by mutableIntStateOf(Unspecified)
    var firstPaneProportionState by mutableFloatStateOf(Float.NaN)
    var currentDraggingOffsetState by mutableIntStateOf(Unspecified)
}

/**
 * The implementations of this interface represent different types of anchors of pane expansion
 * dragging. Setting up anchors when create [PaneExpansionState] will force user dragging to snap to
 * the set anchors after user releases the drag.
 */
@ExperimentalMaterial3AdaptiveApi
sealed class PaneExpansionAnchor private constructor() {
    internal abstract fun positionIn(totalSizePx: Int, density: Density): Int

    /**
     * [PaneExpansionAnchor] implementation that specifies the anchor position in the proportion of
     * the total size of the layout at the start side of the anchor.
     *
     * @property proportion the proportion of the layout at the start side of the anchor. layout.
     */
    class Proportion(@FloatRange(0.0, 1.0) val proportion: Float) : PaneExpansionAnchor() {
        override fun positionIn(totalSizePx: Int, density: Density) =
            (totalSizePx * proportion).roundToInt().coerceIn(0, totalSizePx)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Proportion) return false
            return proportion == other.proportion
        }

        override fun hashCode(): Int {
            return proportion.hashCode()
        }
    }

    /**
     * [PaneExpansionAnchor] implementation that specifies the anchor position in the offset in
     * [Dp]. If a positive value is provided, the offset will be treated as a start offset, on the
     * other hand, if a negative value is provided, the absolute value of the provided offset will
     * be used as an end offset. For example, if -150.dp is provided, the resulted anchor will be at
     * the position that is 150dp away from the end side of the associated layout.
     *
     * @property offset the offset of the anchor in [Dp].
     */
    class Offset(val offset: Dp) : PaneExpansionAnchor() {
        override fun positionIn(totalSizePx: Int, density: Density) =
            with(density) { offset.toPx() }.toInt().let { if (it < 0) totalSizePx + it else it }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Offset) return false
            return offset == other.offset
        }

        override fun hashCode(): Int {
            return offset.hashCode()
        }
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
private fun List<PaneExpansionAnchor>.toPositions(
    maxExpansionWidth: Int,
    density: Density
): IntList {
    val anchors = MutableIntList(size)
    @Suppress("ListIterator") // Not necessarily a random-accessible list
    forEach { anchor -> anchors.add(anchor.positionIn(maxExpansionWidth, density)) }
    anchors.sort()
    return anchors
}

private fun <T : Comparable<T>> IntList.minBy(selector: (Int) -> T): Int {
    if (isEmpty()) {
        throw NoSuchElementException()
    }
    var minElem = this[0]
    var minValue = selector(minElem)
    for (i in 1 until size) {
        val elem = this[i]
        val value = selector(elem)
        if (minValue > value) {
            minElem = elem
            minValue = value
        }
    }
    return minElem
}
