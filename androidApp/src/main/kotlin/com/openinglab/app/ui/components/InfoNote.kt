// SPDX-License-Identifier: Apache-2.0
package com.openinglab.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import com.openinglab.app.ui.theme.MutedCream

/** A concise note with its full explanation available without interrupting the screen. */
@Composable
fun InfoNote(label: String, detail: String, modifier: Modifier = Modifier,
    color: Color = MutedCream, style: TextStyle = MaterialTheme.typography.bodySmall) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = color, style = style, modifier = modifier.weight(1f))
        TextButton({ expanded = true }, Modifier.semantics { contentDescription = "More information: $label" }) { Text("ⓘ", color = color) }
    }
    if (expanded) AlertDialog(onDismissRequest = { expanded = false }, title = { Text(label) },
        text = { Text(detail, Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton({ expanded = false }) { Text("Done") } })
}
