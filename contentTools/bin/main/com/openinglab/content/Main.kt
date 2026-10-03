package com.openinglab.content

import com.openinglab.shared.content.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.charset.StandardCharsets.UTF_8

val contentJson = Json { encodeDefaults = true; prettyPrint = false }
private val prettyJson = Json { encodeDefaults = true; prettyPrint = true }

fun main(args: Array<String>) {
    require(args.size == 1 && args[0] in setOf("import", "verify")) { "Use :contentTools:run --args=import (or verify). Fetch sources first." }
    val content = Path.of("content").toAbsolutePath()
    val configBytes = Files.readAllBytes(content.resolve("sources.json"))
    val configuration = contentJson.decodeFromString<SourceConfiguration>(String(configBytes, UTF_8))
    val lock = contentJson.decodeFromString<SnapshotLock>(Files.readString(content.resolve("snapshots.lock.json")))
    require(configuration.schemaVersion == 1 && lock.schemaVersion == 1) { "Unsupported source snapshot schema" }
    require(configuration.sources.map { it.id }.distinct().size == configuration.sources.size)
    require(digest(configBytes) == lock.configSha256) { "Source configuration differs from pinned lock" }
    require(lock.files.size == configuration.sources.sumOf { it.files.size }) { "Snapshot lock file count differs" }
    require(lock.files.map { it.path }.distinct().size == lock.files.size) { "Duplicate snapshot paths" }
    val allOpenings = mutableListOf<OpeningRecord>()
    val taxonomyPacks = mutableListOf<PackDependency>()
    for (source in configuration.sources.sortedBy { it.kind.ordinal }) {
        ImportEngine.checkSource(source)
        val inputs = source.files.map { file ->
            val relative = "${source.id}/${file.name}"
            val pinned = lock.files.single { it.path == relative }
            require(pinned.url == file.url) { "Snapshot source URL mismatch" }
            verifyFile(content.resolve("raw").resolve(relative), pinned.bytes, pinned.sha256, 2 * 1024 * 1024)
            if (file.format == InputFormat.PGN_ZSTD) {
                require(pinned.decodedPath == "${source.id}/broadcast.pgn") { "Unexpected decoded path" }
                verifyFile(content.resolve("raw").resolve(requireNotNull(pinned.decodedPath)), requireNotNull(pinned.decodedBytes), requireNotNull(pinned.decodedSha256), 8 * 1024 * 1024)
            }
            pinned
        }
        val texts = source.files.filter { it.format != InputFormat.LICENSE }.map { file ->
            val relative = if (file.format == InputFormat.PGN_ZSTD) "${source.id}/broadcast.pgn" else "${source.id}/${file.name}"
            InputText(relative, Files.readString(content.resolve("raw").resolve(relative)))
        }
        val records = when (source.kind) {
            SourceKind.OPENING_TAXONOMY -> ImportEngine.openings(source, texts).also { allOpenings += it.openings }
            SourceKind.BROADCAST_GAMES -> ImportEngine.games(source, texts, allOpenings)
        }
        val payloads = linkedMapOf(
            "openings.jsonl" to lines(records.openings), "games.jsonl" to lines(records.games),
            "issues.jsonl" to lines(records.issues),
            "ATTRIBUTION.txt" to ("${source.title}\n${source.attribution}\nSource: ${source.url}\nRevision: ${source.revision}\n" +
                "License: ${source.license} (${source.licenseUrl})\nEvidence: ${source.licenseEvidenceUrl}\n" +
                "Changes: ${source.modifications}\nRaw sources retained under content/raw/${source.id}/. No endorsement implied.\n").toByteArray(UTF_8),
        )
        val packId = "${source.id}-${source.revision.take(12)}-import-v1"
        val manifest = ContentManifest(packId = packId, source = source, retrievedAt = lock.retrievedAt, inputs = inputs,
            coverage = records.coverage, files = payloads.map { (name, bytes) -> PackFile(name, bytes.size.toLong(), digest(bytes)) },
            snapshotLockSha256 = digest(Files.readAllBytes(content.resolve("snapshots.lock.json"))),
            dependencies = if (source.kind == SourceKind.BROADCAST_GAMES) taxonomyPacks.toList() else emptyList(),
            limitations = listOf(source.coverage, "Names and legally replayed scores are not reviewed teaching, engine analysis or winning promises.",
                "Source metadata is reported, not independently verified. Unresolved names remain source-scoped; no guessed GM aliases.",
                "Raw annotations/evaluations are retained in source files only, not surfaced as move authority.",
                "A recorded result and legal complete score do not prove the source did not omit earlier/later moves.",
                "Data-only pack: the consumer must supply atomic installation, durable storage and reviewed teaching."))
        payloads["manifest.json"] = (prettyJson.encodeToString(manifest) + "\n").toByteArray(UTF_8)
        publishOrVerify(content.resolve("packs").resolve(packId), payloads, args[0] == "verify")
        if (source.kind == SourceKind.OPENING_TAXONOMY) taxonomyPacks += PackDependency(packId, digest(payloads.getValue("manifest.json")))
        println("$packId: ${records.coverage.acceptedRecords} accepted, ${records.coverage.duplicates} duplicates, ${records.coverage.quarantined} quarantined; depth ${records.coverage.minPlies}–${records.coverage.maxPlies} plies")
    }
}

private inline fun <reified T> lines(records: List<T>): ByteArray = records.joinToString("", transform = { contentJson.encodeToString(it) + "\n" }).toByteArray(UTF_8)

fun verifyFile(path: Path, size: Long, hash: String, maximum: Int) {
    require(size in 0..maximum.toLong() && Files.size(path) == size) { "Input size mismatch: ${path.fileName}" }
    require(hash.matches(Regex("[a-f0-9]{64}")) && digest(Files.readAllBytes(path)) == hash) { "Input SHA-256 mismatch: ${path.fileName}" }
}

/** Never overwrite a different content version. Staged files become visible as one directory rename. */
fun publishOrVerify(directory: Path, payloads: Map<String, ByteArray>, verifyOnly: Boolean) {
    require(payloads.keys.all { it.matches(Regex("[A-Za-z0-9_.-]+")) && !it.contains("..") })
    if (Files.exists(directory)) {
        val actualNames = Files.list(directory).use { stream -> stream.map { it.fileName.toString() }.toList().toSet() }
        require(actualNames == payloads.keys) { "Pack contains missing/unexpected files: $directory" }
        for ((name, bytes) in payloads) require(Files.readAllBytes(directory.resolve(name)).contentEquals(bytes)) { "Existing pack differs: $name; create a new version, do not overwrite" }
        return
    }
    require(!verifyOnly) { "Missing pack: $directory" }
    Files.createDirectories(directory.parent)
    val staging = Files.createTempDirectory(directory.parent, ".staging-")
    try {
        for ((name, bytes) in payloads) Files.write(staging.resolve(name), bytes)
        Files.move(staging, directory, StandardCopyOption.ATOMIC_MOVE)
    } finally {
        if (Files.exists(staging)) {
            payloads.keys.forEach { Files.deleteIfExists(staging.resolve(it)) }
            Files.deleteIfExists(staging)
        }
    }
}
