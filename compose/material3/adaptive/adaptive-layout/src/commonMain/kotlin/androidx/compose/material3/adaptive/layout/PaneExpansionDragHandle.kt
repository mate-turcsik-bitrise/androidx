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

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * A default, basic non-customizable implementation of pane expansion drag handle. Note that this
 * implementation will be deprecated in favor of the corresponding Material3 implementation when
 * it's available.
 */
@ExperimentalMaterial3AdaptiveApi
@Composable
// TODO(b/327637983): Implement this as a customizable component as a Material3 component.
fun PaneExpansionDragHandle(
    state: PaneExpansionState,
    color: Color,
    modifier: Modifier = Modifier,
) {
    // TODO (conradchen): support drag handle motion during scaffold and expansion state change
    Box(
        modifier = modifier.paneExpansionDragHandle(state).size(24.dp, 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier =
                Modifier.size(4.dp, 48.dp)
                    .graphicsLayer(shape = CircleShape, clip = true)
                    .background(color)
        )
    }
}

@ExperimentalMaterial3AdaptiveApi
internal fun Modifier.paneExpansionDragHandle(state: PaneExpansionState): Modifier =
    this.draggable(
            state = state,
            orientation = Orientation.Horizontal,
            onDragStopped = { velocity -> state.settleToAnchorIfNeeded(velocity) }
        )
        .systemGestureExclusion()

internal expect fun Modifier.systemGestureExclusion(): Modifier
