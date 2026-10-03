package com.openinglab.content

import com.openinglab.shared.content.*
import com.openinglab.shared.chess.Pgn
import java.security.MessageDigest
import java.nio.charset.StandardCharsets.UTF_8

fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
fun digest(text: String): String = digest(text.toByteArray(UTF_8))
data class InputText(val name: String, val text: String)
data class ImportedRecords(
    val openings: List<OpeningRecord> = emptyList(), val games: List<GameRecord> = emptyList(),
    val issues: List<ImportIssue>, val coverage: CoverageCounts,
)

object ImportEngine {
    fun checkSource(source: ContentSource) {
        require(source.id.matches(Regex("[a-z0-9-]+"))) { "Invalid source ID" }
        require(source.revision.matches(Regex("[A-Za-z0-9_.-]+")) && !source.revision.contains("..")) { "Unsafe source revision" }
        require(source.redistributionApproved && source.rightsReviewedOn.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) { "Source rights not reviewed/approved for this import" }
        require(source.license == if (source.kind == SourceKind.OPENING_TAXONOMY) "CC0-1.0" else "CC-BY-SA-4.0") { "Unsupported collection license; review separately" }
        require(listOf(source.url, source.licenseUrl, source.licenseEvidenceUrl).all { it.startsWith("https://") }) { "Source/license evidence URLs required" }
        require(source.attribution.isNotBlank() && source.revision.isNotBlank() && source.coverage.isNotBlank() && source.modifications.isNotBlank())
        require(source.files.isNotEmpty() && source.files.map { it.name }.distinct().size == source.files.size)
        require(source.files.all { it.name.matches(Regex("[A-Za-z0-9_.-]+")) && !it.name.contains("..") }) { "Unsafe source filename" }
        when (source.kind) {
            SourceKind.OPENING_TAXONOMY -> require(source.files.any { it.format == InputFormat.OPENINGS_TSV } &&
                source.files.all { it.format in setOf(InputFormat.OPENINGS_TSV, InputFormat.LICENSE) }) { "Taxonomy expects TSV/license files" }
            SourceKind.BROADCAST_GAMES -> require(source.files.size == 1 && source.files.single().format == InputFormat.PGN_ZSTD) { "Import one bounded broadcast archive per source" }
        }
    }

    fun openings(source: ContentSource, inputs: List<InputText>): ImportedRecords {
        checkSource(source); require(source.kind == SourceKind.OPENING_TAXONOMY)
        val accepted = linkedMapOf<String, OpeningRecord>()
        val issues = mutableListOf<ImportIssue>()
        var rows = 0
        for (input in inputs) {
            require(input.text.length <= 2 * 1024 * 1024)
            val lines = input.text.lineSequence().toList()
            require(lines.firstOrNull()?.removePrefix("\uFEFF") == "eco\tname\tpgn") { "Unexpected TSV header in ${input.name}" }
            for ((index, raw) in lines.drop(1).withIndex()) {
                if (raw.isBlank()) continue
                require(++rows <= 50_000) { "Too many taxonomy rows" }
                val location = SourceLocation(source.id, input.name, index + 2, digest(raw))
                try {
                    val fields = raw.split('\t')
                    require(fields.size == 3) { "Expected exactly three TSV columns" }
                    val (eco, name, pgn) = fields
                    val game = SourceValidation.opening(eco, name, pgn)
                    val moves = game.line.plies.map { it.move.uci }
                    val id = "opening-" + digest("$eco\n$name\n${moves.joinToString(" ")}")
                    val previous = accepted[id]
                    if (previous != null) {
                        accepted[id] = previous.copy(occurrences = previous.occurrences + location)
                        issues += ImportIssue(location, IssueKind.DUPLICATE, "Exact repeated taxonomy line", id)
                    } else {
                        val final = game.positions().last()
                        accepted[id] = OpeningRecord(id, eco, name, name.substringBefore(':').trim(), moves,
                            game.line.plies.map { it.san }, final.toFen(), final.positionKey, listOf(location))
                    }
                } catch (error: IllegalArgumentException) { issues += ImportIssue(location, IssueKind.INVALID, safeDetail(error)) }
            }
        }
        val records = accepted.values.sortedBy { it.id }
        val counts = counts(rows, records.size, issues, records.map { it.uci.size }).copy(
            families = records.map { it.family }.distinct().size, distinctNames = records.map { it.name }.distinct().size,
            distinctPositions = records.map { it.positionKey }.distinct().size,
            transposedPositions = records.groupBy { it.positionKey }.count { (_, lines) -> lines.map { it.uci }.distinct().size > 1 })
        return ImportedRecords(openings = records, issues = issues, coverage = counts)
    }

    private data class Candidate(val sourceKey: String, val signature: String, val game: GameRecord)

    fun games(source: ContentSource, inputs: List<InputText>, taxonomy: List<OpeningRecord>): ImportedRecords {
        checkSource(source); require(source.kind == SourceKind.BROADCAST_GAMES)
        val index = SourcedOpeningIndex(taxonomy)
        val candidates = mutableListOf<Candidate>()
        val issues = mutableListOf<ImportIssue>()
        var rows = 0
        for (input in inputs) for ((i, raw) in PgnFrames.split(input.text).withIndex()) {
            require(++rows <= 10_000) { "Too many game records" }
            val location = SourceLocation(source.id, input.name, i + 1, digest(raw))
            try {
                val game = SourceValidation.game(raw)
                val positions = game.positions()
                val match = index.identify(positions)
                val white = player(source.id, game.tags, "White")
                val black = player(source.id, game.tags, "Black")
                val uci = game.line.plies.map { it.move.uci }
                val key = game.tags["GameURL"]?.takeIf { it.startsWith("https://") }
                    ?: "record-" + digest(listOf(game.tags["Event"], game.tags["Date"] ?: game.tags["UTCDate"],
                        game.tags["Round"], game.tags["Board"], white.id, black.id).joinToString("\n"))
                val signature = digest(listOf(white.id, black.id, game.tags["Event"], game.tags["Date"] ?: game.tags["UTCDate"],
                    game.tags["Round"], game.tags["Board"], game.tags["UTCTime"], game.initialPosition.toFen(), uci.joinToString(" "), game.result).joinToString("\n"))
                val record = GameRecord("game-$signature", listOf(key), white, black, game.tags, game.result,
                    game.initialPosition.toFen(), uci, game.line.plies.map { it.san }, positions.last().toFen(), Pgn.export(game),
                    match?.openings?.map { it.id }.orEmpty(), match?.matchedAtPly, listOf(location))
                candidates += Candidate(key, signature, record)
            } catch (error: IllegalArgumentException) {
                val detail = safeDetail(error)
                val kind = when {
                    detail.contains("Unsupported chess variant") -> IssueKind.UNSUPPORTED
                    detail.contains("Incomplete game") -> IssueKind.INCOMPLETE
                    else -> IssueKind.INVALID
                }
                issues += ImportIssue(location, kind, detail)
            }
        }
        // Quarantine *all* conflicting records, not just the second one, before deduplicating.
        val conflicts = candidates.groupBy { it.sourceKey }.filterValues { group -> group.map { it.signature }.distinct().size > 1 }.keys
        candidates.filter { it.sourceKey in conflicts }.forEach {
            issues += ImportIssue(it.game.occurrences.single(), IssueKind.CONFLICT, "Source game ID has conflicting moves, result or identity")
        }
        val records = candidates.filterNot { it.sourceKey in conflicts }.groupBy { it.signature }.map { (_, group) ->
            val first = group.first().game
            group.drop(1).forEach { issues += ImportIssue(it.game.occurrences.single(), IssueKind.DUPLICATE, "Repeated game score and metadata", first.id) }
            first.copy(externalIds = group.map { it.sourceKey }.distinct(), occurrences = group.flatMap { it.game.occurrences })
        }.sortedBy { it.id }
        val counts = counts(rows, records.size, issues, records.map { it.uci.size }).copy(
            gamePlies = records.sumOf { it.uci.size }, gamesMatchedToTaxonomy = records.count { it.openingIds.isNotEmpty() },
            distinctPositions = records.flatMap { record ->
                Pgn.parse(record.canonicalPgn).positions().map { it.positionKey }
            }.distinct().size,
            unresolvedPlayers = records.flatMap { listOf(it.white, it.black) }.distinctBy { it.id }.count { it.fideId == null })
        return ImportedRecords(games = records, issues = issues.sortedWith(compareBy({ it.location.file }, { it.location.ordinal })), coverage = counts)
    }

    private fun player(source: String, tags: Map<String, String>, side: String): PlayerReference {
        val name = tags.getValue(side)
        val fide = tags["${side}FideId"]?.takeIf { it.matches(Regex("[1-9][0-9]{0,9}")) }
        return PlayerReference(fide?.let { "fide-$it" } ?: "source-player-" + digest("$source\n${name.trim().lowercase()}"),
            name, fide, if (fide == null) "SOURCE_SCOPED_UNVERIFIED" else "FIDE_ID_REPORTED_BY_SOURCE")
    }

    private fun counts(input: Int, accepted: Int, issues: List<ImportIssue>, depths: List<Int>): CoverageCounts {
        val duplicates = issues.count { it.kind == IssueKind.DUPLICATE }
        val rejected = issues.size - duplicates
        check(input == accepted + duplicates + rejected) { "Every source record must have a disposition" }
        return CoverageCounts(input, accepted, duplicates, rejected, minPlies = depths.minOrNull() ?: 0, maxPlies = depths.maxOrNull() ?: 0)
    }
    private fun safeDetail(error: IllegalArgumentException): String = (error.message ?: "Invalid source record").filterNot { it.isISOControl() }.take(240)
}
