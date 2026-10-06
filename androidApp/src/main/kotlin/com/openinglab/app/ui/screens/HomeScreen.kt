package com.openinglab.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.openinglab.app.ui.LearnerUiState
import com.openinglab.app.ui.TrainerUiState
import com.openinglab.app.ui.components.*
import com.openinglab.app.ui.theme.*
import com.openinglab.shared.chess.BoardPosition
import com.openinglab.shared.course.DeepCourseChapterSummary
import com.openinglab.shared.model.Opening

@Composable
fun HomeScreen(
    openings: List<Opening>, onOpeningClick: (String) -> Unit, onExploreAll: () -> Unit,
    onContinue: () -> Unit, onOfflineLibrary: () -> Unit, onRepertoires: () -> Unit,
    resume: TrainerUiState?, persistenceStatus: String, modifier: Modifier = Modifier,
    catalogLoading: Boolean = false, catalogError: String? = null, onGames: () -> Unit = {},
    gameResume: com.openinglab.app.ui.GameStudyUiState? = null,
    recall: com.openinglab.app.ui.RecallUiState = com.openinglab.app.ui.RecallUiState(), onReview: () -> Unit = {},
    deepCourses: List<DeepCourseChapterSummary> = emptyList(), deepCourseLoading: Boolean = false,
    deepCourseError: String? = null, onDeepCourse: (String) -> Unit = {}, developerMode: Boolean = false,
    learner: LearnerUiState = LearnerUiState(), onTactics: () -> Unit = {}, onOpeningPractice: () -> Unit = {},
    tacticsSummary: com.openinglab.app.tactics.TacticsTodaySummary? = null,
) {
    val ruy = openings.firstOrNull { it.name.replace('ó', 'o') == "Ruy Lopez" } ?: openings.firstOrNull()
    val order = listOf("Ruy Lopez", "London System", "Sicilian Defense", "French Defense", "Caro-Kann Defense", "Italian Game", "Queen's Gambit Declined")
    val ordered = openings.sortedBy { order.indexOf(it.name.replace('ó', 'o')).let { index -> if (index < 0) 100 else index } }
    val routine = learner.routine
    LazyColumn(modifier.testTag("home-list"), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            OpeningLabMark()
            Spacer(Modifier.height(12.dp))
            val greeting = when (learner.hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" }
            Text("$greeting, ${learner.preferences.displayName.ifBlank { "there" }}", color = Cream, style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.testTag("home-greeting"))
        }
        item {
            HomeCard(Modifier.testTag("home-today")) {
                Text("Today", color = Cream, style = MaterialTheme.typography.titleLarge)
                if (learner.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                learner.error?.let { Text(it, color = Gold, style = MaterialTheme.typography.bodySmall) }
                RoutineRow("Tactics · ${routine.settings.dailyPuzzles} puzzles", routine.puzzles, routine.settings.dailyPuzzles,
                    routine.tacticsDone, "routine-tactics", onTactics,
                    tacticsSummary?.activeSet?.let { "$it · Cycle ${tacticsSummary.cycle} · ${tacticsSummary.progress}/${tacticsSummary.puzzleCount}" })
                RoutineRow("Openings · practise ${routine.settings.dailyLines} lines", routine.lines, routine.settings.dailyLines,
                    routine.openingsDone, "routine-openings", onOpeningPractice)
                if (routine.showReview) Row(Modifier.fillMaxWidth().clickable(onClick = onReview).padding(vertical = 10.dp)
                    .testTag("home-open-review"), verticalAlignment = Alignment.CenterVertically) {
                    Text("○", color = Leaf, modifier = Modifier.padding(end = 12.dp))
                    Text("Review · ${routine.due} due", color = Cream, modifier = Modifier.weight(1f))
                    Text("→", color = Leaf)
                }
                if (routine.done && !learner.loading) Text("Routine done for today", color = Leaf, modifier = Modifier.testTag("routine-done"))
                HorizontalDivider(color = Divider)
                Text("${learner.week.studyDays} of ${routine.settings.weeklyGoal} study days this week", color = MutedCream,
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("home-study-week"))
                // Review remains available even before the first chosen scope exists.
                if (!routine.showReview) TextButton(onReview, Modifier.testTag("home-open-review")) { Text("Review", color = Leaf) }
            }
        }
        item {
            if (catalogLoading) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("home-catalog-loading"))
            catalogError?.let { Text(it, color = Gold, modifier = Modifier.testTag("home-catalog-error")) }
            if (gameResume != null) HomeCard(Modifier.clickable(onClick = onContinue).testTag("continue-lesson")) {
                Eyebrow("Continue")
                Text("${gameResume.score.white.name} – ${gameResume.score.black.name}", color = Cream, style = MaterialTheme.typography.titleMedium)
                Text("Move ${gameResume.replay.ply}/${gameResume.replay.moves.size} · ${gameResume.replay.playerSide.name.lowercase()} POV", color = MutedCream)
            } else if (ruy != null) ContinueCard(resume?.opening ?: ruy, resume, onContinue)
            Text(persistenceStatus, color = MutedCream, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp).testTag("persistence-status"))
        }
        if (deepCourses.isNotEmpty() || deepCourseLoading || deepCourseError != null) item {
            if (deepCourseLoading) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("deep-course-loading"))
            deepCourseError?.let { Text(it, color = Gold, modifier = Modifier.testTag("deep-course-error")) }
            deepCourses.distinctBy { it.course.id }.forEach { chapter ->
                val chapters = deepCourses.filter { it.course.id == chapter.course.id }
                val studied = learner.studiedIn(chapters.map { it.openingId }.toSet())
                HomeCard(Modifier.clickable { onDeepCourse(chapter.course.id) }.testTag("deep-pack-${chapter.course.id}")) {
                    Text(if (chapter.course.id == "ruy-lopez") "The Ruy Lopez · Complete course" else chapter.course.title,
                        color = Cream, style = MaterialTheme.typography.titleLarge)
                    val lines = chapters.sumOf { it.lineCount }
                    Text("$studied / $lines lines studied", color = MutedCream, style = MaterialTheme.typography.bodySmall)
                    RoundedProgressBar(if (lines == 0) 0f else studied.toFloat() / lines, Modifier.fillMaxWidth())
                    if (developerMode) Text(courseSubtitle(chapter.course), color = MutedCream, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        // Active tactics are already the routine's first row, including the real cycle progress.
        item { SectionHeader("Your opening courses", "Explore all", onExploreAll) }
        items(ordered.take(7), key = { it.id }) { opening ->
            HomeCard(Modifier.clickable { onOpeningClick(opening.id) }.testTag("opening-${opening.id}")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(opening.name, color = Cream, style = MaterialTheme.typography.titleMedium)
                        Text("${opening.eco} · ${opening.teaching?.sourceRoutes ?: opening.variations.size} routes", color = MutedCream, style = MaterialTheme.typography.bodySmall)
                        if (developerMode) Text(opening.identity, color = MutedCream, style = MaterialTheme.typography.bodySmall)
                    }
                    Text("→", color = Leaf)
                }
            }
        }
        item(key = "home-secondary-links") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onRepertoires, Modifier.testTag("my-repertoires")) { Text("My repertoires", color = Leaf) }
                TextButton(onGames, Modifier.testTag("gm-game-library")) { Text("Players & GM games", color = Leaf) }
                TextButton(onOfflineLibrary, Modifier.testTag("offline-library")) { Text("Offline library", color = Leaf) }
            }
        }
    }
}

@Composable
private fun HomeCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), color = DeepMoss, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Divider)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun RoutineRow(label: String, count: Int, target: Int, done: Boolean, tag: String, onClick: () -> Unit, subtitle: String? = null) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp).testTag(tag), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (done) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            if (done) "Complete" else "In progress", tint = if (done) Leaf else MutedCream, modifier = Modifier.padding(end = 12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = Cream, style = MaterialTheme.typography.titleSmall)
            subtitle?.let { Text(it, color = MutedCream, style = MaterialTheme.typography.bodySmall) }
            RoundedProgressBar(if (target <= 0) 0f else count.toFloat() / target, Modifier.fillMaxWidth().padding(top = 8.dp))
        }
        Text("$count/$target", color = if (done) Leaf else MutedCream, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun ContinueCard(opening: Opening, resume: TrainerUiState?, onClick: () -> Unit) {
    HomeCard(Modifier.clickable(onClick = onClick).testTag("continue-lesson")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Eyebrow(if (resume == null) "Start learning" else "Continue")
                Text(opening.name, color = Cream, style = MaterialTheme.typography.titleMedium)
                Text(resume?.variation?.name ?: opening.mainLine.name, color = MutedCream, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                if (resume != null) Text("${resume.playerSide.name.lowercase()} · ${resume.mode.name.lowercase()} · ${resume.ply}/${resume.replay.moves.size}", color = Leaf, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.width(12.dp))
            ChessBoard(resume?.position ?: BoardPosition.starting(), Modifier.size(74.dp), showCoordinates = false,
                perspective = resume?.playerSide ?: com.openinglab.shared.model.PieceColor.WHITE)
        }
    }
}
