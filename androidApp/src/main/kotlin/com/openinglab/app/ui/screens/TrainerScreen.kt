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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.openinglab.app.ui.FeedbackKind
import com.openinglab.app.ui.LessonMode
import com.openinglab.app.ui.TrainerUiState
import com.openinglab.app.ui.EngineAnalysisUiState
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
    onBuildRepertoire: () -> Unit,
    modifier: Modifier = Modifier,
    analysis: EngineAnalysisUiState = EngineAnalysisUiState.Idle,
    onAnalyze: () -> Unit = {},
    onStopAnalysis: () -> Unit = {},
    onExploreAnalysis: (Int) -> Unit = {},
    onJumpAnalysis: (Int) -> Unit = {},
    onReturnAnalysis: () -> Unit = {},
    onSetPrevious: () -> Unit = {}, onSetNext: () -> Unit = {},
    preparing: Boolean = false, loadError: String? = null,
    recallError: String? = null, onNextReview: () -> Unit = {}, recallRetryAvailable: Boolean = false, onRetryRecall: () -> Unit = {},
    onFlag: ((String) -> Unit)? = null, flagMessage: String? = null,
) {
    LifecycleEventEffect(Lifecycle.Event.ON_STOP, onEvent = onPause)
    LifecycleEventEffect(Lifecycle.Event.ON_START, onEvent = onResume)
    DisposableEffect(Unit) { onDispose(onPause) }
    val study = state.mode == LessonMode.STUDY
    val source = state.opening.provenance
    val teaching = state.opening.teaching
    val deep = state.opening.family == "Deep course"
    var visibleBranches by rememberSaveable(state.replay.pathId, state.ply) { mutableIntStateOf(12) }
    Column(modifier.testTag("lesson-scroll").verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        Row(Modifier.fillMaxWidth().padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Cream) }
            Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                Text(state.opening.name, color = Cream, style = MaterialTheme.typography.titleMedium)
                Text(state.replay.path.name, color = MutedCream, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("active-variation"))
            }
            TextButton(onFlip, Modifier.testTag("flip-side"), enabled = state.repertoirePolicy == null && state.reviewTargetId == null) {
                Text(state.playerSide.name.lowercase().replaceFirstChar { it.uppercase() } + " ⇄", color = Leaf)
            }
        }
        LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth().height(3.dp), color = Leaf, trackColor = Divider)
        Column(Modifier.padding(horizontal = 16.dp)) {
            if (preparing) Text("Preparing the next repertoire route…", color = MutedCream, modifier = Modifier.testTag("set-route-loading"))
            loadError?.let { Text(it, color = Gold, modifier = Modifier.testTag("set-route-error")) }
            recallError?.let { Text(it, color = Gold, modifier = Modifier.testTag("trainer-recall-error")) }
            if (recallRetryAvailable) LessonButton("Retry pending saves", "trainer-retry-recall", onRetryRecall)
            if (state.reviewTargetId != null) {
                Text("ONE-POSITION RECALL · exact chosen scope", color = Gold, modifier = Modifier.testTag("review-card-context"))
                Text("Study or analyze if needed; exposed help is assistance. Return to openings to explore other branches.", color = MutedCream, style = MaterialTheme.typography.bodySmall)
                LessonButton("Next due position", "next-review-card", onNextReview, enabled = !preparing && state.reviewAnswered && state.reviewSaved)
            }
            state.repertoireSetSession?.let { session ->
                Text("${session.plan.set.name} · ${session.plan.set.side.name} · route ${session.index + 1}/${session.plan.items.size}",
                    color = Gold, modifier = Modifier.padding(top = 12.dp).testTag("set-practice-position"))
                Text("Pinned set revision ${session.plan.set.revision}. Route navigation is not recall mastery; practice each included opponent reply. Original family routes remain separate.", color = MutedCream,
                    style = MaterialTheme.typography.bodySmall)
                Row {
                    TextButton(onSetPrevious, enabled = !preparing && session.index > 0, modifier = Modifier.testTag("set-previous")) { Text("Previous route") }
                    TextButton(onSetNext, enabled = !preparing && session.index < session.plan.items.lastIndex, modifier = Modifier.testTag("set-next")) { Text("Next route") }
                }
            }
            TextButton(onBuildRepertoire, modifier = Modifier.testTag("build-repertoire")) { Text("Build / edit my repertoire", color = Leaf) }
            state.repertoirePolicy?.let {
                Text("MY REPERTOIRE · ${it.side.name} · revision ${it.revision} · only included source routes. Edit choices to cover other replies; use the explorer for the full snapshot.", color = Gold,
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("policy-practice-scope"))
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LessonButton(if (study) "Studying" else "Full idea", "study-mode", onStudy, Modifier.weight(1f), selected = study)
                LessonButton(if (study) "Practice from start" else "Practicing", "practice-mode", onPractice,
                    Modifier.weight(1f), selected = !study)
            }
            if (deep) Text("DEEP COURSE · ${state.variation.category} · generated text from checked claims · ${state.replay.moves.size} half-moves",
                color = Gold, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 10.dp).testTag("deep-course-coverage"))
            else Text("${if (teaching != null) if (state.variation.authoredContinuation) "AUTHORED STUDY CONTINUATION" else "NAMED SOURCE ROUTE · ASHVA GUIDE" else if (source == null) "AUTHORED SEED" else "SOURCED · ${source.license}"} · ${state.replay.moves.size} half-moves · finite course coverage", color = MutedCream,
                style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(vertical = 10.dp).testTag(if (source == null) "seed-coverage" else "source-coverage"))
            Text(if (study) "Study · ${state.ply}/${state.replay.moves.size} · ${state.position.sideToMove.name.lowercase()} to move"
                else if (state.isOpponentThinking) "Playing the lesson reply…" else "Practice · ${state.playerSide.name.lowercase()} POV",
                color = Cream, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 8.dp).testTag("lesson-position"))
            ChessBoard(position = state.position, perspective = state.playerSide, selectedSquare = state.selectedSquare,
                legalTargets = state.legalTargets, hintSquares = state.hintSquares, onSquareTap = onSquareTap,
                inputEnabled = !preparing && !study && !state.isOpponentThinking && !state.isComplete)
            Spacer(Modifier.height(8.dp))
            if (study) {
                ReplayControls(state, onJump, onPlayPause, onSpeed)
                MoveIdea(state, onFlag, flagMessage)
                PositionTeachingPanel(state.position, state.playerSide)
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
                    if (state.branchOffers.size > 12) Text("Showing ${minOf(visibleBranches, state.branchOffers.size)} of ${state.branchOffers.size} continuations",
                        color = MutedCream, modifier = Modifier.testTag("branch-count"))
                    state.branchOffers.take(visibleBranches).forEach { branch ->
                        Text("${branch.name} · ${branch.nextMove.san}", color = Cream, style = MaterialTheme.typography.titleSmall)
                        Text(branch.description, color = MutedCream, style = MaterialTheme.typography.bodySmall)
                        LessonButton("${if (study) "Explore" else "Switch to"} ${branch.name}", "branch-${branch.pathId}", { onSwitch(branch) })
                    }
                    if (visibleBranches < state.branchOffers.size) LessonButton("Show more continuations", "more-branches", { visibleBranches += 12 })
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
                    if (deep) Text("Generated course text: each sentence passed an automatic engine, game-statistics or board check before it was shown. Not reviewed by a human coach; engine verdicts are at a stated depth, not proof.",
                        color = MutedCream, style = MaterialTheme.typography.labelSmall, modifier = Modifier.testTag("deep-full-idea-note"))
                    else Text(if (teaching != null) "Ashva-authored guidance plus rules-derived board observations. Plans are conditional, not engine evaluations or verified historical intention. Named source routes end at their recorded endpoint; authored continuations are separate." else if (source == null) "Authored introductory plans, not engine analysis or a promise of a win." else
                        "Source move sequence only. Reviewed strategic plans are unavailable; optional offline analysis below is separate from the source lesson.", color = MutedCream,
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
                    Text(if (state.reviewTargetId != null) "Review answer complete" else "Practice complete", color = Leaf, style = MaterialTheme.typography.titleMedium)
                    Text("${state.mistakes} retries · ${state.assistedMoves} assisted ${if (state.assistedMoves == 1) "move" else "moves"}", color = Cream)
                    Text(if (state.hasStudied) "You studied this line first. Completion is not a mastery score."
                        else "Recorded answers contribute to chosen-scope recall. Completing a line once is not long-term mastery.", color = MutedCream,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            if (!study) Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LessonButton("Hint", "show-hint", onHint, Modifier.weight(1f), enabled = !state.isComplete && !state.isOpponentThinking)
                LessonButton("Restart practice", "restart-practice", onRestart, Modifier.weight(1f))
            }
            EngineAnalysisPanel(analysis, state.playerSide, onAnalyze, onStopAnalysis, onExploreAnalysis, onJumpAnalysis, onReturnAnalysis)
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
private fun MoveIdea(state: TrainerUiState, onFlag: ((String) -> Unit)? = null, flagMessage: String? = null) {
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
        last?.annotation?.players?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = Leaf, style = MaterialTheme.typography.labelSmall, modifier = Modifier.testTag("explanation-players"))
        }
        last?.annotation?.label?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = Gold, style = MaterialTheme.typography.labelSmall, modifier = Modifier.testTag("explanation-label"))
        }
        if (onFlag != null && last != null) Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Explanation wrong or unclear?", color = MutedCream, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            TextButton({ onFlag("wrong") }, Modifier.testTag("flag-wrong")) { Text("Wrong", color = Gold) }
            TextButton({ onFlag("unclear") }, Modifier.testTag("flag-unclear")) { Text("Unclear", color = Gold) }
        }
        flagMessage?.takeIf { onFlag != null }?.let { Text(it, color = Leaf, style = MaterialTheme.typography.labelSmall, modifier = Modifier.testTag("flag-message")) }
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
