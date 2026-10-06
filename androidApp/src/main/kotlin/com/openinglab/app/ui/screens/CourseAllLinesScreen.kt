// SPDX-License-Identifier: Apache-2.0
package com.openinglab.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.openinglab.app.ui.components.ExpandableText
import com.openinglab.app.ui.components.Eyebrow
import com.openinglab.app.ui.theme.*
import com.openinglab.shared.course.*
import com.openinglab.shared.model.PieceColor

@Composable
fun CourseAllLinesScreen(chapter: DeepCourseChapterView, side: PieceColor, onBack: () -> Unit,
                         onStudy: (String) -> Unit, onPractice: (String) -> Unit, modifier: Modifier = Modifier, developerMode: Boolean = false) {
    val game = chapter.chapter.game != null
    var filter by rememberSaveable(chapter.opening.id) { mutableStateOf("ALL") }
    var query by rememberSaveable(chapter.opening.id) { mutableStateOf("") }
    val lines = remember(chapter.opening.id, filter, query) { chapter.opening.variations.filter { line ->
        val role = chapter.roles[line.id]
        val roleOk = when (filter) {
            "MAIN" -> role == "MAIN" || role == "ORIGINAL"
            "DEVIATION" -> role == "DEVIATION" || role == "SIDE"
            "TRAP" -> role == "TRAP" || role == "PUNISH"
            "BRANCH" -> role in setOf("BETTER", "ALTERNATIVE", "REFUTATION")
            else -> true
        }
        roleOk && (query.isBlank() || line.name.contains(query.trim(), ignoreCase = true))
    } }
    LazyColumn(modifier.testTag("deep-all-lines"), contentPadding = PaddingValues(18.dp, 0.dp, 18.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            CourseHeader("All lines", onBack)
            Text("${chapter.chapter.displayTitle(chapter.course)} · ${side.name.lowercase()} POV", color = MutedCream, style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(query, { query = it }, label = { Text("Find a line or variation") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("deep-line-search"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val chips = if (game) listOf("ALL" to "All", "MAIN" to "Game", "BRANCH" to "Branches") else
                    listOf("ALL" to "All", "MAIN" to "Main", "DEVIATION" to "Alternatives", "TRAP" to "Mistakes")
                chips.forEach { (key, label) -> FilterChip(selected = filter == key, onClick = { filter = key },
                    label = { Text(label) }, modifier = Modifier.testTag("deep-filter-$key")) }
            }
            Text("${lines.size} of ${chapter.opening.variations.size} lines", color = MutedCream, modifier = Modifier.testTag("deep-line-count"))
        }
        items(lines, key = { it.id }) { line ->
            Column(Modifier.testTag("deep-line-${line.id}").clickable { onStudy(line.id) }) {
                CourseCard {
                    Eyebrow(line.category, color = if (chapter.roles[line.id] == "TRAP") Gold else Leaf)
                    Text(line.name, color = Cream, style = MaterialTheme.typography.titleMedium)
                    val description = if (developerMode) line.description else chapter.lineEndingSummary(line.id)
                    ExpandableText(description, color = MutedCream, style = MaterialTheme.typography.bodySmall)
                    Row {
                        TextButton({ onStudy(line.id) }) { Text("Study", color = Leaf) }
                        if (!game) OutlinedButton({ onPractice(line.id) }, Modifier.testTag("deep-practice-${line.id}")) { Text("Practice", color = Cream) }
                    }
                }
            }
        }
    }
}
