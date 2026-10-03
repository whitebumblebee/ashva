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

data class SnapshotPaths(val configuration: String, val lock: String, val verifyOnly: Boolean)
fun snapshotPaths(args: Array<String>): SnapshotPaths {
    require(args.size in 1..2 && args[0] in setOf("import", "verify")) {
        "Use :contentTools:run --args='import [reviewed-snapshot-name]' (or verify). Fetch sources first."
    }
    val name = args.getOrNull(1)
    require(name == null || name.matches(Regex("[a-z0-9][a-z0-9-]{0,63}"))) { "Unsafe snapshot name" }
    return SnapshotPaths(name?.let { "snapshots/$it.sources.json" } ?: "sources.json",
        name?.let { "snapshots/$it.lock.json" } ?: "snapshots.lock.json", args[0] == "verify")
}

fun main(args: Array<String>) {
    val paths = snapshotPaths(args)
    val content = Path.of("content").toAbsolutePath()
    val configBytes = Files.readAllBytes(content.resolve(paths.configuration))
    val configuration = contentJson.decodeFromString<SourceConfiguration>(String(configBytes, UTF_8))
    val lockBytes = Files.readAllBytes(content.resolve(paths.lock))
    val lock = contentJson.decodeFromString<SnapshotLock>(String(lockBytes, UTF_8))
    require(configuration.schemaVersion == 1 && lock.schemaVersion == 1) { "Unsupported source snapshot schema" }
    require(configuration.sources.size in 1..32)
    require(configuration.sources.map { it.id }.distinct().size == configuration.sources.size)
    require(digest(configBytes) == lock.configSha256) { "Source configuration differs from pinned lock" }
    require(lock.files.size == configuration.sources.sumOf { it.files.size }) { "Snapshot lock file count differs" }
    require(lock.files.map { it.path }.distinct().size == lock.files.size) { "Duplicate snapshot paths" }
    val inputPaths = configuration.sources.flatMap { source -> source.files.map { "${source.id}/${it.name}" } }.toSet()
    require(configuration.acquisitionSha256.all { (path, hash) -> path in inputPaths && hash.matches(Regex("[a-f0-9]{64}")) })
    val allOpenings = loadTaxonomyDependencies(content, configuration.dependencies).toMutableList()
    val taxonomyPacks = configuration.dependencies.toMutableList()
    for (source in configuration.sources.sortedBy { it.kind.ordinal }) {
        ImportEngine.checkSource(source)
        val inputs = source.files.map { file ->
            val relative = "${source.id}/${file.name}"
            val pinned = lock.files.single { it.path == relative }
            require(pinned.url == file.url) { "Snapshot source URL mismatch" }
            configuration.acquisitionSha256[relative]?.let { require(it == pinned.sha256) { "Provider checksum differs from lock" } }
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
            snapshotLockSha256 = digest(lockBytes),
            dependencies = if (source.kind == SourceKind.BROADCAST_GAMES) taxonomyPacks.toList() else emptyList(),
            limitations = listOf(source.coverage, "Names and legally replayed scores are not reviewed teaching, engine analysis or winning promises.",
                "Source metadata is reported, not independently verified. Unresolved names remain source-scoped; no guessed GM aliases.",
                "Raw annotations/evaluations are retained in source files only, not surfaced as move authority.",
                "A recorded result and legal complete score do not prove the source did not omit earlier/later moves.",
                "Data-only pack: the consumer must supply atomic installation, durable storage and reviewed teaching."))
        payloads["manifest.json"] = (prettyJson.encodeToString(manifest) + "\n").toByteArray(UTF_8)
        require(taxonomyPacks.none { it.packId == packId }) { "Snapshot must not rebuild an immutable dependency" }
        publishOrVerify(content.resolve("packs").resolve(packId), payloads, paths.verifyOnly)
        if (source.kind == SourceKind.OPENING_TAXONOMY) taxonomyPacks += PackDependency(packId, digest(payloads.getValue("manifest.json")))
        println("$packId: ${records.coverage.acceptedRecords} accepted, ${records.coverage.duplicates} duplicates, ${records.coverage.quarantined} quarantined; depth ${records.coverage.minPlies}–${records.coverage.maxPlies} plies")
    }
}

/** Reuse exact old taxonomy manifests, never regenerate their retrieval provenance under a new lock. */
fun loadTaxonomyDependencies(content: Path, dependencies: List<PackDependency>): List<OpeningRecord> {
    require(dependencies.size <= 32 && dependencies.map { it.packId }.distinct().size == dependencies.size)
    val records = mutableListOf<OpeningRecord>()
    for (dependency in dependencies) {
        require(dependency.packId.matches(Regex("[a-z0-9-]{1,200}")) && dependency.manifestSha256.matches(Regex("[a-f0-9]{64}")))
        val directory = content.resolve("packs").resolve(dependency.packId)
        val manifestPath = directory.resolve("manifest.json")
        verifyFile(manifestPath, Files.size(manifestPath), dependency.manifestSha256, 65_536)
        val manifest = contentJson.decodeFromString<ContentManifest>(Files.readString(manifestPath))
        require(manifest.schemaVersion == 1 && manifest.packId == dependency.packId && manifest.source.kind == SourceKind.OPENING_TAXONOMY &&
            manifest.dependencies.isEmpty()) { "Dependency must be an immutable taxonomy pack" }
        ImportEngine.checkSource(manifest.source)
        require(manifest.files.map { it.name }.toSet() == setOf("openings.jsonl", "games.jsonl", "issues.jsonl", "ATTRIBUTION.txt") &&
            manifest.files.size == 4) { "Unexpected taxonomy payload files" }
        for (file in manifest.files) verifyFile(directory.resolve(file.name), file.bytes, file.sha256, 8 * 1024 * 1024)
        require(Files.size(directory.resolve("games.jsonl")) == 0L)
        val openings = Files.readAllLines(directory.resolve("openings.jsonl")).filter { it.isNotBlank() }
        require(openings.size <= 50_000 && records.size + openings.size <= 50_000 && openings.size == manifest.coverage.acceptedRecords)
        for (line in openings) {
            val record = contentJson.decodeFromString<OpeningRecord>(line)
            val game = SourceValidation.opening(record.eco, record.name, record.san.joinToString(" "))
            val final = game.positions().last()
            require(game.line.plies.map { it.move.uci } == record.uci && game.line.plies.map { it.san } == record.san &&
                final.toFen() == record.finalFen && final.positionKey == record.positionKey &&
                record.id == "opening-" + digest("${record.eco}\n${record.name}\n${record.uci.joinToString(" ")}")) {
                "Taxonomy dependency record differs from legal canonical moves"
            }
            records += record
        }
    }
    require(records.map { it.id }.distinct().size == records.size) { "Dependencies contain duplicate opening IDs" }
    return records
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
