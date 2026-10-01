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

@Composable
fun HomeScreen(
    openings: List<Opening>,
    onOpeningClick: (String) -> Unit,
    onExploreAll: () -> Unit,
    onContinue: () -> Unit,
    onOfflineLibrary: () -> Unit,
    resume: TrainerUiState?,
    persistenceStatus: String,
    modifier: Modifier = Modifier,
) {
    val ruy = openings.first { it.id == "ruy-lopez" }
    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
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
                    Text("DEMO", color = Gold, style = MaterialTheme.typography.labelLarge)
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
                    "Study opening ideas and practice short starter lines. Full repertoires and GM-game coaching are still in development.",
                    color = MutedCream,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        item {
            ContinueCard(resume?.opening ?: ruy, resume, onContinue)
            Text(persistenceStatus, color = MutedCream, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp).testTag("persistence-status"))
            TextButton(onClick = onOfflineLibrary, modifier = Modifier.testTag("offline-library")) { Text("Offline library & sources", color = Leaf) }
        }

        item {
            Text("Alpha preview · repertoire percentages and review statistics are examples, not your measured progress.",
                color = MutedCream, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(10.dp))
            DailyDrillCard(onClick = onContinue)
        }

        item { SectionHeader("Your repertoire", "Explore all", onExploreAll) }

        items(openings.take(4), key = { it.id }) { opening ->
            OpeningCard(opening = opening, onClick = { onOpeningClick(opening.id) }, modifier = Modifier.fillMaxWidth())
        }

        item {
            QuoteCard()
            Spacer(Modifier.height(4.dp))
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
                    Text(if (resume == null) "Authored starter lesson" else
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
private fun DailyDrillCard(onClick: () -> Unit) {
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
                Text("5 positions are due", color = Cream, style = MaterialTheme.typography.titleMedium)
                Text("Keep the 7-day streak alive", color = MutedCream, style = MaterialTheme.typography.bodySmall)
            }
            PrimaryAction("Review", onClick, modifier = Modifier.width(102.dp), icon = null, color = Gold)
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
