// SPDX-License-Identifier: Apache-2.0
package com.openinglab.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.openinglab.app.ui.theme.*
import com.openinglab.shared.chess.BoardPosition
import com.openinglab.shared.lesson.*
import com.openinglab.shared.model.PieceColor
import kotlinx.coroutines.*

private sealed interface TeachingLoad {
    data object Loading : TeachingLoad
    data class Ready(val report: PositionTeaching) : TeachingLoad
    data object Failed : TeachingLoad
}

@Composable
fun PositionTeachingPanel(board: BoardPosition, side: PieceColor, tag: String = "position-teaching") {
    var expanded by rememberSaveable(tag) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag(tag), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        OutlinedButton({ expanded = !expanded }, Modifier.testTag("$tag-toggle")) {
            Text(if (expanded) "Hide position ideas" else "Understand this position")
        }
        if (expanded) {
            val fen = board.toFen()
            val load by produceState<TeachingLoad>(TeachingLoad.Loading, fen, board.repetitionHistory, side) {
                value = TeachingLoad.Loading
                try {
                    val context = currentCoroutineContext()
                    value = TeachingLoad.Ready(withContext(Dispatchers.Default) { PositionTeacher.inspect(board, side) { context.ensureActive() } })
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { value = TeachingLoad.Failed }
            }
            val current = (load as? TeachingLoad.Ready)?.report?.takeIf { it.fen == fen && it.pointOfView == side }
            if (load == TeachingLoad.Failed) {
                Text("Position ideas are unavailable for this position. No explanation was invented; close and reopen to retry.", color = Gold, modifier = Modifier.testTag("$tag-error"))
            } else if (current == null) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Preparing position ideas…", color = MutedCream, modifier = Modifier.testTag("$tag-loading"))
            } else {
                Text("Position ideas · ${side.name} POV", color = Leaf, style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.testTag("$tag-ready"))
                Text(current.focus, color = Cream, modifier = Modifier.testTag("$tag-focus"))
                TeachingSide(current.own, "$tag-own", "Your position")
                TeachingSide(current.opponent, "$tag-opponent", "Opponent's counterplay")
                Text("Legal pawn-contact candidates · ${board.sideToMove.name} to move", color = Gold,
                    modifier = Modifier.testTag("$tag-breaks"))
                Text(if (current.pawnBreaks.isEmpty()) "No current legal central pawn-contact move fits this specific lens. This does not mean there is no useful plan or pawn break to prepare." else
                    current.pawnBreaks.joinToString("\n") { "${it.san} (${it.uci}) · pawn contact: ${it.contactSquares.joinToString()}." }, color = Cream)
                Text("These are legal one-move contacts with enemy pawns on c–f files, not recommendations or verified continuations. Compare the lesson branch or analyze alternatives before choosing. No turn is invented for the other color.", color = MutedCream,
                    style = MaterialTheme.typography.bodySmall)
                if (current.geometricMotifs.isNotEmpty()) {
                    Text("Tactical geometry", color = Gold, modifier = Modifier.testTag("$tag-motifs"))
                    current.geometricMotifs.forEach { Text("${it.topic}: ${it.text}", color = Cream) }
                }
                Text("${PositionTeaching.VERSION} · Ashva's rules-derived facts and original conditional plans. Not engine evaluation, historical annotation, GM intention, independent expert review or a promise of winning. Expand engine analysis separately to calculate legal continuations.",
                    color = MutedCream, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("$tag-provenance"))
            }
        }
    }
}

@Composable
private fun TeachingSide(report: SideTeaching, tag: String, title: String) {
    Column(Modifier.testTag(tag), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("$title · ${report.side.name}", color = Leaf, style = MaterialTheme.typography.titleSmall)
        report.points.forEach { point ->
            Text("${if (point.evidence == TeachingEvidence.BOARD_FACT) "BOARD FACT" else "CONDITIONAL PLAN"} · ${point.topic}", color = Gold,
                style = MaterialTheme.typography.labelMedium)
            Text(point.text, color = Cream, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
