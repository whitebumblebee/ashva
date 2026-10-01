package com.openinglab.shared.chess

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNull

class OpeningIdentifierTest {
    private val identifier = OpeningIdentifier()

    @Test
    fun identifiesRuyLopezAndVariation() {
        val match = identifier.identify(listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1b5", "a7a6"))

        assertEquals("ruy-lopez", match.opening?.id)
        assertEquals("Morphy Defence", match.variationName)
        assertEquals(OpeningMatchKind.KNOWN, match.kind)
    }

    @Test
    fun narrowsSicilianAfterBlackSecondMove() {
        val match = identifier.identify(listOf("e2e4", "c7c5"))

        assertEquals("sicilian", match.opening?.id)
        assertEquals(listOf("sicilian"), match.candidates.map { it.id })
        assertNull(match.variationName, "A Sicilian is not yet a Najdorf")
    }

    @Test
    fun transposedLondonIsRecognizedByPosition() {
        val match = identifier.identify(listOf("d2d4", "g8f6", "c1f4", "d7d5", "g1f3"))
        assertEquals("london", match.opening?.id)
        assertEquals(OpeningMatchKind.KNOWN, match.kind)
    }

    @Test
    fun ambiguousEarlyPositionsDoNotPickFirstOpening() {
        val match = identifier.identify(listOf("e2e4"))
        assertNull(match.opening)
        assertEquals(OpeningMatchKind.AMBIGUOUS, match.kind)
        assertTrue(match.candidates.size > 1)
    }

    @Test
    fun noFalseLondonBeforeBishopDevelops() {
        val match = identifier.identify(listOf("d2d4", "g8f6", "g1f3", "c7c5", "e2e3", "e7e6"))
        assertNull(match.opening)
        assertEquals(OpeningMatchKind.UNKNOWN, match.kind)
    }

    @Test
    fun retainsLastKnownOpeningButLabelsOffBook() {
        val match = identifier.identify(listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1b5", "g7g6"))
        assertEquals("ruy-lopez", match.opening?.id)
        assertEquals(OpeningMatchKind.OUT_OF_BOOK, match.kind)
        assertEquals(5, match.matchedPly)
        assertNull(match.variationName)
    }

    @Test
    fun unknownAndIllegalInputsAreDistinct() {
        assertEquals(OpeningMatchKind.UNKNOWN, identifier.identify(listOf("a2a3", "a7a6")).kind)
        assertEquals(OpeningMatchKind.INVALID, identifier.identify(listOf("e2e5")).kind)
        assertEquals(OpeningMatchKind.INVALID, identifier.identifyFen("not a FEN").kind)
    }

    @Test
    fun fenAndPgnRecognitionAgreeWithoutHeuristicConfidence() {
        val moves = listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1b5", "a7a6")
        val board = moves.fold(BoardPosition.starting()) { position, uci -> position.apply(com.openinglab.shared.model.ChessMove.fromUci(uci)) }
        assertEquals("Morphy Defence", identifier.identifyFen(board.toFen()).variationName)
        assertEquals(identifier.identify(moves), identifier.identifyPgn("1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 *"))
    }

    @Test
    fun genuineCatalogAmbiguityIsReturnedRatherThanHidden() {
        val opening = com.openinglab.shared.data.OpeningCatalog.byId("ruy-lopez")
        val ambiguous = OpeningIdentifier(listOf(opening, opening.copy(id = "other-label", name = "Alternative label")))
            .identify(listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1b5"))
        assertNull(ambiguous.opening)
        assertEquals(OpeningMatchKind.AMBIGUOUS, ambiguous.kind)
        assertEquals(2, ambiguous.candidates.size)
    }
}
