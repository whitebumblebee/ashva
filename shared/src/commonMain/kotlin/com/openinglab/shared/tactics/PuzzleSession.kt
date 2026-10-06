// SPDX-License-Identifier: Apache-2.0
package com.openinglab.shared.tactics

import com.openinglab.shared.chess.BoardPosition
import com.openinglab.shared.chess.PositionStatus
import com.openinglab.shared.model.ChessMove
import com.openinglab.shared.model.PieceColor

sealed interface PuzzleResult {
    data class Correct(val afterSolver: BoardPosition, val reply: ChessMove, val position: BoardPosition) : PuzzleResult
    data class Solved(val position: BoardPosition) : PuzzleResult
    data class Wrong(val expected: ChessMove) : PuzzleResult
}

/** A wrong move never changes the position. UI animation uses afterSolver before displaying position. */
class PuzzleSession(val puzzle: Puzzle) {
    val setupPosition = BoardPosition.fromFen(puzzle.fen)
    val setupMove = ChessMove.fromUci(puzzle.moves.first())
    var position = setupPosition.apply(setupMove)
        private set
    val solverSide: PieceColor = position.sideToMove
    var nextIndex = 1
        private set
    val solved: Boolean get() = nextIndex >= puzzle.moves.size
    val expected: ChessMove? get() = puzzle.moves.getOrNull(nextIndex)?.let(ChessMove::fromUci)

    fun submit(move: ChessMove): PuzzleResult {
        check(!solved) { "Puzzle already solved" }
        val expected = expected!!
        val final = nextIndex == puzzle.moves.lastIndex
        val after = if (position.isLegal(move)) position.apply(move) else null
        if (after == null || (move != expected && !(final && after.status() == PositionStatus.CHECKMATE)))
            return PuzzleResult.Wrong(expected)
        nextIndex++
        if (solved) { position = after; return PuzzleResult.Solved(after) }
        val reply = ChessMove.fromUci(puzzle.moves[nextIndex++])
        position = after.apply(reply)
        return PuzzleResult.Correct(after, reply, position)
    }
}
