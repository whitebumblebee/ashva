// SPDX-License-Identifier: Apache-2.0
package com.openinglab.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.openinglab.app.ui.theme.Leaf

@Composable
fun DisclosureButton(title: String, expanded: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Leaf) {
    val rotation by animateFloatAsState(if (expanded) 90f else 0f, label = "Disclosure chevron")
    TextButton(onClick, modifier.fillMaxWidth().semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, Modifier.weight(1f), color = color, style = MaterialTheme.typography.titleSmall)
            Icon(Icons.Rounded.ChevronRight, null, Modifier.rotate(rotation), tint = color)
        }
    }
}
