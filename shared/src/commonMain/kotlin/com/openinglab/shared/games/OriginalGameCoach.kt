// SPDX-License-Identifier: Apache-2.0
package com.openinglab.shared.games

import com.openinglab.shared.analysis.AnalysisPosition
import com.openinglab.shared.chess.sanAndPlay
import com.openinglab.shared.lesson.*
import com.openinglab.shared.model.MoveStep
import com.openinglab.shared.model.PieceColor

/** Original Ashva board observations; never source commentary or inferred historical intention. */
data class OriginalMoveTeaching(
    val ply: Int, val movingSide: PieceColor, val beforeFen: String, val afterFen: String,
    val idea: MoveStep, val version: String = OriginalGameCoach.VERSION,
)

object OriginalGameCoach {
    const val VERSION = "ashva-original-game-coach/1"

    /** One bounded move at a time; a long game's explanations need not all reside in memory. */
    fun explain(graph: LessonGraph, ply: Int, checkpoint: () -> Unit = {}): OriginalMoveTeaching {
        val path = graph.paths.getValue(graph.originalPathId)
        require(path.kind == LessonPathKind.ORIGINAL_GAME && ply in 1..path.moves.size)
        checkpoint()
        val move = path.moves[ply - 1]
        val before = path.positions[ply - 1]
        val checked = before.sanAndPlay(move.move)
        require(checked.san == move.san && checked.position == path.positions[ply]) { "Original move/position mismatch" }
        checkpoint()
        val (idea, after) = PositionCoach.explainAndPlay(before, move.move, move.san)
        require(after == checked.position)
        return OriginalMoveTeaching(ply, before.sideToMove, before.toFen(), after.toFen(), idea)
    }

    /** Keep the full declared history; never silently replace a long history with an isolated FEN. */
    fun analysisPosition(replay: LessonReplay): AnalysisPosition {
        require(replay.path.kind == LessonPathKind.ORIGINAL_GAME && replay.pathId == replay.graph.originalPathId && !replay.canReturn)
        require(replay.ply <= AnalysisPosition.MAX_HISTORY_PLIES) { "Original history exceeds the engine's supported bound" }
        return AnalysisPosition(replay.graph.initialPosition.toFen(), replay.moves.take(replay.ply).map { it.move.uci })
    }
}
