// SPDX-License-Identifier: Apache-2.0
package com.openinglab.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.openinglab.app.ui.theme.Divider
import com.openinglab.app.ui.theme.Leaf

/** A continuous track: zero is empty, one is full, with no endpoint dot or gap. */
@Composable
fun RoundedProgressBar(progress: Float, modifier: Modifier = Modifier, color: Color = Leaf, trackColor: Color = Divider) {
    val fraction = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f
    Canvas(modifier.fillMaxWidth().height(4.dp).semantics {
        progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
    }) {
        val corners = CornerRadius(size.height / 2)
        drawRoundRect(trackColor, cornerRadius = corners)
        if (fraction > 0f) drawRoundRect(color, size = Size(size.width * fraction, size.height), cornerRadius = corners)
    }
}
