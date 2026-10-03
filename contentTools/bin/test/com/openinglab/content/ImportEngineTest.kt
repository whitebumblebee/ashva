package com.openinglab.content

import com.openinglab.shared.content.*
import com.openinglab.shared.model.PieceColor
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class ImportEngineTest {
    private fun source(kind: SourceKind = SourceKind.OPENING_TAXONOMY) = ContentSource(
        "test-source", kind, "Fixture", "https://example.org/source", "fixture-v1",
        if (kind == SourceKind.OPENING_TAXONOMY) "CC0-1.0" else "CC-BY-SA-4.0",
        "https://example.org/license", "https://example.org/evidence", "Fixture author", "2026-10-01", true,
        "Canonical mainline only", "Test fixtures, not coverage", listOf(SourceFile(
            if (kind == SourceKind.OPENING_TAXONOMY) "test.tsv" else "test.pgn.zst", "https://example.org/input",
            if (kind == SourceKind.OPENING_TAXONOMY) InputFormat.OPENINGS_TSV else InputFormat.PGN_ZSTD)))
    private val header = "eco\tname\tpgn\n"
    private fun openings(rows: String) = ImportEngine.openings(source(), listOf(InputText("test.tsv", header + rows)))
    private fun score(moves: String = "1. e4 e5 2. Nf3 Nc6", result: String = "1-0", url: String = "https://example.org/game/1", extra: String = "") =
        "[Event \"Fixture\"]\n[Date \"2020.04.01\"]\n[Round \"1\"]\n[White \"Alice\"]\n[Black \"Bob\"]\n[Result \"$result\"]\n[GameURL \"$url\"]\n$extra\n$moves $result"
    private fun games(raw: String, taxonomy: List<OpeningRecord> = emptyList()) =
        ImportEngine.games(source(SourceKind.BROADCAST_GAMES), listOf(InputText("test.pgn", raw)), taxonomy)

    @Test fun everyTaxonomyRowHasADispositionAndDuplicateProvenanceIsKept() {
        val line = "C60\tRuy Lopez\t1. e4 e5 2. Nf3 Nc6 3. Bb5"
        val imported = openings("$line\n$line\nZ99\tWrong\t1. e4\nC20\tIllegal\t1. e5\nmissing columns\n")
        assertEquals(5, imported.coverage.inputRecords)
        assertEquals(1, imported.coverage.acceptedRecords)
        assertEquals(1, imported.coverage.duplicates)
        assertEquals(3, imported.coverage.quarantined)
        assertEquals(listOf(2, 3), imported.openings.single().occurrences.map { it.ordinal })
        assertEquals(imported.openings.single().id, imported.issues.single { it.kind == IssueKind.DUPLICATE }.retainedId)
        assertTrue(imported.issues.all { it.location.rawSha256.length == 64 })
    }

    @Test fun taxonomyTranspositionsSharePositionsButKeepBothRoutesAndLabels() {
        val imported = openings("D02\tQueen Pawn: first\t1. d4 d5 2. Nf3 Nf6\nD02\tQueen Pawn: second\t1. Nf3 d5 2. d4 Nf6\n")
        assertEquals(2, imported.openings.size)
        assertEquals(1, imported.coverage.families)
        assertEquals(1, imported.coverage.distinctPositions)
        assertEquals(1, imported.coverage.transposedPositions)
        assertEquals(2, imported.coverage.distinctNames)
        assertEquals(2, imported.openings.map { it.uci }.distinct().size)
    }

    @Test fun malformedFileHeadersAreFatalRatherThanSilentlySkippingTheFile() {
        assertFailsWith<IllegalArgumentException> { ImportEngine.openings(source(), listOf(InputText("wrong.tsv", "name\teco\tpgn"))) }
    }

    @Test fun rightsAndFileShapesMustBeExplicitlyReviewed() {
        for (bad in listOf(source().copy(redistributionApproved = false), source().copy(license = "AGPL-3.0"),
            source().copy(id = "../escape"), source().copy(revision = "../../escape"), source().copy(licenseEvidenceUrl = ""),
            source().copy(files = listOf(SourceFile("../unsafe", "https://example.org/input", InputFormat.OPENINGS_TSV))),
            source().copy(kind = SourceKind.BROADCAST_GAMES, license = "CC-BY-SA-4.0"))) {
            assertFailsWith<IllegalArgumentException> { ImportEngine.checkSource(bad) }
        }
    }

    @Test fun aBadGameDoesNotHideGoodSubsequentTaggedRecords() {
        val imported = games(score() + "\n" + score("1. e5", url = "https://example.org/game/2") + "\n" +
            score("1. d4 d5", url = "https://example.org/game/3"))
        assertEquals(3, imported.coverage.inputRecords)
        assertEquals(2, imported.games.size)
        assertEquals(1, imported.coverage.quarantined)
        assertEquals(IssueKind.INVALID, imported.issues.single().kind)
        assertEquals(2, imported.issues.single().location.ordinal)
    }

    @Test fun incompleteAndUnsupportedGamesAreReportedNotPublished() {
        val imported = games(score(result = "*") + "\n" + score(extra = "[Variant \"Chess960\"]"))
        assertEquals(0, imported.games.size)
        assertEquals(2, imported.coverage.quarantined)
        assertEquals(setOf(IssueKind.INCOMPLETE, IssueKind.UNSUPPORTED), imported.issues.map { it.kind }.toSet())
    }

    @Test fun repeatedGamesMergeOccurrencesAndExternalIds() {
        val imported = games(score() + "\n" + score(url = "https://example.org/game/2"))
        val retained = imported.games.single()
        assertEquals(1, imported.coverage.duplicates)
        assertEquals(0, imported.coverage.quarantined)
        assertEquals(2, retained.externalIds.size)
        assertEquals(2, retained.occurrences.size)
        assertEquals(retained.id, imported.issues.single().retainedId)
    }

    @Test fun conflictingExternalIdentityQuarantinesAllVersions() {
        val imported = games(score() + "\n" + score("1. d4 d5"))
        assertTrue(imported.games.isEmpty())
        assertEquals(2, imported.coverage.quarantined)
        assertTrue(imported.issues.all { it.kind == IssueKind.CONFLICT })
    }

    @Test fun namesDoNotBecomeVerifiedGmAliasesAndFideIdsAreOnlySourceReports() {
        val imported = games(score(extra = "[WhiteFideId \"1234\"]\n[BlackFideId \"not-an-id\"]"))
        val game = imported.games.single()
        assertEquals("fide-1234", game.white.id)
        assertEquals("FIDE_ID_REPORTED_BY_SOURCE", game.white.identityStatus)
        assertNull(game.black.fideId)
        assertEquals("SOURCE_SCOPED_UNVERIFIED", game.black.identityStatus)
        assertTrue(game.black.id.startsWith("source-player-"))
        assertEquals(1, imported.coverage.unresolvedPlayers)
    }

    @Test fun fullGameMatchesLastKnownOpeningWithoutInventingTeaching() {
        val taxonomy = openings("C20\tKing Pawn Game\t1. e4 e5\n").openings
        val imported = games(score("1. e4 {[%eval 0.5]} e5 2. Nf3 (2. Bc4 Nc6) Nc6"), taxonomy)
        val game = imported.games.single()
        assertEquals(4, imported.coverage.gamePlies)
        assertEquals(1, imported.coverage.gamesMatchedToTaxonomy)
        assertEquals(2, game.openingMatchedAtPly)
        assertEquals(taxonomy.map { it.id }, game.openingIds)
        assertFalse(game.canonicalPgn.contains("[%eval"))
        assertFalse(game.canonicalPgn.contains("Bc4"))
        assertEquals("NOT_INCLUDED", game.annotationStatus)
        assertEquals(4, game.toLessonGraph().start(PieceColor.BLACK).last().ply)
        val roundTrip = contentJson.decodeFromString<GameRecord>(contentJson.encodeToString(GameRecord.serializer(), game))
        assertEquals(game, roundTrip)
    }

    @Test fun importedIdentitiesAndOrderAreDeterministic() {
        val first = score()
        val second = score("1. d4 d5", url = "https://example.org/game/2")
        val forward = games("$first\n$second").games
        val reverse = games("$second\n$first").games
        assertEquals(forward.map { it.id }, reverse.map { it.id })
        assertEquals(forward.map { it.uci }, reverse.map { it.uci })
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", digest("abc"))
    }

    @Test fun pinnedLocalSliceMeetsMeasuredTargetsAndEveryGameReplaysBothColors() {
        val root = Path.of("content")
        val taxonomyDir = root.resolve("packs/lichess-openings-c67912be581f-import-v1")
        val gameDir = root.resolve("packs/lichess-broadcast-2020-04-2020-04-snap-import-v1")
        val taxonomyManifest = contentJson.decodeFromString<ContentManifest>(Files.readString(taxonomyDir.resolve("manifest.json")))
        val gameManifest = contentJson.decodeFromString<ContentManifest>(Files.readString(gameDir.resolve("manifest.json")))
        assertEquals(3815, taxonomyManifest.coverage.acceptedRecords)
        assertEquals(149, taxonomyManifest.coverage.families)
        assertEquals(79, gameManifest.coverage.acceptedRecords)
        assertEquals(7606, gameManifest.coverage.gamePlies)
        assertEquals(listOf(PackDependency(taxonomyManifest.packId, digest(Files.readAllBytes(taxonomyDir.resolve("manifest.json"))))), gameManifest.dependencies)
        assertEquals(digest(Files.readAllBytes(root.resolve("snapshots.lock.json"))), gameManifest.snapshotLockSha256)
        for ((directory, manifest) in listOf(taxonomyDir to taxonomyManifest, gameDir to gameManifest)) {
            assertEquals(0, manifest.coverage.quarantined)
            assertEquals(manifest.coverage.inputRecords, manifest.coverage.acceptedRecords + manifest.coverage.duplicates + manifest.coverage.quarantined)
            for (file in manifest.files) verifyFile(directory.resolve(file.name), file.bytes, file.sha256, 4 * 1024 * 1024)
        }
        val games = Files.readAllLines(gameDir.resolve("games.jsonl")).map { contentJson.decodeFromString<GameRecord>(it) }
        assertEquals(79, games.size)
        for (game in games) for (side in PieceColor.entries) {
            val replay = game.toLessonGraph().start(side).last()
            assertEquals(game.uci, replay.moves.map { it.move.uci })
            assertEquals(game.finalFen, replay.position.toFen())
            assertEquals(game.result, replay.path.result)
            assertTrue(replay.branches().isEmpty())
        }
    }
}
