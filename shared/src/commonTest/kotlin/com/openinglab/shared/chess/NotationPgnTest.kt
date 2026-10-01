package com.openinglab.shared.chess

import com.openinglab.shared.data.OpeningCatalog
import com.openinglab.shared.model.ChessMove
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NotationPgnTest {
    @Test fun allSeedMovesAreLegalAndTheirNotationIsChecked() {
        for (opening in OpeningCatalog.openings) for (variation in opening.variations) {
            var board = BoardPosition.starting()
            for (step in variation.steps) {
                val move = ChessMove.fromUci(step.uci)
                assertTrue(board.isLegal(move), variation.id + ": " + move.uci)
                assertEquals(move, board.parseSan(step.san), variation.id + ": " + step.san)
                assertEquals(move, board.parseSan(board.san(move)))
                board = board.apply(move)
            }
        }
    }

    @Test fun disambiguationUsesLegalPiecesOnly() {
        val board = BoardPosition.fromFen("4k3/8/8/8/8/8/8/1N2KN2 w - - 0 1")
        assertEquals("Nbd2", board.san(ChessMove.fromUci("b1d2")))
        assertEquals("Nfd2", board.san(ChessMove.fromUci("f1d2")))
        assertFailsWith<IllegalArgumentException> { board.parseSan("Nd2") }
        val pinned = BoardPosition.fromFen("k3r3/8/8/8/8/8/4N1N1/4K3 w - - 0 1")
        assertEquals("Nf4", pinned.san(ChessMove.fromUci("g2f4")))
    }

    @Test fun promotionsCheckMateAndCastlingNotation() {
        val promotion = BoardPosition.fromFen("7k/P7/8/8/8/8/8/7K w - - 0 1")
        assertEquals("a8=Q+", promotion.san(ChessMove.fromUci("a7a8q")))
        assertEquals(ChessMove.fromUci("a7a8n"), promotion.parseSan("a8=N"))
        val castle = BoardPosition.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        assertEquals(ChessMove.fromUci("e1g1"), castle.parseSan("0-0"))
        val game = Pgn.parse("1.f3 e5 2.g4 Qh4# 0-1")
        assertEquals(PositionStatus.CHECKMATE, game.positions().last().status())
        assertFailsWith<IllegalArgumentException> { BoardPosition.starting().parseSan("e4#") }
    }

    @Test fun nestedVariationsCommentsGlyphsAndOriginalLineRoundTrip() {
        val pgn = """
            [Event "A \"quoted\" event"]
            [Result "*"]
            {Opening idea}
            1.e4! {space} (1.d4 d5 (1...Nf6 {flexible}) 2.c4) e5
            2.Nf3 $1 Nc6 ; develop
            3.Bb5 a6 *
        """.trimIndent()
        val game = Pgn.parse(pgn)
        assertEquals(listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1b5", "a7a6"), game.line.plies.map { it.move.uci })
        assertEquals(listOf(1), game.line.plies.first().nags)
        assertEquals(listOf("space"), game.line.plies.first().comments)
        val branch = game.line.plies.first().variations.single()
        assertEquals("d2d4", branch.plies.first().move.uci)
        assertEquals("g8f6", branch.plies[1].variations.single().plies.single().move.uci)
        val exported = Pgn.parse(Pgn.export(game))
        assertEquals(game.line, exported.line)
        assertEquals(game.tags["Event"], exported.tags["Event"])
        assertEquals(game.positions().last().toFen(), exported.positions().last().toFen())
    }

    @Test fun fenSetupBlackMoveNumbersAndArchiveRoundTrip() {
        val pgn = """
            [SetUp "1"]
            [FEN "7k/8/8/8/8/8/8/KR6 b - - 12 20"]
            [Result "*"]
            20...Kh7 *
        """.trimIndent()
        val game = Pgn.parse(pgn)
        assertEquals("h8h7", game.line.plies.single().move.uci)
        assertEquals(game.line, Pgn.parse(Pgn.export(game)).line)
        assertEquals(2, Pgn.parseArchive("1. e4 e5 *\n\n1. d4 d5 *").size)
    }

    @Test fun malformedIllegalAndUnsupportedGamesFailExplicitly() {
        val invalid = listOf(
            "1. e5 *", "1. e4", "1. e4 (1. d4 *", "1. e4 ) *", "1. e4 {oops",
            "[Variant \"Chess960\"] 1. e4 *", "[Result \"1-0\"] 1. e4 0-1",
            "[SetUp \"1\"] 1. e4 *", "1. e4 $999 *",
        )
        for (pgn in invalid) assertFailsWith<IllegalArgumentException>(pgn) { Pgn.parse(pgn) }
    }

    @Test fun commentsBetweenTagsAndAfterTerminationArePreserved() {
        val game = Pgn.parse("{before tags} [Event \"Demo\"] {between tags} [Result \"*\"] 1. e4 * {after result}")
        assertEquals(listOf("before tags", "between tags"), game.line.leadingComments)
        assertEquals(listOf("after result"), game.trailingComments)
        val roundTrip = Pgn.parse(Pgn.export(game))
        assertEquals(game.line, roundTrip.line)
        assertEquals(game.trailingComments, roundTrip.trailingComments)
    }

    @Test fun nestingLimitIsEnforcedBeforeStackOverflow() {
        val nested = "1. e4 " + "(1. d4 ".repeat(65) + ")".repeat(65) + " *"
        assertFailsWith<IllegalArgumentException> { Pgn.parse(nested) }
    }
}
