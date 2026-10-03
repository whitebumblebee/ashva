// SPDX-License-Identifier: Apache-2.0
package com.openinglab.content

import com.openinglab.shared.content.*
import kotlinx.serialization.encodeToString
import java.nio.file.Files
import kotlin.test.*

class SnapshotDependencyTest {
    @Test fun snapshotNamespaceIsExplicitAndCannotEscapeOrChangeDefaultPaths() {
        assertEquals(SnapshotPaths("sources.json", "snapshots.lock.json", true), snapshotPaths(arrayOf("verify")))
        assertEquals(SnapshotPaths("snapshots/broadcast-2020-01.sources.json", "snapshots/broadcast-2020-01.lock.json", false),
            snapshotPaths(arrayOf("import", "broadcast-2020-01")))
        for (args in listOf(emptyArray(), arrayOf("unknown"), arrayOf("verify", "../escape"), arrayOf("import", ""),
            arrayOf("verify", "/tmp"), arrayOf("verify", "safe", "extra")))
            assertFailsWith<IllegalArgumentException> { snapshotPaths(args) }
    }

    @Test fun pinnedDependencyLoadsWithoutRewritingItsFilesAndRejectsTampering() {
        val root = Files.createTempDirectory("ashva-dependency-test-")
        try {
            val source = contentJson.decodeFromString<SourceConfiguration>(Files.readString(java.nio.file.Path.of("content/sources.json"))).sources.first()
            val imported = ImportEngine.openings(source, listOf(InputText("a.tsv", "eco\tname\tpgn\nA00\tTest Opening\t1. a3\n")))
            val bytes = (contentJson.encodeToString(imported.openings.single()) + "\n").toByteArray()
            val payloads = mapOf("openings.jsonl" to bytes, "games.jsonl" to byteArrayOf(), "issues.jsonl" to byteArrayOf(), "ATTRIBUTION.txt" to "test".toByteArray())
            val manifest = ContentManifest(packId = "taxonomy-v1", source = source, retrievedAt = "2026-10-02T00:00:00Z", inputs = emptyList(),
                coverage = imported.coverage, files = payloads.map { (name, data) -> PackFile(name, data.size.toLong(), digest(data)) },
                limitations = listOf("Synthetic test"), snapshotLockSha256 = "0".repeat(64))
            val manifestBytes = contentJson.encodeToString(manifest).toByteArray()
            val directory = root.resolve("packs/taxonomy-v1")
            Files.createDirectories(directory)
            (payloads + ("manifest.json" to manifestBytes)).forEach { (name, data) -> Files.write(directory.resolve(name), data) }
            val dependency = PackDependency("taxonomy-v1", digest(manifestBytes))
            assertEquals(imported.openings, loadTaxonomyDependencies(root, listOf(dependency)))
            assertContentEquals(manifestBytes, Files.readAllBytes(directory.resolve("manifest.json")))
            assertFailsWith<IllegalArgumentException> { loadTaxonomyDependencies(root, listOf(dependency.copy(manifestSha256 = "1".repeat(64)))) }
            assertFailsWith<IllegalArgumentException> { loadTaxonomyDependencies(root, listOf(dependency, dependency)) }
            assertFailsWith<IllegalArgumentException> { loadTaxonomyDependencies(root, listOf(dependency.copy(packId = "../escape"))) }
            Files.writeString(directory.resolve("openings.jsonl"), "tampered")
            assertFailsWith<IllegalArgumentException> { loadTaxonomyDependencies(root, listOf(dependency)) }
            Files.write(directory.resolve("openings.jsonl"), bytes)
            val wrongManifest = contentJson.encodeToString(manifest.copy(source = source.copy(kind = SourceKind.BROADCAST_GAMES))).toByteArray()
            Files.write(directory.resolve("manifest.json"), wrongManifest)
            assertFailsWith<IllegalArgumentException> { loadTaxonomyDependencies(root, listOf(dependency.copy(manifestSha256 = digest(wrongManifest)))) }
        } finally {
            Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }
}
