// SPDX-License-Identifier: Apache-2.0
package com.openinglab.shared

import com.openinglab.shared.chess.*
import com.openinglab.shared.content.*
import com.openinglab.shared.games.*
import com.openinglab.shared.model.PieceColor
import com.openinglab.shared.storage.InstalledPack
import kotlin.test.*

class GameLibraryTest {
    private val pgn = "[White \"Synthetic Alpha\"]\n[Black \"Synthetic Beta\"]\n[Event \"Synthetic Cup\"]\n[Date \"2020.01.03\"]\n[WhiteTitle \"GM\"]\n\n1. e4 e5 2. Nf3 Nc6 1-0"
    private fun score(id: String = "one", metadata: Map<String, String> = emptyMap()): GameRecord {
        val parsed = Pgn.parse(pgn)
        return GameRecord(id, emptyList(), PlayerReference("fide-123", "Synthetic Alpha", "123", "FIDE_ID_REPORTED_BY_SOURCE"),
            PlayerReference("source-player-beta", "Synthetic Beta", identityStatus = "SOURCE_SCOPED_UNVERIFIED"),
            parsed.tags + metadata, parsed.result, parsed.initialPosition.toFen(), parsed.line.plies.map { it.move.uci },
            parsed.line.plies.map { it.san }, parsed.positions().last().toFen(), Pgn.export(parsed), listOf("opening-fixture"), 3, emptyList())
    }
    private fun pack(vararg games: GameRecord, id: String = "fixture-v1"): LibraryPack {
        val source = ContentSource("synthetic", SourceKind.BROADCAST_GAMES, "Synthetic sample", "https://example.org",
            "fixture", "CC-BY-SA-4.0", "https://example.org/license", "https://example.org/license", "Synthetic fixture",
            "2026-10-02", true, "None", "Synthetic only", emptyList())
        val manifest = ContentManifest(packId = id, source = source, retrievedAt = "2026-10-02", inputs = emptyList(),
            coverage = CoverageCounts(games.size, games.size, 0, 0), files = emptyList(), limitations = listOf("Synthetic only"), snapshotLockSha256 = "b".repeat(64))
        return LibraryPack(InstalledPack(manifest, "a".repeat(64), "Synthetic fixture"), games.toList(), mapOf("opening-fixture" to "Ruy Lopez"))
    }

    @Test fun searchFiltersExactPlayerEventYearOpeningColorResultAndReportedTitle() {
        val game = score()
        val library = GameLibrary.build(listOf(pack(game)))
        assertEquals(listOf("broadcast:one"), library.search(GameLibraryFilter(query = "alpha cup ruy", playerId = "fide-123", event = "cup", year = 2020,
            opening = "Ruy", playerColor = PieceColor.WHITE, result = "1-0", reportedGmOnly = true)).map { it.id })
        assertTrue(library.search(GameLibraryFilter(playerId = "fide-123", playerColor = PieceColor.BLACK)).isEmpty())
        assertTrue(library.search(GameLibraryFilter(playerColor = PieceColor.WHITE)).isEmpty()) // No arbitrary player color.
        assertTrue(library.search(GameLibraryFilter(followedOnly = true)).isEmpty())
        assertEquals(1, library.search(GameLibraryFilter(followedOnly = true), setOf("fide-123")).size)
        assertTrue(library.search(GameLibraryFilter(year = 2021)).isEmpty())
        assertEquals(setOf("GM"), library.players.first { it.reference.id == "fide-123" }.reportedTitles)
        assertEquals("FIDE_ID_REPORTED_BY_SOURCE", library.players.first { it.reference.id == "fide-123" }.reference.identityStatus)
        val utc = game.copy(tags = game.tags + mapOf("Date" to "????.??.??", "UTCDate" to "2019.01.01"))
        assertEquals(2019, GameLibrary.build(listOf(pack(utc))).scores.single().year)
    }

    @Test fun exactDuplicateRecordsRetainProvenanceAndConflictsFailClosed() {
        val original = score()
        val library = GameLibrary.build(listOf(pack(original), pack(original, id = "fixture-v2")))
        assertEquals(1, library.scores.size)
        assertEquals(2, (library.scores.single() as LibraryScore.Broadcast).origins.size)
        assertFailsWith<IllegalArgumentException> { GameLibrary.build(listOf(pack(original), pack(original.copy(result = "0-1"), id = "fixture-v2"))) }
        assertFailsWith<IllegalArgumentException> { GameLibrary.build(listOf(pack(original).copy(openingNames = emptyMap()))) }
        assertFailsWith<IllegalArgumentException> { GameLibrary.build(listOf(pack(original, original))) }
        assertEquals(2, GameLibrary.build(listOf(pack(original, original.copy(id = "different-record")))).scores.size) // Not guessed real-game duplicates.
    }

    @Test fun aliasesUseExactIdentityNotSimilarNamesAndPrivateImportsStaySeparate() {
        val first = score()
        val alias = first.copy(id = "two", white = first.white.copy(name = "ALPHA, Synthetic"))
        val sameNameOtherSource = first.copy(id = "three", white = PlayerReference("source-player-other", first.white.name, identityStatus = "SOURCE_SCOPED_UNVERIFIED"))
        val private = PrivateGameRecord.import(pgn)
        val library = GameLibrary.build(listOf(pack(first, alias, sameNameOtherSource)), listOf(private))
        assertEquals(listOf("ALPHA, Synthetic", "Synthetic Alpha"), library.players.first { it.reference.id == "fide-123" }.reference.names)
        assertTrue(library.players.any { it.reference.id == "source-player-other" })
        assertTrue(private.white.id.startsWith("private-player:"))
        assertEquals("USER_SUPPLIED_UNVERIFIED", private.white.identityStatus)
        assertEquals(4, library.scores.size)
        assertEquals(1, library.packs.size)
        assertEquals(3, library.search(GameLibraryFilter(playerId = "fide-123")).size + library.search(GameLibraryFilter(playerId = "source-player-other")).size)
    }

    @Test fun privatePgnChecksOriginalMovesCommentsVariationsAndRejectsUnsafeImports() {
        val imported = PrivateGameRecord.import("[White \"Synthetic\"]\n[Black \"Fixture\"]\n\n1. e4 {user annotation, unverified} (1. d4 d5) e5 *")
        val parsed = imported.checkedGame()
        assertEquals(listOf("e2e4", "e7e5"), imported.uci)
        assertEquals("*", imported.result)
        assertEquals("user annotation, unverified", parsed.line.plies.first().comments.single())
        assertEquals(1, parsed.line.plies.first().variations.size)
        assertFailsWith<IllegalArgumentException> { imported.copy(canonicalPgn = imported.canonicalPgn + " ").checkedGame() }
        assertFailsWith<IllegalArgumentException> { imported.copy(uci = listOf("e2e3", "e7e5")).checkedGame() }
        assertFailsWith<IllegalArgumentException> { PrivateGameRecord.import("1. e5 *") }
        assertFailsWith<IllegalArgumentException> { PrivateGameRecord.import("1. e4 *\n\n1. d4 *") }
        assertFailsWith<IllegalArgumentException> { PrivateGameRecord.import(" ".repeat(PrivateGameRecord.MAX_BYTES + 1)) }
    }

    @Test fun unavailableFollowersRemainValidAndCancellationPropagates() {
        val retained = FollowedPlayer("source-player-unavailable", listOf("Synthetic absent player"), "SOURCE_SCOPED_UNVERIFIED")
        retained.validate()
        assertTrue(GameLibrary.build(emptyList()).search(GameLibraryFilter(followedOnly = true), setOf(retained.id)).isEmpty())
        assertFailsWith<IllegalArgumentException> { retained.copy(fideId = "123").validate() }
        assertFailsWith<UnsupportedOperationException> { GameLibrary.build(listOf(pack(score()))) { throw UnsupportedOperationException("cancel") } }
    }
}
