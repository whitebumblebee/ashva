// SPDX-License-Identifier: Apache-2.0
package com.openinglab.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.openinglab.app.ui.GameStudyUiState
import com.openinglab.app.ui.OriginalMoveTeachingUiState
import com.openinglab.app.ui.EngineAnalysisUiState
import com.openinglab.app.ui.components.ChessBoard
import com.openinglab.app.ui.theme.*
import com.openinglab.shared.games.LibraryScore
import com.openinglab.shared.model.PieceColor

@Composable
fun GameReplayScreen(study: GameStudyUiState, onBack: () -> Unit, onJump: (Int) -> Unit, onFlip: () -> Unit,
    onPlayPause: () -> Unit, onSpeed: () -> Unit, onPause: () -> Unit, modifier: Modifier = Modifier,
    moveTeaching: OriginalMoveTeachingUiState = OriginalMoveTeachingUiState.Idle,
    analysis: EngineAnalysisUiState = EngineAnalysisUiState.Idle,
    onAnalyze: () -> Unit = {}, onStopAnalysis: () -> Unit = {}, onExplore: (Int) -> Unit = {},
    onJumpAnalysis: (Int) -> Unit = {}, onReturnAnalysis: () -> Unit = {}, onRequestTeaching: () -> Unit = {}) {
    LifecycleEventEffect(Lifecycle.Event.ON_STOP, onEvent = onPause)
    LifecycleEventEffect(Lifecycle.Event.ON_START, onEvent = onRequestTeaching)
    DisposableEffect(Unit) { onDispose(onPause) }
    val replay = study.replay
    val previewing = (analysis as? EngineAnalysisUiState.Ready)?.previewIndex != null
    LazyColumn(modifier.testTag("game-replay-list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item("game-board") {
            TextButton(onBack, Modifier.testTag("game-back")) { Text("Back", color = Leaf) }
            Text("${study.score.white.name} – ${study.score.black.name}", color = Cream, style = MaterialTheme.typography.titleMedium)
            Text("ORIGINAL GAME · result ${study.score.result} · ${replay.playerSide.name} POV", color = Gold, modifier = Modifier.testTag("game-original"))
            Text("${study.score.event} · ${study.score.tags["Date"] ?: study.score.tags["UTCDate"] ?: "date unknown"}", color = MutedCream)
            Text("${replay.ply}/${replay.moves.size} half-moves · ${replay.position.sideToMove.name} to move", color = Cream, modifier = Modifier.testTag("game-position"))
            ChessBoard(replay.position, perspective = replay.playerSide, inputEnabled = false)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                TextButton({ onJump(0) }, enabled = !previewing && !replay.atStart, modifier = Modifier.testTag("game-first")) { Text("First") }
                TextButton({ onJump(replay.ply - 1) }, enabled = !previewing && !replay.atStart, modifier = Modifier.testTag("game-previous")) { Text("Previous") }
                TextButton(onPlayPause, enabled = !previewing && !replay.atEnd, modifier = Modifier.testTag("game-play")) { Text(if (study.isPlaying) "Pause" else "Play original") }
                TextButton({ onJump(replay.ply + 1) }, enabled = !previewing && !replay.atEnd, modifier = Modifier.testTag("game-next")) { Text("Next") }
                TextButton({ onJump(replay.moves.size) }, enabled = !previewing && !replay.atEnd, modifier = Modifier.testTag("game-last")) { Text("Last") }
                TextButton(onFlip, Modifier.testTag("game-flip")) { Text("Flip POV") }
                TextButton(onSpeed, Modifier.testTag("game-speed")) { Text("${study.playbackDelayMillis / 1000f}s") }
            }
            Text(replay.lastMove?.let { "Original move: ${it.san}" } ?: "Original starting position", color = Leaf,
                modifier = Modifier.testTag("game-last-move"))
            if (previewing) Text("Original playback is paused here. Return from the hypothetical line to continue this unchanged score.", color = Gold)
            when (moveTeaching) {
                OriginalMoveTeachingUiState.Loading -> Text("Checking this original move’s board changes…", color = MutedCream, modifier = Modifier.testTag("game-coach-loading"))
                OriginalMoveTeachingUiState.Failed -> {
                    Text("Move explanation unavailable; no historical intention was invented.", color = Gold)
                    TextButton(onRequestTeaching, Modifier.testTag("game-coach-retry")) { Text("Retry explanation") }
                }
                OriginalMoveTeachingUiState.Idle -> if (replay.atStart) Text("Step forward for an explanation of each recorded move. Choose Analyze to compare the next original move with engine candidates.", color = MutedCream)
                is OriginalMoveTeachingUiState.Ready -> {
                    val facts = moveTeaching.facts
                    Text("ASHVA BOARD FACT · ${facts.movingSide.name} played ${facts.idea.san} · ${facts.idea.title}", color = Leaf, modifier = Modifier.testTag("game-coach-move"))
                    Text(facts.idea.explanation, color = Cream, modifier = Modifier.testTag("game-coach-explanation"))
                    Text("CONDITIONAL PLAN · ${facts.idea.principle}", color = MutedCream, modifier = Modifier.testTag("game-coach-plan"))
                    Text("${facts.version} · ${if (moveTeaching.cached) "reused checked local explanation" else "checked offline explanation"}. Not a sourced annotation or the player’s verified intention.", color = MutedCream,
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("game-coach-provenance"))
                }
            }
            if (study.score is LibraryScore.Private) {
                Text("PRIVATE USER PGN · supplied annotations are unverified; this game is not in public source statistics.", color = MutedCream)
                replay.lastMove?.annotation?.comments?.takeIf { it.isNotEmpty() }?.let {
                    Text("USER-SUPPLIED COMMENTARY (not Ashva/GM-verified): ${it.joinToString("\n")}", color = MutedCream)
                }
            } else Text("Source mainline, not expert commentary or inferred GM intention. A reported result can reflect resignation/agreement before a board-terminal position.", color = MutedCream)
            PositionTeachingPanel(replay.position, replay.playerSide, "game-position-teaching")
            Text(replay.nextMove?.let { "Analysis root: this original position, before recorded ${it.san}. To compare the last played move, use Previous first." }
                ?: "End of recorded score. Analysis explores this board, not a replacement game result.", color = MutedCream, modifier = Modifier.testTag("game-analysis-root"))
            EngineAnalysisPanel(analysis, replay.playerSide, onAnalyze, onStopAnalysis, onExplore, onJumpAnalysis, onReturnAnalysis,
                contextLabel = "Your original score, result and saved game cursor stay unchanged during a preview.",
                returnLabel = "Return to unchanged original game", comparedLabel = "Next recorded original move")
            Text("Exact retained source: ${study.reference.packId ?: "private local import"}\n${study.reference.manifestSha256.orEmpty()}\nRecord ${study.reference.recordId}",
                color = MutedCream, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("game-exact-source"))
            Text("Full original move list · tap to replay. Studying is not a mastery score.", color = Gold)
        }
        itemsIndexed(replay.moves, key = { index, _ -> index }) { index, move ->
            val before = replay.graph.paths.getValue(replay.graph.originalPathId).positions[index]
            TextButton({ onJump(index + 1) }, Modifier.testTag("game-move-${index + 1}"), enabled = !previewing) {
                Text("${before.fullmoveNumber}${if (before.sideToMove == PieceColor.WHITE) "." else "..."} ${move.san}", color = if (index + 1 == replay.ply) Leaf else Cream)
            }
        }
    }
}
