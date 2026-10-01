package com.openinglab.shared.chess

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Reference positions also used by Stockfish:
 * https://github.com/official-stockfish/Stockfish/blob/master/tests/perft.sh
 * Lower-depth counts: python-chess examples/perft/tricky.perft.
 * These test legal move enumeration, not engine strength.
 */
class PerftTest {
    private fun perft(board: BoardPosition, depth: Int): Long {
        if (depth == 0) return 1
        val moves = board.legalMoves()
        if (depth == 1) return moves.size.toLong()
        return moves.sumOf { perft(board.apply(it), depth - 1) }
    }

    @Test fun startingPositionThroughDepthFour() {
        val board = BoardPosition.starting()
        for ((depth, count) in listOf(1 to 20L, 2 to 400L, 3 to 8902L, 4 to 197281L))
            assertEquals(count, perft(board, depth), "start, depth $depth")
    }

    @Test fun kiwipeteCastlingPinsAndCaptures() {
        val board = BoardPosition.fromFen("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1")
        for ((depth, count) in listOf(1 to 48L, 2 to 2039L, 3 to 97862L))
            assertEquals(count, perft(board, depth), "Kiwipete, depth $depth")
    }

    @Test fun rookPawnEndgameEnPassant() {
        val board = BoardPosition.fromFen("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1")
        assertEquals(43238L, perft(board, 4))
    }

    @Test fun promotionAndCheckEvasions() {
        val mirrored = BoardPosition.fromFen("r2q1rk1/pP1p2pp/Q4n2/bbp1p3/Np6/1B3NBn/pPPP1PPP/R3K2R b KQ - 0 1")
        assertEquals(9467L, perft(mirrored, 3))
        val promotion = BoardPosition.fromFen("rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 0 1")
        assertEquals(62379L, perft(promotion, 3))
    }

    @Test fun complexMiddlegame() {
        val board = BoardPosition.fromFen("r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 1")
        assertEquals(89890L, perft(board, 3))
    }

    @Test fun enPassantDiscoveredCheckAlignment() {
        assertEquals(711L, perft(BoardPosition.fromFen("8/8/8/1k6/3Pp3/8/8/4KQ2 b - d3 0 1"), 3))
        assertEquals(555L, perft(BoardPosition.fromFen("1b1k4/8/8/1rPpK3/8/8/8/8 w - d6 0 1"), 3))
    }
}
