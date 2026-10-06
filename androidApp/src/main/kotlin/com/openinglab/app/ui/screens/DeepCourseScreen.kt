// SPDX-License-Identifier: Apache-2.0
package com.openinglab.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.openinglab.app.ui.components.Eyebrow
import com.openinglab.app.ui.components.PrimaryAction
import com.openinglab.app.ui.theme.Cream
import com.openinglab.app.ui.theme.DeepMoss
import com.openinglab.app.ui.theme.Divider
import com.openinglab.app.ui.theme.Gold
import com.openinglab.app.ui.theme.Leaf
import com.openinglab.app.ui.theme.MutedCream
import com.openinglab.shared.course.CourseRoles
import com.openinglab.shared.course.CourseSideIdeas
import com.openinglab.shared.course.CourseVariation
import com.openinglab.shared.course.DeepCourseCatalog
import com.openinglab.shared.course.DeepCourseChapterView
import com.openinglab.shared.model.PieceColor

/** One deep-course chapter: how it was made, its named variations with how each side wins, and every line. */
@Composable
fun DeepCourseScreen(
    chapter: DeepCourseChapterView,
    feedbackCount: Int,
    onBack: () -> Unit,
    onStudy: (PieceColor, String?) -> Unit,
    onPractice: (PieceColor, String?) -> Unit,
    onPracticeRandom: (PieceColor) -> Unit,
    onShareFeedback: () -> Unit,
    modifier: Modifier = Modifier,
    onExample: (String, PieceColor) -> Unit = { _, _ -> },
) {
    val pack = chapter.course
    val game = chapter.chapter.game
    var pov by rememberSaveable(chapter.opening.id) { mutableStateOf((pack.learnerSide ?: PieceColor.WHITE).name) }
    val learner = pack.learnerSide ?: PieceColor.valueOf(pov)
    var filter by rememberSaveable(chapter.opening.id) { mutableStateOf("ALL") }
    var query by rememberSaveable(chapter.opening.id) { mutableStateOf("") }
    var showLines by rememberSaveable(chapter.opening.id) { mutableStateOf(chapter.chapter.variations.isEmpty()) }
    val lines = remember(chapter.opening.id, filter, query) {
        chapter.opening.variations.filter { v ->
            val role = chapter.roles[v.id]
            val roleOk = when (filter) {
                "MAIN" -> role == "MAIN" || role == "ORIGINAL"
                "DEVIATION" -> role == "DEVIATION" || role == "SIDE"
                "TRAP" -> role == "TRAP" || role == "PUNISH"
                "BRANCH" -> role in setOf("BETTER", "ALTERNATIVE", "REFUTATION")
                else -> true
            }
            roleOk && (query.isBlank() || v.name.contains(query.trim(), ignoreCase = true))
        }
    }
    LazyColumn(modifier.testTag("deep-course"), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Cream) }
                Eyebrow(if (game != null) "GM game · generated teaching" else "Deep course · ${pack.level}", color = Gold)
            }
            Text(chapter.opening.name, color = Cream, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("deep-course-title"))
            Spacer(Modifier.height(8.dp))
            Text(chapter.chapter.intro, color = Cream, style = MaterialTheme.typography.bodyLarge)
        }
        if (game != null) item {
            Card {
                Text("${game.white}${game.whiteElo?.let { " ($it)" } ?: ""} – ${game.black}${game.blackElo?.let { " ($it)" } ?: ""}",
                    color = Cream, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("deep-game-players"))
                Text("${game.event} · ${game.date} · ${game.result}", color = MutedCream)
                Text("Original moves as broadcast (${game.license}). Engine branches are hypothetical lines, never the players' stated intentions.\n${game.site}",
                    color = MutedCream, style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            Card {
                Text("How this text was made", color = Gold, style = MaterialTheme.typography.titleSmall)
                Text(pack.provenance.labelPolicy, color = Cream, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("deep-label-policy"))
                Text("Engine: ${pack.provenance.engine} · ${pack.provenance.engineBudget}\nWriter: ${pack.provenance.writer} · generated ${pack.provenance.generatedOn}",
                    color = MutedCream, style = MaterialTheme.typography.bodySmall)
                pack.provenance.sources.forEach { source ->
                    Text("${source.title} · ${source.license} · ${source.games} games. ${source.note}", color = MutedCream, style = MaterialTheme.typography.bodySmall)
                }
                Text(pack.provenance.limitations, color = Gold, style = MaterialTheme.typography.bodySmall)
            }
        }
        chapter.chapter.coverage?.let { c ->
            item {
                Card {
                    Text("Coverage", color = Gold, style = MaterialTheme.typography.titleSmall)
                    Text("${c.lines} lines · ${c.minPlies}–${c.maxPlies} half-moves from the start", color = Cream, modifier = Modifier.testTag("deep-coverage"))
                    Text("Line endings: " + c.verdicts.entries.joinToString { "${CourseRoles.verdict(it.key)} ${it.value}" } +
                        (if (c.capped > 0) " · ${c.capped} line(s) reached the length cap" else ""), color = MutedCream, style = MaterialTheme.typography.bodySmall)
                    Text(c.note, color = MutedCream, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (pack.learnerSide == null && game == null) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Your side:", color = MutedCream, modifier = Modifier.padding(end = 8.dp))
                    TextButton({ pov = "WHITE" }, Modifier.testTag("deep-pov-white")) { Text(if (learner == PieceColor.WHITE) "✓ White" else "White", color = if (learner == PieceColor.WHITE) Leaf else MutedCream) }
                    TextButton({ pov = "BLACK" }, Modifier.testTag("deep-pov-black")) { Text(if (learner == PieceColor.BLACK) "✓ Black" else "Black", color = if (learner == PieceColor.BLACK) Leaf else MutedCream) }
                }
                if (game != null) {
                    PrimaryAction("Study the game as White", { onStudy(PieceColor.WHITE, null) }, Modifier.fillMaxWidth().testTag("deep-study-white"), color = Leaf)
                    PrimaryAction("Study the game as Black", { onStudy(PieceColor.BLACK, null) }, Modifier.fillMaxWidth().testTag("deep-study-black"), color = Gold)
                } else {
                    PrimaryAction("Study the main line", { onStudy(learner, null) }, Modifier.fillMaxWidth().testTag("deep-study-main"), color = Leaf)
                    PrimaryAction("Practice a random line", { onPracticeRandom(learner) }, Modifier.fillMaxWidth().testTag("deep-practice-random"), color = Gold)
                    Text("Random practice picks lines as often as players reach them, so common replies and common mistakes come up most.",
                        color = MutedCream, style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$feedbackCount explanation flag${if (feedbackCount == 1) "" else "s"} saved on this device", color = MutedCream,
                        modifier = Modifier.weight(1f).testTag("deep-feedback-count"))
                    TextButton(onShareFeedback, enabled = feedbackCount > 0, modifier = Modifier.testTag("deep-share-feedback")) { Text("Share flags", color = Leaf) }
                }
            }
        }
        if (chapter.chapter.variations.isNotEmpty()) {
            item {
                Text("Variations · how games are won", color = Cream, style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("deep-variations"))
                Text("Each variation: master games by rating band, results, and what the winners did, shown for your side first.",
                    color = MutedCream, style = MaterialTheme.typography.bodySmall)
            }
            itemsIndexed(chapter.chapter.variations, key = { _, v -> "variation-${v.nodeId}" }) { index, v ->
                VariationCard(v, index, chapter, learner, onStudy = { chapter.lineThrough(v.nodeId)?.let { onStudy(learner, it) } }, onExample = onExample)
            }
            item {
                TextButton({ showLines = !showLines }, Modifier.testTag("deep-toggle-lines")) {
                    Text(if (showLines) "Hide all lines" else "Show all ${chapter.opening.variations.size} lines", color = Leaf)
                }
            }
        }
        if (showLines) {
            item {
                Text("Lines", color = Cream, style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(query, { query = it }, label = { Text("Find a line or variation") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("deep-line-search"))
                Row {
                    val chips = if (game != null) listOf("ALL" to "All", "MAIN" to "Game", "BRANCH" to "Branches")
                        else listOf("ALL" to "All", "MAIN" to "Main", "DEVIATION" to "Alternatives", "TRAP" to "Mistakes")
                    chips.forEach { (key, label) ->
                        TextButton({ filter = key }, Modifier.testTag("deep-filter-$key")) { Text(if (filter == key) "✓ $label" else label, color = if (filter == key) Leaf else MutedCream) }
                    }
                }
                Text("${lines.size} of ${chapter.opening.variations.size} lines", color = MutedCream, modifier = Modifier.testTag("deep-line-count"))
            }
            items(lines, key = { it.id }) { line ->
                Surface(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onStudy(if (game != null) PieceColor.WHITE else learner, line.id) }
                    .testTag("deep-line-${line.id}"), color = DeepMoss, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Divider)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Eyebrow(line.category, color = if (chapter.roles[line.id] == "TRAP") Gold else Leaf)
                        Text(line.name, color = Cream, style = MaterialTheme.typography.titleMedium)
                        Text(line.description, color = MutedCream, style = MaterialTheme.typography.bodySmall)
                        Row {
                            TextButton({ onStudy(if (game != null) PieceColor.WHITE else learner, line.id) }) { Text("Study", color = Leaf) }
                            if (game == null) OutlinedButton({ onPractice(learner, line.id) }, border = BorderStroke(1.dp, Divider),
                                modifier = Modifier.testTag("deep-practice-${line.id}")) { Text("Practice", color = Cream) }
                        }
                    }
                }
            }
        }
        if (pack.glossary.isNotEmpty()) {
            item { Text("Ideas glossary", color = Cream, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp)) }
            items(pack.glossary, key = { "glossary-${it.id}" }) { entry ->
                Card {
                    Text(entry.title, color = Leaf, style = MaterialTheme.typography.titleSmall)
                    Text(entry.text, color = Cream, style = MaterialTheme.typography.bodySmall)
                    Text(entry.label, color = MutedCream, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun VariationCard(v: CourseVariation, index: Int, chapter: DeepCourseChapterView, learner: PieceColor, onStudy: () -> Unit,
                          onExample: (String, PieceColor) -> Unit) {
    var open by rememberSaveable(v.nodeId) { mutableStateOf(false) }
    Surface(Modifier.fillMaxWidth().testTag("deep-variation-$index"), color = DeepMoss, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Divider)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(v.name, color = Cream, style = MaterialTheme.typography.titleMedium, modifier = Modifier.clickable { open = !open })
            Text(v.path.mapIndexed { i, s -> if (i % 2 == 0) "${i / 2 + 1}.$s" else s }.joinToString(" "), color = MutedCream, style = MaterialTheme.typography.bodySmall)
            val total = maxOf(1, v.whiteWins + v.draws + v.blackWins)
            Text("Master games: ${v.games} (" + v.bands.filterValues { it > 0 }.entries.joinToString(" · ") { "${it.key} ${it.value}" } +
                ") · White ${100 * v.whiteWins / total}% · draws ${100 * v.draws / total}% · Black ${100 * v.blackWins / total}%",
                color = Leaf, style = MaterialTheme.typography.labelMedium)
            if (open) {
                Text(v.intro, color = Cream, style = MaterialTheme.typography.bodyMedium)
                Text(v.introLabel, color = MutedCream, style = MaterialTheme.typography.labelSmall)
                val sides = if (learner == PieceColor.WHITE) listOf(true, false) else listOf(false, true)
                sides.forEach { white ->
                    SideIdeas(if (white) "How White wins" else "How Black wins", if (white) v.white else v.black, index, white, chapter, onExample)
                }
                Row {
                    TextButton(onStudy, Modifier.testTag("deep-variation-study-$index")) { Text("Study this variation", color = Leaf) }
                }
            } else TextButton({ open = true }, Modifier.testTag("deep-variation-open-$index")) { Text("How games are won →", color = Leaf) }
        }
    }
}

@Composable
private fun SideIdeas(title: String, ideas: CourseSideIdeas, variation: Int, white: Boolean, chapter: DeepCourseChapterView,
                      onExample: (String, PieceColor) -> Unit) {
    Column(Modifier.fillMaxWidth().background(com.openinglab.app.ui.theme.Ink.copy(alpha = .25f), RoundedCornerShape(12.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = Gold, style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.testTag("deep-ideas-title-${if (white) "w" else "b"}-$variation"))
        Text(ideas.text, color = Cream, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("deep-ideas-${if (white) "w" else "b"}-$variation"))
        Text(ideas.label, color = MutedCream, style = MaterialTheme.typography.labelSmall)
        ideas.examples.forEachIndexed { n, ex ->
            TextButton({ onExample(DeepCourseCatalog.exampleId(chapter.opening.id, variation, white, n), if (white) PieceColor.WHITE else PieceColor.BLACK) },
                Modifier.testTag("deep-example-${if (white) "w" else "b"}-$variation-$n")) {
                Text("Replay: ${ex.white}${ex.whiteElo?.let { " ($it)" } ?: ""} – ${ex.black}${ex.blackElo?.let { " ($it)" } ?: ""}, ${ex.event} ${ex.date.take(4)} · ${ex.result}",
                    color = Leaf, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().background(DeepMoss, RoundedCornerShape(18.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
}
