// SPDX-License-Identifier: Apache-2.0
package com.openinglab.shared.content

import com.openinglab.shared.chess.BoardPosition
import com.openinglab.shared.chess.Pgn
import com.openinglab.shared.data.OpeningCatalog
import com.openinglab.shared.lesson.LessonGraph
import com.openinglab.shared.model.PieceColor
import com.openinglab.shared.repertoire.RepertoireBook
import com.openinglab.shared.storage.InstalledPack
import kotlin.test.*

class ObservedReplyIndexTest {
    private fun game(id: String, moves: String): GameRecord {
        val parsed = Pgn.parse(moves)
        val player = PlayerReference("fixture-player", "Synthetic player", identityStatus = "UNRESOLVED")
        return GameRecord(id, emptyList(), player, player, parsed.tags, parsed.result, parsed.initialPosition.toFen(),
            parsed.line.plies.map { it.move.uci }, parsed.line.plies.map { it.san }, parsed.positions().last().toFen(),
            moves, emptyList(), null, emptyList())
    }
    private fun sample(vararg games: GameRecord, id: String = "fixture-v1"): ObservedGameSample {
        val source = ContentSource("fixture", SourceKind.BROADCAST_GAMES, "Synthetic sample", "https://example.org",
            "fixture", "CC-BY-SA-4.0", "https://example.org/license", "https://example.org/license", "Synthetic fixture",
            "2026-10-02", true, "None", "Synthetic only", emptyList())
        val manifest = ContentManifest(packId = id, source = source, retrievedAt = "2026-10-02", inputs = emptyList(),
            coverage = CoverageCounts(games.size, games.size, 0, 0, gamePlies = games.sumOf { it.uci.size }),
            files = emptyList(), limitations = listOf("Synthetic only"), snapshotLockSha256 = "b".repeat(64))
        return ObservedGameSample(InstalledPack(manifest, "a".repeat(64), "Synthetic fixture"), games.toList())
    }
    private fun index(vararg games: GameRecord) = ObservedReplyIndex.build(listOf(sample(*games)))

    @Test fun transpositionsCombineDifferentMoveOrdersAndClocksDoNotSplitCounts() {
        val first = game("one", "1. d4 d5 2. Nf3 Nf6 3. c4 e6 1/2-1/2")
        val second = game("two", "1. Nf3 d5 2. d4 Nf6 3. Bf4 e6 1/2-1/2")
        val board = Pgn.parse(first.canonicalPgn).positions()[4]
        assertEquals(board.positionKey, Pgn.parse(second.canonicalPgn).positions()[4].positionKey)
        val replies = index(first, second).at(board.positionKey)
        assertEquals(2, replies.scoresSeen); assertEquals(2, replies.scoresWithReply)
        assertEquals(setOf("c2c4", "c1f4"), replies.replies.map { it.uci }.toSet())
        val differentClocks = BoardPosition.fromFen(board.toFen().split(' ').take(4).joinToString(" ") + " 19 40")
        assertEquals(replies, index(first, second).at(differentClocks.positionKey))
    }

    @Test fun repetitionUsesFirstVisitRatherThanInflatingOrChangingTheRecordedReply() {
        val repeated = game("one", "1. Nf3 Nf6 2. Ng1 Ng8 3. d4 d5 1/2-1/2")
        val other = game("two", "1. d4 d5 1/2-1/2")
        val replies = index(repeated, other).at(BoardPosition.starting().positionKey)
        assertEquals(2, replies.scoresSeen); assertEquals(2, replies.scoresWithReply)
        assertEquals(1, replies.count("g1f3")); assertEquals(1, replies.count("d2d4"))
        assertEquals(2, replies.replies.sumOf { it.scores })
    }

    @Test fun scoresEndingHereDoNotEnterTheReplyDenominator() {
        val shorter = game("short", "1. e4 e5 1/2-1/2")
        val longer = game("long", "1. e4 e5 2. Nf3 Nc6 1/2-1/2")
        val key = Pgn.parse(shorter.canonicalPgn).positions().last().positionKey
        val replies = index(shorter, longer).at(key)
        assertEquals(2, replies.scoresSeen); assertEquals(1, replies.scoresWithReply)
        assertEquals(1, replies.scoresWithoutReply); assertEquals(1, replies.count("g1f3"))
        val endpoint = index(shorter).at(key)
        assertEquals(1, endpoint.scoresSeen); assertEquals(0, endpoint.scoresWithReply)
        assertTrue(endpoint.replies.isEmpty())
    }

    @Test fun sameScoreIdAcrossPacksCountsOnceWithBothProvenancesButConflictsAreRejected() {
        val original = game("stable-id", "1. e4 e5 1/2-1/2")
        val first = sample(original)
        val second = sample(original.copy(tags = mapOf("Event" to "Metadata update")), id = "fixture-v2")
        val index = ObservedReplyIndex.build(listOf(first, second))
        assertEquals(1, index.totalScores); assertEquals(listOf(first.source, second.source), index.sources)
        assertEquals(1, index.at(BoardPosition.starting().positionKey).count("e2e4"))
        val conflict = sample(game(original.id, "1. d4 d5 1/2-1/2"), id = "fixture-v2")
        assertFailsWith<IllegalArgumentException> { ObservedReplyIndex.build(listOf(first, conflict)) }
        assertEquals(2, index(original, original.copy(id = "another-id")).totalScores)
    }

    @Test fun unknownPositionHasZeroObservationsNotAnInventedMoveOrGameResult() {
        val original = game("one", "1. e4 e5 1/2-1/2")
        val observations = index(original).at(Pgn.parse("1. a4 *").positions().last().positionKey)
        assertEquals(0, observations.scoresSeen); assertEquals(0, observations.scoresWithReply)
        assertEquals(0, observations.count("a7a5")); assertTrue(observations.replies.isEmpty())
        assertFailsWith<IllegalArgumentException> { ObservedReplyIndex.build(emptyList()) }
    }

    @Test fun sampleCountsPartitionSelectedUnselectedAndOutsideMovesForEitherColor() {
        val observations = index(game("one", "1. e4 e5 1/2-1/2"), game("two", "1. e4 c5 1/2-1/2"), game("three", "1. d4 d5 1/2-1/2"))
        val book = RepertoireBook(LessonGraph.fromOpening(OpeningCatalog.byId("ruy-lopez")))
        val board = book.graph.initialPosition
        for (side in PieceColor.entries) {
            val policy = book.seed(side, "ruy-main")
            assertEquals(ObservedChoiceCounts(2, 0, 1), observations.choices(policy, board.positionKey, setOf("e2e4")))
            val blackTurn = book.graph.paths.getValue("ruy-main").positions[1]
            assertEquals(ObservedChoiceCounts(1, 0, 1), observations.choices(policy, blackTurn.positionKey, setOf("e7e5")))
        }
        val policy = book.seed(PieceColor.BLACK, "ruy-main")
        assertEquals(ObservedChoiceCounts(2, 1, 0), observations.choices(policy, board.positionKey, setOf("e2e4", "d2d4")))
    }

    @Test fun inconsistentOrIllegalScoresAndUntrustedSamplesFailClosed() {
        val original = game("one", "1. e4 e5 1/2-1/2")
        for (broken in listOf(original.copy(uci = listOf("e2e5", "e7e5")), original.copy(san = listOf("d4", "e5")),
            original.copy(finalFen = BoardPosition.starting().toFen()), original.copy(result = "*"), original.copy(san = emptyList()))) {
            assertFailsWith<IllegalArgumentException> { index(broken) }
        }
        val sample = sample(original)
        assertFailsWith<IllegalArgumentException> { ObservedReplyIndex.build(listOf(sample.copy(source = sample.source.copy(manifestSha256 = "bad")))) }
        assertFailsWith<IllegalArgumentException> { ObservedReplyIndex.build(listOf(sample.copy(source = sample.source.copy(manifest = sample.source.manifest.copy(coverage = CoverageCounts(0, 0, 0, 0)))))) }
        assertFailsWith<IllegalArgumentException> { ObservedReplyIndex.build(listOf(sample.copy(source = sample.source.copy(manifest = sample.source.manifest.copy(source = sample.source.manifest.source.copy(redistributionApproved = false)))))) }
        assertFailsWith<IllegalArgumentException> { index(original, original) }
    }

    @Test fun workIsBoundedAndSupportsCancellationCheckpoints() {
        val original = game("one", "1. e4 e5 1/2-1/2")
        val sample = sample(original)
        assertFailsWith<IllegalArgumentException> { ObservedReplyIndex.build(listOf(sample.copy(games = List(ObservedReplyIndex.MAX_SCORES + 1) { original }))) }
        val oversized = original.copy(uci = List(SourceValidation.MAX_GAME_PLIES + 1) { "e2e4" }, san = List(SourceValidation.MAX_GAME_PLIES + 1) { "e4" })
        assertFailsWith<IllegalArgumentException> { index(oversized) }
        var checkpoints = 0
        assertFailsWith<IllegalStateException> { ObservedReplyIndex.build(listOf(sample)) { if (++checkpoints == 3) error("Synthetic cancellation") } }
        assertEquals(3, checkpoints)
    }
}
