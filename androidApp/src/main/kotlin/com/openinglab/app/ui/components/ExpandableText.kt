// SPDX-License-Identifier: Apache-2.0
package com.openinglab.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.openinglab.app.ui.theme.Cream
import com.openinglab.app.ui.theme.Leaf

@Composable
fun ExpandableText(text: String, modifier: Modifier = Modifier, color: Color = Cream,
                   style: TextStyle = MaterialTheme.typography.bodyMedium, more: String = "Show more", less: String = "Show less") {
    var expanded by rememberSaveable(text) { mutableStateOf(false) }
    var overflows by remember(text) { mutableStateOf(false) }
    Column {
        Text(text, modifier, color = color, style = style, maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis, onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow })
        if (expanded || overflows) TextButton({ expanded = !expanded }) { Text(if (expanded) less else more, color = Leaf) }
    }
}
