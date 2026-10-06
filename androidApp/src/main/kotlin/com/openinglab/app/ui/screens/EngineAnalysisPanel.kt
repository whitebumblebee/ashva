// SPDX-License-Identifier: Apache-2.0
package com.openinglab.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.openinglab.app.ui.EngineAnalysisUiState
import com.openinglab.app.ui.components.ChessBoard
import com.openinglab.app.ui.components.ExpandableText
import com.openinglab.app.ui.theme.*
import com.openinglab.shared.analysis.*
import com.openinglab.shared.model.PieceColor
import java.util.Locale

@Composable
fun EngineAnalysisPanel(state: EngineAnalysisUiState, side: PieceColor, onAnalyze: () -> Unit, onStop: () -> Unit,
                        onExplore: (Int) -> Unit, onJump: (Int) -> Unit, onReturn: () -> Unit,
                        contextLabel: String = "Your lesson and repertoire stay unchanged.",
                        returnLabel: String = "Return to unchanged lesson", comparedLabel: String = "Compared move", developerMode: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp).testTag("engine-analysis"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Analyze alternatives", color = Leaf, style = MaterialTheme.typography.titleMedium)
        ExpandableText("Offline, bounded analysis—not a guarantee. Continuation explanations describe legal board changes, not why an engine or a GM intended a move. $contextLabel", color = MutedCream)
        if (state == EngineAnalysisUiState.Loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("Analyzing candidates and the compared move…", color = Cream, modifier = Modifier.testTag("engine-loading"))
            OutlinedButton(onStop, Modifier.testTag("engine-stop")) { Text("Stop analysis") }
        } else OutlinedButton(onAnalyze, Modifier.testTag("engine-analyze")) { Text(if (state == EngineAnalysisUiState.Idle) "Analyze this position" else "Analyze again") }
        if (state is EngineAnalysisUiState.Error) Text(state.message, color = Gold, modifier = Modifier.testTag("engine-error"))
        if (state is EngineAnalysisUiState.Ready) {
            val result = state.result
            if (developerMode) Text("${result.engine.name} ${result.engine.version} · ${side.name} score perspective · 1 thread / ${result.budget.hashMiB} MiB hash. Each search: ≤${result.budget.depth} depth, ${result.budget.nodes} nodes, ${result.budget.moveTimeMillis} ms.",
                color = MutedCream, modifier = Modifier.testTag("engine-budget"))
            if (!result.position.hasHistoryFromStart) Text("FEN start: earlier repetitions are unknown.", color = Gold)
            result.terminal?.let { Text("Terminal position: ${it.name}. No engine search needed.", color = Cream, modifier = Modifier.testTag("engine-terminal")) }
            result.alternatives?.let {
                if (!it.completeCandidateSet) Text("Partial candidate set at the stopping budget; no missing lines were invented.", color = Gold)
            }
            state.lines.forEachIndexed { index, line ->
                val compared = index >= result.alternatives?.lines.orEmpty().size
                val score = line.score.forSide(result.rootSide, side)
                val label = if (compared) comparedLabel else if (line.rank == 1) "Strongest found at this budget" else "Candidate ${line.rank}"
                Text("$label: ${line.san.first()} · ${scoreText(score)} · depth ${line.depth}, ${line.nodes} nodes, ${line.timeMillis} ms", color = Cream,
                    modifier = Modifier.testTag("engine-line-$index"))
                ExpandableText(line.san.joinToString(" "), color = MutedCream)
                state.previewExplanations.getOrNull(index)?.firstOrNull()?.let { idea ->
                    ExpandableText("${if (developerMode) "BOARD FACT · " else ""}${idea.title}: ${idea.explanation}", color = Cream, modifier = Modifier.testTag("engine-idea-$index"))
                }
                TextButton({ onExplore(index) }, Modifier.testTag("engine-explore-$index")) { Text("Explore this analyzed continuation") }
            }
            if (result.original != null) ExpandableText(when (result.compareOriginal()) {
                MoveComparison.NEAR_EQUAL_AT_BUDGET -> "Scores are within the 20-cp comparison threshold at these search budgets; this is a heuristic, not proof of equal strength."
                MoveComparison.DIFFERENT_AT_BUDGET -> "Scores differ at these budgets. This alone does not classify the compared move as a mistake."
                MoveComparison.NOT_COMPARABLE -> "These scores cannot be compared as exact centipawn values (mate, bounds or unavailable score)."
            }, color = Gold, modifier = Modifier.testTag("engine-comparison"))
            state.previewIndex?.let { index ->
                val positions = state.previewPositions[index]
                Text("HYPOTHETICAL ENGINE LINE · ${state.previewPly} / ${positions.lastIndex} half-moves", color = Gold, modifier = Modifier.testTag("engine-preview"))
                ChessBoard(positions[state.previewPly], perspective = side, inputEnabled = false)
                state.previewExplanations.getOrNull(index)?.getOrNull(state.previewPly - 1)?.let { idea ->
                    Text("${idea.san} · ${idea.title}", color = Leaf, modifier = Modifier.testTag("engine-preview-idea"))
                    ExpandableText(idea.explanation, color = Cream)
                    ExpandableText("${if (developerMode) "CONDITIONAL PLAN · " else ""}${idea.principle}", color = MutedCream)
                }
                PositionTeachingPanel(positions[state.previewPly], side, "engine-position-teaching", developerMode)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton({ onJump(0) }, Modifier.testTag("engine-first")) { Text("First") }
                    TextButton({ onJump(state.previewPly - 1) }, Modifier.testTag("engine-previous"), enabled = state.previewPly > 0) { Text("Previous") }
                    TextButton({ onJump(state.previewPly + 1) }, Modifier.testTag("engine-next"), enabled = state.previewPly < positions.lastIndex) { Text("Next") }
                    TextButton({ onJump(positions.lastIndex) }, Modifier.testTag("engine-last")) { Text("Last") }
                }
                TextButton(onReturn, Modifier.testTag("engine-return")) { Text(returnLabel) }
            }
            if (developerMode) Text("GPLv3 engine · separate standard-UCI process. Exact binary SHA-256: ${result.engine.binarySha256}\nNNUE: ${result.engine.networks.entries.joinToString { "${it.key}: ${it.value}" }}\nSource, build recipe, license and authors are included in APK engine assets; no network call or paid provider.", color = MutedCream,
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("engine-provenance"))
        }
    }
}

private fun scoreText(score: EngineScore): String {
    val value = when (score) {
        is EngineScore.Centipawns -> String.format(Locale.ROOT, "%+.2f", score.value / 100.0)
        is EngineScore.Mate -> if (score.moves > 0) "engine reports mate in ${score.moves}" else "engine reports being mated in ${-score.moves}"
    }
    return when (score.bound) { ScoreBound.EXACT -> value; ScoreBound.LOWER -> "≥ $value"; ScoreBound.UPPER -> "≤ $value" }
}
