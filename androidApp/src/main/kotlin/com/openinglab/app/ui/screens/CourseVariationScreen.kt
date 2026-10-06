// SPDX-License-Identifier: Apache-2.0
package com.openinglab.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import com.openinglab.app.ui.components.ChessBoard
import com.openinglab.app.ui.components.ExpandableText
import com.openinglab.app.ui.components.RoundedProgressBar
import com.openinglab.app.ui.components.DisclosureButton
import com.openinglab.app.ui.theme.*
import com.openinglab.shared.chess.BoardPosition
import com.openinglab.shared.course.*
import com.openinglab.shared.model.PieceColor

@Composable
fun CourseVariationScreen(chapter: DeepCourseChapterView, index: Int, side: PieceColor, onBack: () -> Unit,
                          onVariation: (Int) -> Unit, onStudy: () -> Unit, onPractice: () -> Unit,
                          onExample: (String, PieceColor) -> Unit, modifier: Modifier = Modifier, developerMode: Boolean = false) {
    val tree = chapter.variationTree
    val v = tree.variations.getOrNull(index) ?: return
    val board = chapter.nodePositions.getValue(v.nodeId)
    var expanded by rememberSaveable(chapter.opening.id, index) { mutableStateOf(emptyList<Int>()) }
    LazyColumn(modifier.testTag("deep-variation-screen"), contentPadding = PaddingValues(18.dp, 0.dp, 18.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            CourseHeader(tree.shortName(index), onBack)
            Text(numberedCourseMoves(v.path), color = MutedCream, style = MaterialTheme.typography.bodySmall)
        }
        item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ChessBoard(board, Modifier.width(240.dp).testTag("deep-anchor-board"), perspective = side, inputEnabled = false, showCoordinates = false)
        } }
        item {
            CourseCard {
                ResultBar(v, labels = true)
                Text("${v.games} master games", color = MutedCream, style = MaterialTheme.typography.labelSmall)
                val bands = listOf("2600+", "2400–2599", "2200–2399", "Club")
                val counts = bands.map { band -> if (band == "Club") v.bands.filterKeys { it.startsWith("Lichess") || it == "Club" }.values.sum() else v.bands[band] ?: 0 }
                val max = maxOf(1, counts.maxOrNull() ?: 0)
                bands.forEachIndexed { i, band ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(band, color = MutedCream, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(76.dp))
                        RoundedProgressBar(counts[i].toFloat() / max, Modifier.weight(1f))
                        Text("${counts[i]}", color = MutedCream, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        item { CourseCard {
            Text("The idea", color = Cream, style = MaterialTheme.typography.titleMedium)
            ExpandableText(v.intro)
            if (developerMode) Text(v.introLabel, color = MutedCream, style = MaterialTheme.typography.labelSmall)
        } }
        val sides = if (side == PieceColor.WHITE) listOf(true, false) else listOf(false, true)
        sides.forEach { white -> item(key = "ideas-$white") {
            VariationSideIdeas(if (white) v.white else v.black, index, white, (side == PieceColor.WHITE) == white, chapter, onExample, developerMode)
        } }
        if (tree.children(index).isNotEmpty()) {
            item { Text("Sub-variations", color = Cream, style = MaterialTheme.typography.titleMedium); ResultLegend() }
            variationRows(tree, index, expanded, { child -> expanded = if (child in expanded) expanded - child else expanded + child }, onVariation)
        }
        item {
            Button(onStudy, Modifier.fillMaxWidth().testTag("deep-variation-study-$index")) { Text("Study this variation") }
            OutlinedButton(onPractice, Modifier.fillMaxWidth().testTag("deep-variation-practice-$index")) { Text("Practice this variation") }
        }
    }
}

@Composable
private fun VariationSideIdeas(ideas: CourseSideIdeas, index: Int, white: Boolean, chosen: Boolean, chapter: DeepCourseChapterView,
                               onExample: (String, PieceColor) -> Unit, developerMode: Boolean) {
    val key = if (white) "w" else "b"
    var open by rememberSaveable(chapter.opening.id, index, white, chosen) { mutableStateOf(chosen) }
    CourseCard {
        DisclosureButton(if (white) "How White wins" else "How Black wins", open, { open = !open },
            Modifier.testTag("deep-ideas-title-$key-$index"), color = Gold)
        if (open) {
            // Older installed packs retain their complete prose until they are re-packed with structured plans.
            if (ideas.plan.isNotBlank()) ExpandableText(ideas.plan, Modifier.testTag("deep-ideas-$key-$index"))
            else if (ideas.patterns.isEmpty() && ideas.text.contains("most distinctively played")) ExpandableText(ideas.text, Modifier.testTag("deep-ideas-$key-$index"), style = MaterialTheme.typography.bodySmall)
            if (ideas.patterns.isNotEmpty()) {
                FlowRow(modifier = if (ideas.plan.isBlank()) Modifier.testTag("deep-ideas-$key-$index") else Modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ideas.patterns.forEach { pattern -> SuggestionChip(onClick = {}, label = {
                        Text("${pattern.san} · ${(pattern.winShare * 100).toInt()}% of wins")
                    }) }
                }
            } else if (!ideas.text.contains("most distinctively played")) {
                Text("No single plan stands out — see the model games.", color = MutedCream, style = MaterialTheme.typography.bodySmall,
                    modifier = if (ideas.plan.isBlank()) Modifier.testTag("deep-ideas-$key-$index") else Modifier)
            }
            if (ideas.plan.isBlank() && ideas.patterns.isEmpty() && !ideas.text.contains("most distinctively played")) {
                var details by rememberSaveable(chapter.opening.id, index, white) { mutableStateOf(false) }
                DisclosureButton("More detail", details, { details = !details })
                if (details) ExpandableText(ideas.text, style = MaterialTheme.typography.bodySmall)
            }
            if (developerMode) {
                if (ideas.plan.isNotBlank() || ideas.patterns.isNotEmpty()) ExpandableText(ideas.text, style = MaterialTheme.typography.bodySmall)
                Text(ideas.label, color = MutedCream, style = MaterialTheme.typography.labelSmall)
            }
            if (ideas.examples.isNotEmpty()) Text("Model games", color = Cream, style = MaterialTheme.typography.titleSmall)
            ideas.examples.forEachIndexed { n, ex ->
                TextButton({ onExample(DeepCourseCatalog.exampleId(chapter.opening.id, index, white, n), if (white) PieceColor.WHITE else PieceColor.BLACK) },
                    Modifier.fillMaxWidth().testTag("deep-example-$key-$index-$n"), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)) {
                    Icon(Icons.Rounded.PlayArrow, "Replay game", Modifier.size(18.dp), tint = Leaf)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("${ex.white} – ${ex.black} · ${ex.date.take(4)} · ${ex.result}", color = Leaf, style = MaterialTheme.typography.bodySmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(ex.event, color = MutedCream, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
