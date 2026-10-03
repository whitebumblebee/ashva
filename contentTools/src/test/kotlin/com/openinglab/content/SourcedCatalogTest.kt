// SPDX-License-Identifier: Apache-2.0
package com.openinglab.content

import com.openinglab.shared.content.*
import com.openinglab.shared.data.SourcedOpeningCatalog
import com.openinglab.shared.lesson.LessonGraph
import com.openinglab.shared.lesson.LessonPathKind
import com.openinglab.shared.model.PieceColor
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class SourcedCatalogTest {
    @Test fun wholePinnedTaxonomyIsExposedWithoutTruncatingFamiliesOrRoutes() {
        val directory = Path.of("content/packs/lichess-openings-c67912be581f-import-v1")
        val manifest = contentJson.decodeFromString<ContentManifest>(Files.readString(directory.resolve("manifest.json")))
        val records = Files.readAllLines(directory.resolve("openings.jsonl")).map { contentJson.decodeFromString<OpeningRecord>(it) }
        val catalog = SourcedOpeningCatalog(manifest, records)
        assertEquals(149, catalog.openings.size)
        assertEquals(3815, catalog.openings.sumOf { it.variations.size })
        assertEquals(records.map { it.id }.toSet(), catalog.openings.flatMap { it.variations }.map { it.id }.toSet())
        assertTrue(catalog.openings.all { it.provenance?.license == "CC0-1.0" && it.historicalGame == null })
        val ruy = catalog.openings.single { it.name == "Ruy Lopez" }
        assertEquals(235, ruy.variations.size)
        assertEquals(391, catalog.openings.single { it.name == "Sicilian Defense" }.variations.size)
        assertEquals(1, catalog.openings.minOf { requireNotNull(it.provenance).minPlies })
        assertEquals(36, catalog.openings.maxOf { requireNotNull(it.provenance).maxPlies })
        assertEquals(ruy.id, catalog.search("Ruy Berlin").single().id)
        val graph = LessonGraph.fromOpening(ruy)
        assertEquals(235, graph.paths.size)
        assertTrue(graph.paths.values.all { it.kind == LessonPathKind.SOURCED_OPENING })
        val byId = records.associateBy { it.id }
        for (path in graph.paths.values) for (side in PieceColor.entries) {
            val replay = graph.start(side, path.id).last()
            val record = byId.getValue(path.id)
            assertEquals(record.uci, replay.moves.map { it.move.uci })
            assertEquals(record.finalFen, replay.position.toFen())
        }
    }
}
