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

package androidx.wear.compose.foundation

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastForEach
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListAnchorType
import androidx.wear.compose.foundation.lazy.ScalingLazyListItemInfo
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.startOffset

/**
 * An interface for providing scroll information for different scrollable containers, such lists.
 * Used for scrolling away, showing, hiding or scaling screen elements based on scrollable state.
 *
 * [ScrollInfoProvider] can be used to create a ScrollAway modifier, typically applied to an object
 * that appears at the top of the screen to scroll it away vertically when a list is scrolled
 * upwards. The scrolled offset is typically calculated with reference to the position of an anchor
 * e.g. the top item.
 */
interface ScrollInfoProvider {
    /**
     * Whether it is valid to scroll away the anchor item with the current configuration, For
     * example, if the selected anchor item does not exist, it is not valid to scroll away.
     */
    val isScrollAwayValid: Boolean

    /** Whether the container is currently scrollable. */
    val isScrollable: Boolean

    /**
     * Whether the list is currently scrolling (which can be used to show/hide a scroll indicator or
     * time text during the scroll operation).
     */
    val isScrollInProgress: Boolean

    /**
     * The amount that the anchor item has been scrolled upwards in the y direction (in Pixels),
     * relative to the initial position of the scrolling container (so >= zero). In the event that
     * the anchor item is no longer visible on the screen and its offset cannot be calculated, the
     * returned offset is Float.NaN.
     */
    val anchorItemOffset: Float

    /**
     * The amount of space between the last item (which may not be visible) and the bottom edge of
     * the viewport. This is always greater or equal to 0, if there is no (or negative) room
     * (including the case in which the last item is not on screen), 0 should be returned.
     */
    val lastItemOffset: Float
}

/**
 * Function for creating a [ScrollInfoProvider] from a [ScalingLazyListState], for use with
 * [ScalingLazyColumn] - used to create a ScrollAway modifier directly that can be applied to an
 * object that appears at the top of the screen, to scroll it away vertically when the
 * [ScalingLazyColumn] is scrolled upwards.
 *
 * @param state
 */
fun ScrollInfoProvider(state: ScalingLazyListState): ScrollInfoProvider =
    ScalingLazyListStateScrollInfoProvider(state)

/**
 * Function for creating a [ScrollInfoProvider] from a [LazyListState], for use with [LazyColumn] -
 * used to create a ScrollAway modifier directly that can be applied to an object that appears at
 * the top of the screen, to scroll it away vertically when the [LazyColumn] is scrolled upwards.
 */
fun ScrollInfoProvider(state: LazyListState): ScrollInfoProvider =
    LazyListStateScrollInfoProvider(state)

/**
 * Function for creating a [ScrollInfoProvider] from a [ScrollState], for use with [Column] - used
 * to create a ScrollAway modifier directly that can be applied to an object that appears at the top
 * of the screen, to scroll it away vertically when the [Column] is scrolled upwards.
 *
 * @param state the [ScrollState] to use as the base for creating the [ScrollInfoProvider]
 * @param bottomButtonHeight optional parameter to specify the size of a bottom button if one is
 *   provided.
 */
fun ScrollInfoProvider(state: ScrollState, bottomButtonHeight: Float = 0f): ScrollInfoProvider =
    ScrollStateScrollInfoProvider(state, bottomButtonHeight)

// Implementation of [ScrollAwayInfoProvider] for [ScalingLazyColumn].
// Being in Foundation, this implementation has access to the ScalingLazyListState
// auto-centering params, which are internal.
private class ScalingLazyListStateScrollInfoProvider(val state: ScalingLazyListState) :
    ScrollInfoProvider {
    override val isScrollAwayValid
        get() =
            state.layoutInfo.totalItemsCount > (state.config.value?.autoCentering?.itemIndex ?: 1)

    override val isScrollable
        get() =
            state.layoutInfo.visibleItemsInfo.isNotEmpty() &&
                (state.canScrollBackward || state.canScrollForward)

    override val isScrollInProgress
        get() = state.isScrollInProgress

    override val anchorItemOffset: Float
        get() {
            val layoutInfo = state.layoutInfo
            val autoCentering = state.config.value?.autoCentering
            return layoutInfo.visibleItemsInfo
                .fastFirstOrNull { it.index == (autoCentering?.itemIndex ?: 1) }
                ?.let {
                    val startOffset = it.startOffset(ScalingLazyListAnchorType.ItemStart)
                    if (initialStartOffset == null || startOffset > initialStartOffset!!) {
                        initialStartOffset = startOffset
                    }
                    -it.offset + initialStartOffset!!
                } ?: Float.NaN
        }

    override val lastItemOffset: Float
        get() {
            val screenHeightPx = state.config.value?.viewportHeightPx ?: 0
            var lastItemInfo: ScalingLazyListItemInfo? = null
            state.layoutInfo.visibleItemsInfo.fastForEach { ii ->
                if (ii.index == state.layoutInfo.totalItemsCount - 1) lastItemInfo = ii
            }
            return lastItemInfo?.let {
                val bottomEdge = it.offset + screenHeightPx / 2 + it.size / 2
                (screenHeightPx - bottomEdge).toFloat().coerceAtLeast(0f)
            } ?: 0f
        }

    override fun toString(): String {
        return "ScalingLazyListStateScrollInfoProvider(isScrollAwayValid=$isScrollAwayValid, " +
            "isScrollable=$isScrollable," +
            "isScrollInProgress=$isScrollInProgress, " +
            "anchorItemOffset=$anchorItemOffset, " +
            "lastItemOffset=$lastItemOffset)"
    }

    private var initialStartOffset: Float? = null
}

// Implementation of [ScrollAwayInfoProvider] for [LazyColumn].
private class LazyListStateScrollInfoProvider(val state: LazyListState) : ScrollInfoProvider {
    override val isScrollAwayValid
        get() = state.layoutInfo.totalItemsCount > 0

    override val isScrollable
        get() = state.layoutInfo.totalItemsCount > 0

    override val isScrollInProgress
        get() = state.isScrollInProgress

    override val anchorItemOffset
        get() =
            state.layoutInfo.visibleItemsInfo
                .fastFirstOrNull { it.index == 0 }
                ?.let { -it.offset.toFloat() } ?: Float.NaN

    override val lastItemOffset: Float
        get() {
            val screenHeightPx = state.layoutInfo.viewportSize.height
            var lastItemInfo: LazyListItemInfo? = null
            state.layoutInfo.visibleItemsInfo.fastForEach { ii ->
                if (ii.index == state.layoutInfo.totalItemsCount - 1) lastItemInfo = ii
            }
            return lastItemInfo?.let {
                val bottomEdge = it.offset + it.size - state.layoutInfo.viewportStartOffset
                (screenHeightPx - bottomEdge).toFloat().coerceAtLeast(0f)
            } ?: 0f
        }

    override fun toString(): String {
        return "LazyListStateScrollInfoProvider(isScrollAwayValid=$isScrollAwayValid, " +
            "isScrollable=$isScrollable," +
            "isScrollInProgress=$isScrollInProgress, " +
            "anchorItemOffset=$anchorItemOffset, " +
            "lastItemOffset=$lastItemOffset)"
    }
}

// Implementation of [ScrollAwayInfoProvider] for [Column]
private class ScrollStateScrollInfoProvider(
    val state: ScrollState,
    val bottomButtonHeight: Float = 0f
) : ScrollInfoProvider {
    override val isScrollAwayValid: Boolean
        get() = true

    override val isScrollable = state.maxValue != 0
    // Work around the default implementation of ScrollState not providing a useful
    // isScrollInProgress
    private var prevOffset: Int? = null
    override val isScrollInProgress
        get() =
            state.value.let { currentOffset ->
                (state.isScrollInProgress || (prevOffset != null && prevOffset != currentOffset))
                    .also { prevOffset = currentOffset }
            }

    override val anchorItemOffset: Float
        get() = state.value.toFloat()

    override val lastItemOffset: Float
        get() {
            return if (state.maxValue == Int.MAX_VALUE || bottomButtonHeight == 0f) 0f
            else (state.value - state.maxValue + bottomButtonHeight).coerceAtLeast(0f)
        }

    override fun toString(): String {
        return "ScrollStateScrollInfoProvider(isScrollAwayValid=$isScrollAwayValid, " +
            "isScrollable=$isScrollable," +
            "isScrollInProgress=$isScrollInProgress, " +
            "anchorItemOffset=$anchorItemOffset, " +
            "lastItemOffset=$lastItemOffset)"
    }
}
