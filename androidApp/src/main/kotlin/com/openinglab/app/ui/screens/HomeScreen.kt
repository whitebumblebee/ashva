package com.openinglab.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.openinglab.app.ui.components.ChessBoard
import com.openinglab.app.ui.components.Eyebrow
import com.openinglab.app.ui.components.OpeningCard
import com.openinglab.app.ui.components.OpeningLabMark
import com.openinglab.app.ui.components.PrimaryAction
import com.openinglab.app.ui.components.SectionHeader
import com.openinglab.app.ui.theme.Cream
import com.openinglab.app.ui.theme.DeepMoss
import com.openinglab.app.ui.theme.Divider
import com.openinglab.app.ui.theme.Gold
import com.openinglab.app.ui.theme.Ink
import com.openinglab.app.ui.theme.Leaf
import com.openinglab.app.ui.theme.Moss
import com.openinglab.app.ui.theme.MutedCream
import com.openinglab.app.ui.TrainerUiState
import androidx.compose.ui.platform.testTag
import com.openinglab.shared.chess.BoardPosition
import com.openinglab.shared.data.OpeningCatalog
import com.openinglab.shared.model.ChessMove
import com.openinglab.shared.model.Opening
import com.openinglab.shared.course.DeepCourseChapterView

@Composable
fun HomeScreen(
    openings: List<Opening>,
    onOpeningClick: (String) -> Unit,
    onExploreAll: () -> Unit,
    onContinue: () -> Unit,
    onOfflineLibrary: () -> Unit,
    onRepertoires: () -> Unit,
    resume: TrainerUiState?,
    persistenceStatus: String,
    modifier: Modifier = Modifier,
    catalogLoading: Boolean = false,
    catalogError: String? = null,
    onGames: () -> Unit = {},
    gameResume: com.openinglab.app.ui.GameStudyUiState? = null,
    recall: com.openinglab.app.ui.RecallUiState = com.openinglab.app.ui.RecallUiState(), onReview: () -> Unit = {},
    deepCourses: List<DeepCourseChapterView> = emptyList(),
    deepCourseLoading: Boolean = false,
    deepCourseError: String? = null,
    onDeepCourse: (String) -> Unit = {},
) {
    val ruy = openings.firstOrNull { it.name.replace('ó', 'o') == "Ruy Lopez" } ?: openings.first()
    val ordered = openings.sortedBy { opening ->
        listOf("Ruy Lopez", "London System", "Sicilian Defense", "French Defense", "Caro-Kann Defense", "Italian Game", "Queen's Gambit Declined").indexOf(opening.name.replace('ó', 'o')).let { if (it < 0) 100 else it }
    }
    LazyColumn(
        modifier = modifier.testTag("home-list"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item { TextButton(onRepertoires, modifier = Modifier.testTag("my-repertoires")) { Text("My repertoires →", color = Leaf) } }
        item { TextButton(onGames, modifier = Modifier.testTag("gm-game-library")) { Text("Players & GM games →", color = Leaf) } }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OpeningLabMark(Modifier.weight(1f))
                Row(
                    Modifier
                        .clip(CircleShape)
                        .background(Gold.copy(alpha = .12f))
                        .border(1.dp, Gold.copy(alpha = .36f), CircleShape)
                        .padding(horizontal = 11.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Bolt, null, tint = Gold, modifier = Modifier.size(17.dp))
                    Text("LEARN", color = Gold, style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.width(10.dp))
                Box(Modifier.size(36.dp).clip(CircleShape).background(Leaf), contentAlignment = Alignment.Center) {
                    Text("♞", color = Ink, style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        item {
            Column {
                Eyebrow("Your repertoire, move by move")
                Spacer(Modifier.height(7.dp))
                Text("Build positions\nyou can trust.", color = Cream, style = MaterialTheme.typography.displayMedium)
                Spacer(Modifier.height(12.dp))
                Text(
                    if (openings.any { it.teaching != null }) "${openings.size} opening families. Study both sides, understand the moves and explore named variations." else
                        "Preparing opening courses. Starter lessons remain available while the local catalog loads.",
                    color = MutedCream,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        item {
            if (catalogLoading) Text("Preparing the offline opening courses…", color = Leaf, modifier = Modifier.testTag("home-catalog-loading"))
            catalogError?.let { Text(it, color = Gold, modifier = Modifier.testTag("home-catalog-error")) }
            if (gameResume == null) ContinueCard(resume?.opening ?: ruy, resume, onContinue)
            else Column {
                Text("Continue original game", color = Leaf)
                Text("${gameResume.score.white.name} – ${gameResume.score.black.name}", color = Cream)
                Text("${gameResume.replay.playerSide.name} POV · ${gameResume.replay.ply}/${gameResume.replay.moves.size} half-moves · exact retained score", color = MutedCream)
                TextButton(onContinue, modifier = Modifier.testTag("continue-lesson")) { Text("Continue game") }
            }
            Text(persistenceStatus, color = MutedCream, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp).testTag("persistence-status"))
            TextButton(onClick = onOfflineLibrary, modifier = Modifier.testTag("offline-library")) { Text("Offline library & sources", color = Leaf) }
        }

        if (deepCourses.isNotEmpty() || deepCourseLoading || deepCourseError != null) item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Eyebrow("Deep courses · generated, engine-checked", color = Gold)
                if (deepCourseLoading) Text("Checking the bundled deep course…", color = Leaf, modifier = Modifier.testTag("deep-course-loading"))
                deepCourseError?.let { Text(it, color = Gold, modifier = Modifier.testTag("deep-course-error")) }
                deepCourses.forEach { chapter -> DeepCourseCard(chapter) { onDeepCourse(chapter.opening.id) } }
            }
        }

        item {
            Text("Local recall uses your chosen route, family or named-set revision. Completing a line once is not long-term mastery.",
                color = MutedCream, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(10.dp))
            DailyDrillCard(onClick = onReview, recall = recall)
        }

        item { SectionHeader("Your repertoire", "Explore all", onExploreAll) }

        items(ordered, key = { it.id }) { opening ->
            OpeningCard(opening = opening, onClick = { onOpeningClick(opening.id) }, modifier = Modifier.fillMaxWidth().testTag("opening-${opening.id}"))
        }

        item {
            QuoteCard()
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun DeepCourseCard(chapter: DeepCourseChapterView, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).clickable(onClick = onClick).testTag("deep-course-${chapter.chapter.id}"),
        color = DeepMoss, shape = RoundedCornerShape(22.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Gold.copy(alpha = .4f)),
    ) {
        Column(Modifier.padding(18.dp)) {
            Eyebrow(if (chapter.chapter.kind == "GAME") "GM game · both points of view" else "${chapter.course.title} · chapter", color = Gold)
            Spacer(Modifier.height(6.dp))
            Text(chapter.chapter.title, color = Cream, style = MaterialTheme.typography.titleLarge)
            Text(chapter.chapter.coverage?.let { "${it.lines} lines · ${chapter.chapter.variations.size.takeIf { n -> n > 0 }?.let { n -> "$n variations · " } ?: ""}${it.minPlies}–${it.maxPlies} half-moves" }
                ?: chapter.chapter.game?.let { "${it.white} – ${it.black} · ${it.event} · ${it.result}" } ?: "${chapter.opening.variations.size} lines",
                color = MutedCream, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ContinueCard(opening: Opening, resume: TrainerUiState?, onClick: () -> Unit) {
    val position = resume?.position ?: BoardPosition.starting()
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("continue-lesson").clip(RoundedCornerShape(26.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(26.dp),
        color = Leaf,
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Eyebrow(if (resume == null) "Start learning" else "Continue learning", color = Ink.copy(alpha = .64f))
                Spacer(Modifier.height(8.dp))
                Text(opening.name, color = Ink, style = MaterialTheme.typography.headlineMedium)
                Text(resume?.variation?.name ?: opening.mainLine.name, color = Ink.copy(alpha = .68f), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(19.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Schedule, null, tint = Ink.copy(alpha = .64f), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (resume == null) if (opening.teaching != null) "Opening course · both colors" else "Authored starter lesson" else
                        "${resume.playerSide.name.lowercase()} · ${resume.mode.name.lowercase()} · Move ${resume.ply}/${resume.replay.moves.size}",
                        color = Ink.copy(alpha = .74f), style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.width(14.dp))
            ChessBoard(position, modifier = Modifier.size(126.dp), showCoordinates = false,
                perspective = resume?.playerSide ?: com.openinglab.shared.model.PieceColor.WHITE)
        }
    }
}

@Composable
private fun DailyDrillCard(onClick: () -> Unit, recall: com.openinglab.app.ui.RecallUiState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = DeepMoss,
        shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Divider),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(Gold.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                Text("♟", color = Gold, style = MaterialTheme.typography.headlineLarge)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Eyebrow("Daily recall", color = Gold)
                Text(if (recall.loading) "Loading your schedules…" else if (recall.scopes.isEmpty()) "Choose a line to practice" else "${recall.scopes.size} chosen review scopes", color = Cream, style = MaterialTheme.typography.titleMedium)
                Text("Select a scope to see real due decisions", color = MutedCream, style = MaterialTheme.typography.bodySmall)
            }
            PrimaryAction("Open review", onClick, modifier = Modifier.width(102.dp).testTag("home-open-review"), icon = null, color = Gold)
        }
    }
}

@Composable
private fun QuoteCard() {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Moss)
            .padding(22.dp)
    ) {
        Text("“", color = Leaf, style = MaterialTheme.typography.displayLarge)
        Text(
            "The good player is always lucky — because they have built positions where luck can find them.",
            color = Cream,
            style = MaterialTheme.typography.bodyLarge,
            fontStyle = FontStyle.Italic,
        )
        Spacer(Modifier.height(8.dp))
        Eyebrow("Ashva study note · not a historical quote", color = MutedCream)
    }
}
