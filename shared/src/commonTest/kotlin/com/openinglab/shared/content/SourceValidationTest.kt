package com.openinglab.shared.content

import com.openinglab.shared.chess.BoardPosition
import com.openinglab.shared.chess.Pgn
import com.openinglab.shared.model.PieceColor
import kotlin.test.*

class SourceValidationTest {
    private fun score(moves: String, result: String = "1-0", extra: String = "") =
        "[Event \"Test\"]\n[White \"Alice\"]\n[Black \"Bob\"]\n[Result \"$result\"]\n$extra\n$moves $result"

    @Test fun openingLabelsAndCanonicalMovesAreValidated() {
        val game = SourceValidation.opening("C60", "Ruy Lopez", "1. e4 e5 2. Nf3 Nc6 3. Bb5")
        assertEquals(5, game.line.plies.size)
        assertEquals("f1b5", game.line.plies.last().move.uci)
        for ((eco, name, moves) in listOf(Triple("Z60", "Ruy", "1. e4"), Triple("C60", "", "1. e4"),
            Triple("C60", "Ruy", "1. e5"), Triple("C60", "Ruy", "1. e4 {comment}"),
            Triple("C60", "Ruy", "[Event \"Test\"] 1. e4"), Triple("C60", "Ruy", "1. e4 (1. d4)"))) {
            assertFailsWith<IllegalArgumentException> { SourceValidation.opening(eco, name, moves) }
        }
    }

    @Test fun gameKeepsOriginalMainlineButDoesNotElevateSourceAnnotations() {
        val raw = score("{intro} 1. e4 {[%eval 0.2]} (1. d4 d5) e5 2. Nf3 $1 Nc6 {clock}")
        val game = SourceValidation.game(raw)
        assertEquals(listOf("e2e4", "e7e5", "g1f3", "b8c6"), game.line.plies.map { it.move.uci })
        assertTrue(game.line.leadingComments.isEmpty())
        assertTrue(game.line.plies.all { it.comments.isEmpty() && it.nags.isEmpty() && it.variations.isEmpty() })
        assertEquals(game.positions(), Pgn.parse(Pgn.export(game)).positions())
        assertTrue(raw.contains("[%eval"))
    }

    @Test fun incompleteIllegalUnsupportedAndContradictoryScoresFailClosed() {
        val inputs = listOf(score("1. e4", "*"), score("1. e5"),
            score("1. e4", extra = "[Variant \"Chess960\"]"),
            score("1. f3 e5 2. g4 Qh4#", "1-0"), score("1. e4").replace("Alice", "?"))
        for (raw in inputs) assertFailsWith<IllegalArgumentException> { SourceValidation.game(raw) }
        assertEquals("0-1", SourceValidation.game(score("1. f3 e5 2. g4 Qh4#", "0-1")).result)
    }

    @Test fun archiveFramingRespectsCommentsEscapedTagStringsAndVariations() {
        val first = score("1. e4 {literal\n[Event \"not a header\"]\n} (1. d4 d5) e5 ;[fake]\n2. Nf3 Nc6",
            extra = "[Site \"escaped \\\"[tag]\\\"\"]")
        val second = score("1. d4 d5")
        val frames = PgnFrames.split("\uFEFF$first\n\n$second\n")
        assertEquals(2, frames.size)
        assertEquals(4, SourceValidation.game(frames[0]).line.plies.size)
        assertEquals(2, SourceValidation.game(frames[1]).line.plies.size)
    }

    @Test fun corruptLexicalTailIsNotGuessedIntoMoreGames() {
        for (broken in listOf("1. e4 {never closed", "1. e4 (1. d4")) {
            val raw = score("1. d4 d5") + "\n" + score(broken) + "\n" + score("1. c4 e5")
            val frames = PgnFrames.split(raw)
            assertEquals(2, frames.size)
            SourceValidation.game(frames[0])
            assertFailsWith<IllegalArgumentException> { SourceValidation.game(frames[1]) }
        }
    }

    @Test fun framingAndGameSizeLimitsAreExplicit() {
        assertEquals(emptyList(), PgnFrames.split(" \n"))
        assertFailsWith<IllegalArgumentException> { PgnFrames.split(" ".repeat(8 * 1024 * 1024 + 1)) }
        assertFailsWith<IllegalArgumentException> { SourceValidation.game(" ".repeat(SourceValidation.MAX_GAME_CHARS + 1)) }
    }

    private fun record(id: String, moves: String): OpeningRecord {
        val game = SourceValidation.opening("D02", "Queen Pawn: $id", moves)
        val end = game.positions().last()
        return OpeningRecord(id, "D02", "Queen Pawn: $id", "Queen Pawn", game.line.plies.map { it.move.uci },
            game.line.plies.map { it.san }, end.toFen(), end.positionKey, emptyList())
    }

    @Test fun positionIndexRecognizesDifferentMoveOrdersWithoutMergingTheirPaths() {
        val first = record("first", "1. d4 d5 2. Nf3 Nf6")
        val second = record("second", "1. Nf3 d5 2. d4 Nf6")
        assertEquals(first.positionKey, second.positionKey)
        assertNotEquals(first.uci, second.uci)
        val index = SourcedOpeningIndex(listOf(first, second))
        assertEquals(setOf("first", "second"), index.at(BoardPosition.fromFen(first.finalFen)).map { it.id }.toSet())
        val match = assertNotNull(index.identify(Pgn.parse("1. Nf3 d5 2. d4 Nf6 3. Bf4 *").positions()))
        assertEquals(4, match.matchedAtPly)
        assertFalse(match.atCurrentPosition)
        assertNull(index.identify(listOf(BoardPosition.starting())))
    }

    @Test fun importedScoreBuildsImmutableReplayForBothPerspectivesAndRejectsTampering() {
        val game = SourceValidation.game(score("1. e4 e5 2. Nf3 Nc6"))
        val player = PlayerReference("fixture", "Fixture", identityStatus = "SOURCE_SCOPED_UNVERIFIED")
        val record = GameRecord("fixture-game", emptyList(), player, player, game.tags, game.result,
            game.initialPosition.toFen(), game.line.plies.map { it.move.uci }, game.line.plies.map { it.san },
            game.positions().last().toFen(), Pgn.export(game), emptyList(), null, emptyList())
        val graph = record.toLessonGraph()
        for (side in PieceColor.entries) {
            val replay = graph.start(side).last()
            assertEquals(game.positions().last(), replay.position)
            assertEquals(record.uci, replay.moves.map { it.move.uci })
            assertEquals("1-0", replay.path.result)
            assertEquals(side, replay.playerSide)
            assertTrue(replay.branches().isEmpty())
        }
        assertFailsWith<IllegalArgumentException> { record.copy(uci = record.uci.reversed()).toLessonGraph() }
        assertFailsWith<IllegalArgumentException> { record.copy(result = "0-1").toLessonGraph() }
        assertFailsWith<IllegalArgumentException> { record.copy(finalFen = BoardPosition.START_FEN).toLessonGraph() }
    }
}
