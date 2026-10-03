// SPDX-License-Identifier: Apache-2.0
package com.openinglab.content

import com.openinglab.shared.chess.Pgn
import com.openinglab.shared.content.*
import com.openinglab.shared.storage.InstalledPack
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class WholeBroadcastSnapshotTest {
    private fun sample(id: String): ObservedGameSample {
        val path = Path.of("content/packs/$id")
        val bytes = Files.readAllBytes(path.resolve("manifest.json"))
        val manifest = contentJson.decodeFromString<ContentManifest>(bytes.decodeToString())
        val games = Files.readAllLines(path.resolve("games.jsonl")).filter { it.isNotBlank() }.map { contentJson.decodeFromString<GameRecord>(it) }
        return ObservedGameSample(InstalledPack(manifest, digest(bytes), Files.readString(path.resolve("ATTRIBUTION.txt"))), games)
    }

    @Test fun everyWholeArchiveFrameHasExactlyOneDispositionWithOriginalProvenance() {
        val sample = sample("lichess-broadcast-2020-01-2020-01-snap-import-v1")
        val path = Path.of("content/packs/${sample.source.manifest.packId}")
        val issues = Files.readAllLines(path.resolve("issues.jsonl")).map { contentJson.decodeFromString<ImportIssue>(it) }
        val raw = PgnFrames.split(Files.readString(Path.of("content/raw/lichess-broadcast-2020-01/broadcast.pgn")))
        assertEquals(952, raw.size); assertEquals(857, sample.games.size); assertEquals(95, issues.size)
        assertEquals(55, issues.count { it.kind == IssueKind.INCOMPLETE })
        assertEquals(40, issues.count { it.kind == IssueKind.INVALID })
        val locations = sample.games.flatMap { it.occurrences } + issues.map { it.location }
        assertEquals((1..952).toSet(), locations.map { it.ordinal }.toSet()); assertEquals(952, locations.size)
        for (location in locations) assertEquals(digest(raw[location.ordinal - 1]), location.rawSha256)
        assertEquals(75_073, sample.games.sumOf { it.uci.size })
        assertEquals(1, sample.games.minOf { it.uci.size }); assertEquals(253, sample.games.maxOf { it.uci.size })
        assertEquals(listOf(PackDependency("lichess-openings-c67912be581f-import-v1", "40d2ab2920dafa5c60ce163695c8e0e3fa31cf1cf922a285461aeafcb1f55dc3")), sample.source.manifest.dependencies)
        for (game in sample.games) {
            val parsed = Pgn.parse(game.canonicalPgn)
            assertEquals(game.uci, parsed.line.plies.map { it.move.uci })
            assertEquals(game.san, parsed.line.plies.map { it.san })
            assertEquals(game.finalFen, parsed.positions().last().toFen())
            assertTrue(parsed.line.plies.all { it.comments.isEmpty() && it.nags.isEmpty() && it.variations.isEmpty() })
            assertTrue(game.openingIds.isNotEmpty()); assertEquals("NOT_INCLUDED", game.annotationStatus)
        }
    }

    @Test fun wholeArchiveAndLegacyFixtureContributeDistinctBoundedObservedScores() {
        val samples = listOf(sample("lichess-broadcast-2020-01-2020-01-snap-import-v1"), sample("lichess-broadcast-2020-04-2020-04-snap-import-v1"))
        val index = ObservedReplyIndex.build(samples)
        assertEquals(936, index.totalScores)
        assertEquals(82_679, samples.sumOf { it.source.manifest.coverage.gamePlies })
        assertEquals(936, index.at(com.openinglab.shared.chess.BoardPosition.starting().positionKey).scoresSeen)
        assertEquals(936, index.at(com.openinglab.shared.chess.BoardPosition.starting().positionKey).scoresWithReply)
        assertEquals(samples.map { it.source }, index.sources)
        assertTrue(index.sources.all { it.manifest.source.license == "CC-BY-SA-4.0" })
    }
}
