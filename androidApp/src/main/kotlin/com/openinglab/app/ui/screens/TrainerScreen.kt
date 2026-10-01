package com.openinglab.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FirstPage
import androidx.compose.material.icons.automirrored.rounded.LastPage
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.openinglab.app.ui.FeedbackKind
import com.openinglab.app.ui.LessonMode
import com.openinglab.app.ui.TrainerUiState
import com.openinglab.app.ui.components.ChessBoard
import com.openinglab.app.ui.theme.Cream
import com.openinglab.app.ui.theme.DeepMoss
import com.openinglab.app.ui.theme.Divider
import com.openinglab.app.ui.theme.Gold
import com.openinglab.app.ui.theme.Ink
import com.openinglab.app.ui.theme.Leaf
import com.openinglab.app.ui.theme.Moss
import com.openinglab.app.ui.theme.MutedCream
import com.openinglab.shared.lesson.LessonBranch
import com.openinglab.shared.model.PieceColor
import com.openinglab.shared.model.PieceType

@Composable
fun TrainerScreen(
    state: TrainerUiState,
    onBack: () -> Unit,
    onSquareTap: (String) -> Unit,
    onHint: () -> Unit,
    onRestart: () -> Unit,
    onStudy: () -> Unit,
    onPractice: () -> Unit,
    onJump: (Int) -> Unit,
    onPlayPause: () -> Unit,
    onSpeed: () -> Unit,
    onFlip: () -> Unit,
    onStay: () -> Unit,
    onSwitch: (LessonBranch) -> Unit,
    onReturn: () -> Unit,
    onPromotion: (PieceType) -> Unit,
    onCancelPromotion: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LifecycleEventEffect(Lifecycle.Event.ON_STOP, onEvent = onPause)
    LifecycleEventEffect(Lifecycle.Event.ON_START, onEvent = onResume)
    DisposableEffect(Unit) { onDispose(onPause) }
    val study = state.mode == LessonMode.STUDY
    Column(modifier.testTag("lesson-scroll").verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        Row(Modifier.fillMaxWidth().padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Cream) }
            Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                Text(state.opening.name, color = Cream, style = MaterialTheme.typography.titleMedium)
                Text(state.replay.path.name, color = MutedCream, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("active-variation"))
            }
            TextButton(onFlip, Modifier.testTag("flip-side")) {
                Text(state.playerSide.name.lowercase().replaceFirstChar { it.uppercase() } + " ⇄", color = Leaf)
            }
        }
        LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth().height(3.dp), color = Leaf, trackColor = Divider)
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LessonButton(if (study) "Studying" else "Full idea", "study-mode", onStudy, Modifier.weight(1f), selected = study)
                LessonButton(if (study) "Practice from start" else "Practicing", "practice-mode", onPractice,
                    Modifier.weight(1f), selected = !study)
            }
            Text("AUTHORED SEED · ${state.replay.moves.size} half-moves · not a full repertoire", color = MutedCream,
                style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(vertical = 10.dp).testTag("seed-coverage"))
            Text(if (study) "Study · ${state.ply}/${state.replay.moves.size} · ${state.position.sideToMove.name.lowercase()} to move"
                else if (state.isOpponentThinking) "Playing the lesson reply…" else "Practice · ${state.playerSide.name.lowercase()} POV",
                color = Cream, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 8.dp).testTag("lesson-position"))
            ChessBoard(position = state.position, perspective = state.playerSide, selectedSquare = state.selectedSquare,
                legalTargets = state.legalTargets, hintSquares = state.hintSquares, onSquareTap = onSquareTap,
                inputEnabled = !study && !state.isOpponentThinking && !state.isComplete)
            Spacer(Modifier.height(8.dp))
            if (study) {
                ReplayControls(state, onJump, onPlayPause, onSpeed)
                MoveIdea(state)
            } else {
                TeachingCard {
                    Text(state.feedback, color = if (state.feedbackKind == FeedbackKind.ERROR) Gold else Leaf,
                        style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag("lesson-feedback"))
                    if (state.hintSquares.isNotEmpty()) state.replay.nextMove?.let {
                        Text("Expected: ${it.san} · ${it.move.from} → ${it.move.to}", color = Gold,
                            modifier = Modifier.testTag("expected-move"))
                    }
                    Text(state.explanation, color = Cream, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (state.branchOffers.isNotEmpty()) {
                TeachingCard {
                    Text("Choose a continuation", color = Gold, style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.testTag("branch-choice"))
                    Text("Playback is paused here. The board stays unchanged until you choose.", color = MutedCream,
                        style = MaterialTheme.typography.bodySmall)
                    LessonButton("Stay on ${state.replay.path.name}", "stay-line", onStay)
                    state.branchOffers.forEach { branch ->
                        Text("${branch.name} · ${branch.nextMove.san}", color = Cream, style = MaterialTheme.typography.titleSmall)
                        Text(branch.description, color = MutedCream, style = MaterialTheme.typography.bodySmall)
                        LessonButton("${if (study) "Explore" else "Switch to"} ${branch.name}", "branch-${branch.pathId}", { onSwitch(branch) })
                    }
                }
            }
            if (state.replay.canReturn) {
                LessonButton("Return to branch point", "return-branch", onReturn, Modifier.fillMaxWidth())
            }
            if (study) {
                TeachingCard {
                    Text("Full idea · ${state.playerSide.name.lowercase()} POV", color = Leaf,
                        style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("full-idea"))
                    Text(state.replay.path.description, color = Cream)
                    Text(if (state.playerSide == PieceColor.WHITE) state.replay.path.whiteIdea else state.replay.path.blackIdea,
                        color = Cream, modifier = Modifier.testTag("chosen-plan"))
                    Text("Opponent's plan", color = Gold, style = MaterialTheme.typography.labelLarge)
                    Text(if (state.playerSide == PieceColor.WHITE) state.replay.path.blackIdea else state.replay.path.whiteIdea,
                        color = MutedCream, style = MaterialTheme.typography.bodySmall)
                    Text("Authored introductory plans, not engine analysis or a promise of a win.", color = MutedCream,
                        style = MaterialTheme.typography.labelSmall)
                }
            }
            Text(if (study) "Full line · tap any move to replay" else "Moves played · tap to study", color = MutedCream,
                style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
            FlowRow(Modifier.fillMaxWidth().testTag("lesson-moves"), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val moves = if (study) state.replay.moves else state.replay.moves.take(state.ply)
                moves.forEachIndexed { i, step ->
                    val before = state.replay.jump(i).position
                    val notation = "${before.fullmoveNumber}${if (before.sideToMove == PieceColor.WHITE) "." else "..."} ${step.san}"
                    TextButton({ onJump(i + 1) }, modifier = Modifier.testTag("move-${i + 1}")
                        .background(if (i + 1 == state.ply) Moss else DeepMoss, RoundedCornerShape(9.dp))) {
                        Text(notation, color = if (i + 1 == state.ply) Leaf else Cream)
                    }
                }
            }
            if (!study && state.isComplete) {
                TeachingCard {
                    Text("Practice complete", color = Leaf, style = MaterialTheme.typography.titleMedium)
                    Text("${state.mistakes} retries · ${state.assistedMoves} assisted ${if (state.assistedMoves == 1) "move" else "moves"}", color = Cream)
                    Text(if (state.hasStudied) "You studied this line first. Completion is not a mastery score."
                        else "Practice covers this seed line only; no repertoire mastery is recorded.", color = MutedCream,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            if (!study) Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LessonButton("Hint", "show-hint", onHint, Modifier.weight(1f), enabled = !state.isComplete && !state.isOpponentThinking)
                LessonButton("Restart practice", "restart-practice", onRestart, Modifier.weight(1f))
            }
        }
    }
    if (state.pendingPromotion != null) AlertDialog(onDismissRequest = onCancelPromotion,
        title = { Text("Choose promotion") }, text = {
            Column { listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT).forEach { type ->
                TextButton({ onPromotion(type) }) { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }) }
            } }
        }, confirmButton = {}, dismissButton = { TextButton(onCancelPromotion) { Text("Cancel") } })
}

@Composable
private fun MoveIdea(state: TrainerUiState) {
    TeachingCard {
        val last = state.replay.lastMove
        Text(last?.let { "${it.san} · ${it.annotation.title}" } ?: "Starting position", color = Leaf,
            style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag("move-explanation"))
        if (last != null) {
            Text(if (state.replay.jump(state.ply - 1).position.sideToMove == state.playerSide) "Your move" else "Opponent's move",
                color = Gold, style = MaterialTheme.typography.labelMedium)
        }
        Text(state.explanation, color = Cream)
        last?.annotation?.principle?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = MutedCream, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ReplayControls(state: TrainerUiState, onJump: (Int) -> Unit, onPlayPause: () -> Unit, onSpeed: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        IconButton({ onJump(0) }, Modifier.testTag("replay-first"), enabled = !state.replay.atStart) {
            Icon(Icons.Rounded.FirstPage, "First position", tint = MutedCream)
        }
        IconButton({ onJump(state.ply - 1) }, Modifier.testTag("replay-previous"), enabled = !state.replay.atStart) {
            Icon(Icons.Rounded.SkipPrevious, "Previous move", tint = MutedCream)
        }
        IconButton(onPlayPause, Modifier.testTag("replay-play"), enabled = !state.isComplete && state.branchOffers.isEmpty()) {
            Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (state.isPlaying) "Pause replay" else "Play replay", tint = Leaf)
        }
        IconButton({ onJump(state.ply + 1) }, Modifier.testTag("replay-next"), enabled = !state.isComplete) {
            Icon(Icons.Rounded.SkipNext, "Next move", tint = MutedCream)
        }
        IconButton({ onJump(state.replay.moves.size) }, Modifier.testTag("replay-last"), enabled = !state.isComplete) {
            Icon(Icons.AutoMirrored.Rounded.LastPage, "Last position", tint = MutedCream)
        }
        TextButton(onSpeed, Modifier.testTag("replay-speed")) { Text("${state.playbackDelayMillis / 1000f}s", color = Gold) }
    }
}

@Composable
private fun TeachingCard(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 7.dp).background(DeepMoss, RoundedCornerShape(18.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)) { content() }
}

@Composable
private fun LessonButton(label: String, tag: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    selected: Boolean = false, enabled: Boolean = true) {
    if (selected) Button(onClick, modifier.testTag(tag), enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = Leaf, contentColor = Ink), shape = RoundedCornerShape(12.dp)) { Text(label) }
    else OutlinedButton(onClick, modifier.testTag(tag), enabled = enabled, border = BorderStroke(1.dp, Divider),
        shape = RoundedCornerShape(12.dp)) { Text(label, color = if (enabled) Cream else MutedCream) }
}
