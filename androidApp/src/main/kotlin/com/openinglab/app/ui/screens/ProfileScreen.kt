// SPDX-License-Identifier: Apache-2.0
package com.openinglab.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.openinglab.app.BuildConfig
import com.openinglab.app.content.LearnerPreferences
import com.openinglab.app.ui.LearnerUiState
import com.openinglab.app.ui.RecallUiState
import com.openinglab.app.ui.components.RoundedProgressBar
import com.openinglab.app.ui.components.Eyebrow
import com.openinglab.app.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun ProfileScreen(state: RecallUiState, modifier: Modifier = Modifier, developerMode: Boolean = false,
    onDeveloperMode: (Boolean) -> Unit = {}, onSources: () -> Unit = {}, feedbackCount: Int = 0,
    onFeedback: () -> Unit = {}, learner: LearnerUiState = LearnerUiState(),
    onPreferences: (LearnerPreferences) -> Unit = {}, setNames: Map<String, String> = emptyMap(), onShare: () -> Unit = {},
    provenance: String = "Course text combines legally replayed source moves, original explanations, checked statistics and Stockfish analysis. Machine checks verify the claims; the text has not been independently reviewed. Details: docs/COURSE_PROVENANCE.md.") {
    val prefs = learner.preferences
    val totals = state.totals
    var editName by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var showProvenance by rememberSaveable { mutableStateOf(false) }
    val answers = totals.unaided + totals.assisted + totals.notRecalled
    LazyColumn(modifier.testTag("learner-profile-screen"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(64.dp).background(Leaf, CircleShape), contentAlignment = Alignment.Center) {
                    Text(prefs.displayName.firstOrNull()?.uppercase() ?: "♞", color = Ink, style = MaterialTheme.typography.headlineLarge)
                }
                Column(Modifier.padding(start = 16.dp).weight(1f)) {
                    Text(prefs.displayName.ifBlank { "Your profile" }, color = Cream, style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.clickable { name = prefs.displayName; editName = true }.testTag("profile-display-name"))
                    TextButton({ name = prefs.displayName; editName = true }, Modifier.testTag("profile-edit-name")) { Text("Edit name", color = Leaf) }
                    Text(learner.week.firstDay?.let { "Learning since ${LocalDate.ofEpochDay(it).format(DateTimeFormatter.ofPattern("d MMM yyyy"))}" }
                        ?: "Start your first study day", color = MutedCream, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (learner.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        learner.error?.let { item { Text(it, color = Gold) } }
        state.error?.let { item { Text(it, color = Gold) } }
        item {
            ProfileCard {
                Text("This week", color = Cream, style = MaterialTheme.typography.titleLarge)
                Text("${learner.week.studyDays} of ${prefs.routine.weeklyGoal} study days", color = Leaf, modifier = Modifier.testTag("profile-week-days"))
                RoundedProgressBar(learner.week.studyDays.toFloat() / prefs.routine.weeklyGoal, Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { index, label ->
                        val day = learner.week.monday + index
                        val active = day in learner.week.activeDays
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.testTag("profile-day-$index")
                            .semantics { stateDescription = "${LocalDate.ofEpochDay(day)}: ${if (active) "studied" else "no activity"}" }) {
                            Text(label, color = MutedCream, style = MaterialTheme.typography.labelMedium)
                            Box(Modifier.padding(top = 6.dp).size(28.dp).background(if (active) Leaf else Moss, CircleShape), contentAlignment = Alignment.Center) {
                                Text(if (active) "✓" else "·", color = if (active) Ink else MutedCream)
                            }
                        }
                    }
                }
                Text("${learner.week.streak} day streak", color = Cream, modifier = Modifier.testTag("profile-streak"))
            }
        }
        item {
            Column(Modifier.testTag("profile-real-totals"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ProfileCard {
                    Text("Tactics", color = Cream, style = MaterialTheme.typography.titleLarge)
                    Metric("${learner.tactics.attempts.count { it.correct }}", "Puzzles solved")
                    Metric(accuracy(learner.tactics.attempts.count { it.correct }, learner.tactics.attempts.size), "First-try accuracy")
                    Metric("${learner.tactics.cycles.count { it.completedAt != null && it.setId.startsWith("woodpecker-") }}", "Woodpecker cycles completed")
                    learner.tactics.cycles.filter { it.completedAt != null }.groupBy { it.setId }.forEach { (id, cycles) ->
                        Metric(duration(cycles.minOf { it.activeMs }), "Best cycle · ${setNames[id] ?: id}")
                    }
                }
                ProfileCard {
                    Text("Openings", color = Cream, style = MaterialTheme.typography.titleLarge)
                    Metric("${learner.practisedLines}", "Lines practised")
                    Metric("${learner.studiedLines}", "Lines studied")
                    Metric("$answers", "Review answers")
                    Metric(accuracy(totals.unaided, answers), "Unaided review accuracy")
                    Metric("${learner.activity.due}", "Due now")
                    // Keep the retained history visible without treating legacy moves as line completions.
                    Metric("${totals.attempts}", "Recorded move attempts")
                    Metric("${totals.assisted}", "Assisted correct answers")
                    Metric("${totals.studyViews}", "Study views")
                    if (developerMode) Metric("${totals.legacyUngraded}", "Ungraded legacy / exploratory attempts")
                }
            }
        }
        item {
            ProfileCard {
                Text("Routine settings", color = Cream, style = MaterialTheme.typography.titleLarge)
                ChoiceRow("Daily puzzles", listOf(10, 20, 30, 50), prefs.routine.dailyPuzzles, "daily-puzzles") {
                    onPreferences(prefs.copy(routine = prefs.routine.copy(dailyPuzzles = it)))
                }
                ChoiceRow("Daily opening practice", listOf(1, 3, 5), prefs.routine.dailyLines, "daily-lines") {
                    onPreferences(prefs.copy(routine = prefs.routine.copy(dailyLines = it)))
                }
                SettingSwitch("Review due cards", prefs.routine.reviewDue, "routine-review") { onPreferences(prefs.copy(routine = prefs.routine.copy(reviewDue = it))) }
                ChoiceRow("Weekly study-day goal", (3..7).toList(), prefs.routine.weeklyGoal, "weekly-goal") {
                    onPreferences(prefs.copy(routine = prefs.routine.copy(weeklyGoal = it)))
                }
            }
        }
        item {
            ProfileCard {
                Text("Preferences", color = Cream, style = MaterialTheme.typography.titleLarge)
                SettingSwitch("Board coordinates", prefs.boardCoordinates, "board-coordinates") { onPreferences(prefs.copy(boardCoordinates = it)) }
                Text("Autoplay speed", color = Cream, style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2000L to "Slow", 1200L to "Normal", 700L to "Fast").forEach { (millis, label) ->
                        FilterChip(prefs.playbackMillis == millis, { onPreferences(prefs.copy(playbackMillis = millis)) }, { Text(label) }, modifier = Modifier.testTag("profile-speed-$millis"))
                    }
                }
                SettingSwitch("Puzzles auto-next", prefs.puzzlesAutoNext, "profile-puzzles-auto-next") { onPreferences(prefs.copy(puzzlesAutoNext = it)) }
                SettingSwitch("Developer mode", developerMode, "developer-mode", onDeveloperMode)
            }
        }
        item(key = "app-version") {
            ProfileCard {
                Text("About", color = Cream, style = MaterialTheme.typography.titleLarge)
                Text("Ashva ${BuildConfig.VERSION_NAME}", color = MutedCream)
                TextButton(onSources, Modifier.testTag("profile-sources")) { Text("Sources & licences", color = Leaf) }
                Text("Everything stays on this device. No account, no analytics.", color = MutedCream, style = MaterialTheme.typography.bodySmall)
                Text("No cloud backup; clearing app data or uninstalling removes your history.", color = MutedCream, style = MaterialTheme.typography.bodySmall)
                if (developerMode) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onFeedback, Modifier.weight(1f).testTag("profile-content-feedback")) { Text("Content feedback ($feedbackCount)", color = Leaf) }
                        TextButton(onShare, enabled = feedbackCount > 0, modifier = Modifier.testTag("profile-share-feedback")) { Text("Share", color = Leaf) }
                    }
                    TextButton({ showProvenance = true }, Modifier.testTag("profile-provenance")) { Text("How course text is made", color = Leaf) }
                }
            }
        }
    }
    if (editName) AlertDialog(onDismissRequest = { editName = false }, title = { Text("Display name") }, text = {
        OutlinedTextField(name, { name = it.take(40) }, singleLine = true, label = { Text("Name") }, modifier = Modifier.testTag("profile-name-input"))
    }, confirmButton = { TextButton({ onPreferences(prefs.copy(displayName = name)); editName = false }, Modifier.testTag("profile-save-name")) { Text("Save") } },
        dismissButton = { TextButton({ editName = false }) { Text("Cancel") } })
    if (showProvenance) AlertDialog(onDismissRequest = { showProvenance = false }, title = { Text("How course text is made") },
        text = { Text(provenance, Modifier.heightIn(max = 420.dp).then(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()))) }, confirmButton = { TextButton({ showProvenance = false }) { Text("Done") } })
}

@Composable private fun ProfileCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = DeepMoss, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}
@Composable private fun Metric(value: String, label: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, color = MutedCream, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, color = Leaf, style = MaterialTheme.typography.titleMedium)
    }
}
@Composable private fun ChoiceRow(label: String, values: List<Int>, selected: Int, tag: String, onSelect: (Int) -> Unit) {
    Text(label, color = Cream, style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value -> FilterChip(selected == value, { onSelect(value) }, { Text("$value") }, modifier = Modifier.testTag("$tag-$value")) }
    }
}
@Composable private fun SettingSwitch(label: String, checked: Boolean, tag: String, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Cream, modifier = Modifier.weight(1f))
        Switch(checked, onChecked, Modifier.testTag(tag))
    }
}
private fun accuracy(correct: Int, attempts: Int): String = if (attempts == 0) "—" else "${(100.0 * correct / attempts).toInt()}%"
private fun duration(ms: Long): String = "${ms / 3_600_000}h ${(ms / 60_000) % 60}m ${(ms / 1000) % 60}s"
