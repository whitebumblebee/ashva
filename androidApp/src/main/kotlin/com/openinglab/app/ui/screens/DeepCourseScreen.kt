// SPDX-License-Identifier: Apache-2.0
package com.openinglab.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.openinglab.app.ui.components.ExpandableText
import com.openinglab.app.ui.components.Eyebrow
import com.openinglab.app.ui.components.DisclosureButton
import com.openinglab.app.ui.theme.*
import com.openinglab.shared.course.*
import com.openinglab.shared.model.PieceColor

@Composable
fun DeepCourseScreen(
    chapter: DeepCourseChapterView,
    onBack: () -> Unit,
    onStudy: (PieceColor, String?) -> Unit,
    onPracticeRandom: (PieceColor) -> Unit,
    onVariation: (Int, PieceColor) -> Unit,
    onAllLines: (PieceColor) -> Unit,
    modifier: Modifier = Modifier,
    developerMode: Boolean = false,
) {
    val pack = chapter.course
    val game = chapter.chapter.game
    var pov by rememberSaveable(chapter.opening.id) { mutableStateOf((pack.learnerSide ?: PieceColor.WHITE).name) }
    val learner = pack.learnerSide ?: PieceColor.valueOf(pov)
    val tree = chapter.variationTree
    var expanded by rememberSaveable(chapter.opening.id) { mutableStateOf(tree.children(null)) }
    var glossaryOpen by rememberSaveable(chapter.opening.id) { mutableStateOf(false) }
    LazyColumn(modifier.testTag("deep-course"), contentPadding = PaddingValues(18.dp, 0.dp, 18.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            CourseHeader(chapter.chapter.displayTitle(pack), onBack, "deep-course-title")
            if (developerMode) Eyebrow(if (game != null) "GM game · generated teaching" else "Deep course · ${pack.level}", color = Gold)
            ExpandableText(chapter.chapter.intro, more = "More", less = "Less")
        }
        if (game != null) item {
            CourseCard {
                Text("${game.white}${game.whiteElo?.let { " ($it)" } ?: ""} – ${game.black}${game.blackElo?.let { " ($it)" } ?: ""}",
                    color = Cream, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("deep-game-players"))
                Text("${game.event} · ${game.date} · ${game.result}", color = MutedCream)
            }
        }
        item {
            if (pack.learnerSide == null && game == null) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(PieceColor.WHITE, PieceColor.BLACK).forEachIndexed { index, side ->
                        SegmentedButton(selected = learner == side, onClick = { pov = side.name },
                            shape = SegmentedButtonDefaults.itemShape(index, 2), modifier = Modifier.testTag("deep-pov-${side.name.lowercase()}")) {
                            Text(if (side == PieceColor.WHITE) "White" else "Black")
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (game != null) {
                    Button({ onStudy(PieceColor.WHITE, null) }, Modifier.weight(1f).testTag("deep-study-white")) { Text("Study as White") }
                    OutlinedButton({ onStudy(PieceColor.BLACK, null) }, Modifier.weight(1f).testTag("deep-study-black")) { Text("Study as Black") }
                } else {
                    Button({ onStudy(learner, null) }, Modifier.weight(1f).testTag("deep-study-main")) { Text("Study main line") }
                    OutlinedButton({ onPracticeRandom(learner) }, Modifier.weight(1f).testTag("deep-practice-random")) { Text("Practice") }
                }
            }
        }
        if (tree.variations.isNotEmpty()) {
            item { Text("Variations", color = Cream, style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("deep-variations")); ResultLegend() }
            variationRows(tree, null, expanded, { index -> expanded = if (index in expanded) expanded - index else expanded + index },
                { onVariation(it, learner) })
        }
        item { TextButton({ onAllLines(learner) }, Modifier.testTag("deep-toggle-lines")) { Text("Browse all lines", color = Leaf) } }
        if (developerMode) {
            item {
                CourseCard {
                    Text("How this text was made", color = Gold, style = MaterialTheme.typography.titleSmall)
                    Text(pack.provenance.labelPolicy, color = Cream, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("deep-label-policy"))
                    Text("Engine: ${pack.provenance.engine} · ${pack.provenance.engineBudget}\nWriter: ${pack.provenance.writer} · generated ${pack.provenance.generatedOn}",
                        color = MutedCream, style = MaterialTheme.typography.bodySmall)
                    pack.provenance.sources.forEach { source -> Text("${source.title} · ${source.license} · ${source.games} games. ${source.note}", color = MutedCream, style = MaterialTheme.typography.bodySmall) }
                    Text(pack.provenance.limitations, color = Gold, style = MaterialTheme.typography.bodySmall)
                }
            }
            chapter.chapter.coverage?.let { c -> item {
                CourseCard {
                    Text("Coverage", color = Gold, style = MaterialTheme.typography.titleSmall)
                    Text("${c.lines} lines · ${c.minPlies}–${c.maxPlies} half-moves from the start", color = Cream, modifier = Modifier.testTag("deep-coverage"))
                    Text("Line endings: " + c.verdicts.entries.joinToString { "${CourseRoles.verdict(it.key)} ${it.value}" } +
                        (if (c.capped > 0) " · ${c.capped} line(s) reached the length cap" else ""), color = MutedCream, style = MaterialTheme.typography.bodySmall)
                    Text(c.note, color = MutedCream, style = MaterialTheme.typography.bodySmall)
                }
            } }
        }
        if (pack.glossary.isNotEmpty()) item {
            CourseCard {
                DisclosureButton("Key ideas", glossaryOpen, { glossaryOpen = !glossaryOpen }, Modifier.testTag("deep-key-ideas"))
                if (glossaryOpen) pack.glossary.forEach { entry ->
                    Text(entry.title, color = Cream, style = MaterialTheme.typography.titleSmall)
                    ExpandableText(entry.text, style = MaterialTheme.typography.bodySmall)
                    if (developerMode) Text(entry.label, color = MutedCream, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
