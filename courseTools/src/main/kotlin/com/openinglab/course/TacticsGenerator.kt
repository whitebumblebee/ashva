// SPDX-License-Identifier: Apache-2.0
package com.openinglab.course

import com.openinglab.shared.storage.contentSha256
import com.openinglab.shared.tactics.Puzzle
import com.openinglab.shared.tactics.PuzzleSet
import com.openinglab.shared.tactics.TacticsPack
import com.openinglab.shared.tactics.TacticsPackValidator
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.Reader

data class TacticsSetRule(val id: String, val name: String, val size: Int, val range: IntRange,
    val category: String, val matches: (Puzzle) -> Boolean = { true })
data class PuzzleCsvStats(var rows: Int = 0, var qualityRejected: Int = 0, var illegalRejected: Int = 0, var partialTail: Boolean = false)

object TacticsGenerator {
    const val SEED = 20261006L
    val compactJson = Json { encodeDefaults = true; explicitNulls = false }
    private val motifs = listOf("fork", "pin", "skewer", "discoveredAttack", "discoveredCheck", "mateIn2", "mateIn3",
        "backRankMate", "exposedKing", "kingsideAttack", "sacrifice", "attraction", "deflection", "advancedPawn",
        "promotion", "defensiveMove", "quietMove", "intermezzo", "hangingPiece", "trappedPiece", "endgame", "mateIn1")
    val rules: List<TacticsSetRule> = buildList {
        add(TacticsSetRule("woodpecker-easy", "Woodpecker · Easy", 222, 1000..1500, "WOODPECKER"))
        add(TacticsSetRule("woodpecker-intermediate", "Woodpecker · Intermediate", 762, 1500..2000, "WOODPECKER"))
        add(TacticsSetRule("woodpecker-advanced", "Woodpecker · Advanced", 144, 2000..2500, "WOODPECKER"))
        fun theme(id: String, name: String, vararg tags: String) {
            add(TacticsSetRule(id, name, 100, 1100..2100, "THEME") { p -> p.themes.any { it in tags } })
        }
        theme("forks", "Forks", "fork")
        theme("pins-skewers", "Pins & skewers", "pin", "skewer")
        theme("discovered-attacks", "Discovered attacks", "discoveredAttack", "discoveredCheck")
        theme("mate-in-2", "Mate in 2", "mateIn2")
        theme("mate-in-3", "Mate in 3", "mateIn3")
        theme("back-rank-exposed-king", "Back rank & exposed king", "backRankMate", "exposedKing", "kingsideAttack")
        theme("sacrifices", "Sacrifices", "sacrifice", "attraction", "deflection")
        add(TacticsSetRule("endgame-tactics", "Endgame tactics", 100, 1100..2100, "THEME") {
            "endgame" in it.themes && it.themes.any { tag -> tag in setOf("advancedPawn", "promotion") }
        })
        theme("defensive-moves", "Defensive moves", "defensiveMove", "quietMove", "intermezzo")
        add(TacticsSetRule("ruy-lopez", "Ruy Lopez tactics", 150, 900..2400, "OPENING") { it.opening?.startsWith("Ruy_Lopez") == true })
    }

    /** Emits only newline-terminated records: readLine() alone cannot distinguish a cut-off final CSV row. */
    fun readCsv(reader: Reader, stats: PuzzleCsvStats = PuzzleCsvStats()): List<Puzzle> {
        val puzzles = linkedMapOf<String, Puzzle>()
        val line = StringBuilder()
        val buffer = CharArray(64 * 1024)
        var header = true
        fun accept() {
            val row = line.toString().trimEnd('\r'); line.setLength(0)
            if (header) { require(row.startsWith("PuzzleId,FEN,Moves,Rating,")) { "Unsupported puzzle CSV header" }; header = false; return }
            stats.rows++
            val fields = csvFields(row)
            if (fields == null || fields.size != 11) { stats.qualityRejected++; return }
            val rating = fields[3].toIntOrNull()
            val deviation = fields[4].toIntOrNull()
            val popularity = fields[5].toIntOrNull()
            val plays = fields[6].toLongOrNull()
            val moves = fields[2].split(' ').filter { it.isNotBlank() }
            if (rating == null || rating !in 800..2600 || deviation == null || deviation !in 0..80 ||
                popularity == null || popularity < 90 || plays == null || plays < 1000 || moves.size !in 2..12 || moves.size % 2 != 0) {
                stats.qualityRejected++; return
            }
            val puzzle = Puzzle(fields[0], fields[1], moves, rating, fields[7].split(' ').filter { it.isNotBlank() }.distinct(),
                fields[9].substringBefore(' ').takeIf { it.isNotBlank() })
            try { TacticsPackValidator.validatePuzzle(puzzle) } catch (_: IllegalArgumentException) { stats.illegalRejected++; return }
            val existing = puzzles.putIfAbsent(puzzle.id, puzzle)
            require(existing == null || existing == puzzle) { "Conflicting puzzle id ${puzzle.id}" }
        }
        while (true) {
            val read = reader.read(buffer)
            if (read < 0) break
            for (i in 0 until read) {
                if (buffer[i] == '\n') accept() else {
                    line.append(buffer[i]); require(line.length <= 4096) { "Puzzle CSV row is too large" }
                }
            }
        }
        require(!header) { "Puzzle CSV has no complete header" }
        stats.partialTail = line.isNotEmpty()
        return puzzles.values.toList()
    }

    internal fun csvFields(line: String): List<String>? {
        val out = mutableListOf<String>(); val field = StringBuilder()
        var quoted = false; var i = 0
        while (i < line.length) {
            when (val ch = line[i]) {
                '"' -> if (quoted && line.getOrNull(i + 1) == '"') { field.append('"'); i++ } else quoted = !quoted
                ',' -> if (quoted) field.append(ch) else { out += field.toString(); field.setLength(0) }
                else -> field.append(ch)
            }; i++
        }
        if (quoted) return null
        out += field.toString(); return out
    }

    /** A stable motif assignment makes groups disjoint; every round visits each available motif once. */
    fun diverse(puzzles: List<Puzzle>, size: Int, seed: Long = SEED): List<Puzzle> {
        val hashes = puzzles.associate { it.id to contentSha256("$seed|${it.id}".encodeToByteArray()) }
        val order = compareBy<Puzzle> { it.rating }.thenBy { hashes.getValue(it.id) }.thenBy { it.id }
        return roundRobin(puzzles, size, order, compareBy<Puzzle> { it.rating }.thenBy { it.id })
    }

    private fun roundRobin(puzzles: List<Puzzle>, size: Int, order: Comparator<Puzzle>,
        roundOrder: Comparator<Puzzle> = order): List<Puzzle> {
        val groups = puzzles.groupBy { p -> motifs.firstOrNull { it in p.themes } ?: "other" }.toSortedMap()
            .values.map { group -> group.sortedWith(order).iterator() }
        val result = mutableListOf<Puzzle>()
        while (result.size < size) {
            val round = groups.mapNotNull { if (it.hasNext()) it.next() else null }.sortedWith(roundOrder)
            if (round.isEmpty()) break
            result += round.take(size - result.size)
        }
        return result
    }

    /** Five equal-width bands, with the inclusive upper endpoint in the final band. */
    private fun spread(eligible: List<Puzzle>, rule: TacticsSetRule, seed: Long): List<Puzzle> {
        require(eligible.size >= rule.size) { "${rule.id}: need ${rule.size}, found ${eligible.size} eligible puzzles" }
        val width = (rule.range.last.toLong() - rule.range.first).coerceAtLeast(1)
        val buckets = List(5) { mutableListOf<Puzzle>() }
        eligible.forEach { puzzle ->
            val bucket = ((puzzle.rating.toLong() - rule.range.first) * buckets.size / width).toInt().coerceAtMost(buckets.lastIndex)
            buckets[bucket] += puzzle
        }
        val quotas = IntArray(buckets.size) { rule.size / buckets.size + if (it < rule.size % buckets.size) 1 else 0 }
        val counts = IntArray(buckets.size) { minOf(quotas[it], buckets[it].size) }
        // Reserve every band's share before borrowing the nearest available surplus.
        for (bucket in buckets.indices) {
            var missing = quotas[bucket] - counts[bucket]
            if (missing <= 0) continue
            val neighbours = buckets.indices.sortedWith(compareBy<Int> { kotlin.math.abs(it - bucket) }.thenBy { it })
            for (neighbour in neighbours) {
                val extra = minOf(missing, buckets[neighbour].size - counts[neighbour])
                counts[neighbour] += extra
                missing -= extra
                if (missing == 0) break
            }
        }
        val hashes = eligible.associate { it.id to contentSha256("$seed|${rule.id}|${it.id}".encodeToByteArray()) }
        // Rank by seed within each motif so even a single-motif band avoids its lowest-rated edge.
        val order = compareBy<Puzzle> { hashes.getValue(it.id) }.thenBy { it.id }
        return buckets.flatMapIndexed { index, bucket -> roundRobin(bucket, counts[index], order) }
            .sortedWith(compareBy<Puzzle> { it.rating }.thenBy { it.id })
    }

    fun compose(puzzles: List<Puzzle>, setRules: List<TacticsSetRule> = rules, poolSize: Int = 4000, seed: Long = SEED): TacticsPack {
        require(puzzles.map { it.id }.distinct().size == puzzles.size)
        fun seeded(list: List<Puzzle>, tag: String): List<Puzzle> {
            val hashes = list.associate { it.id to contentSha256("$seed|$tag|${it.id}".encodeToByteArray()) }
            return list.sortedWith(compareBy<Puzzle> { hashes.getValue(it.id) }.thenBy { it.id })
        }
        val sets = setRules.map { rule ->
            val eligible = puzzles.filter { it.rating in rule.range && rule.matches(it) }
            val picked = spread(eligible, rule, seed)
            PuzzleSet(rule.id, rule.name, picked.map { it.id }, rule.category)
        }
        val selected = sets.flatMap { it.puzzleIds }.toSet()
        val extra = puzzles.filter { it.id !in selected }.groupBy { ((it.rating - 800) / 100).coerceAtMost(17) }.toSortedMap()
        // Seed sampling precedes motif round-robin so the pool spans each rating band rather than its lower edge.
        val bins = extra.map { (bin, list) ->
            diverse(seeded(list, "pool").take(2000),
                poolSize / 18 + if (bin < poolSize % 18) 1 else 0, seed)
        }
        val pool = bins.flatten().toMutableList()
        if (pool.size < poolSize) {
            val used = selected + pool.map { it.id }
            pool += seeded(puzzles.filter { it.id !in used }, "remainder").take(poolSize - pool.size)
        }
        require(pool.size == poolSize) { "Not enough puzzles for the custom pool" }
        val all = puzzles.associateBy { it.id }
        return TacticsPack(puzzles = (selected.map { all.getValue(it) } + pool).sortedBy { it.id }, sets = sets)
            .also { TacticsPackValidator.validate(it) }
    }

    fun run(input: File = File(cacheDir, "raw/puzzles/chunk-00000"), output: File = File("content/tactics/v1")) {
        val stats = PuzzleCsvStats()
        val (process, reader) = GameFilter.openChunks(listOf(input))
        val puzzles = try { reader.use { readCsv(it, stats) } } finally { process.waitFor() }
        // zstd exits nonzero on the expected truncated compressed frame; complete decoded CSV records remain usable.
        val pack = compose(puzzles)
        val bytes = (compactJson.encodeToString(TacticsPack.serializer(), pack) + "\n").encodeToByteArray()
        require(bytes.size <= 1_500_000) { "Tactics pack exceeds 1.5 MB (${bytes.size})" }
        val manifest = TacticsManifest(sha256 = contentSha256(bytes), bytes = bytes.size, puzzles = pack.puzzles.size,
            customPool = pack.puzzles.size - pack.sets.flatMap { it.puzzleIds }.distinct().size,
            sets = pack.sets.associate { it.id to it.puzzleIds.size }, inputSha256 = contentSha256(input.readBytes()),
            rows = stats.rows, qualityRejected = stats.qualityRejected, illegalRejected = stats.illegalRejected,
            partialLastRowIgnored = stats.partialTail)
        output.mkdirs()
        File(output, "tactics.json").writeBytes(bytes)
        File(output, "tactics.manifest.json").writeText(json.encodeToString(TacticsManifest.serializer(), manifest) + "\n")
        println("Tactics: ${stats.rows} complete rows, ${puzzles.size} eligible, ${pack.puzzles.size} bundled / ${pack.sets.size} sets / ${manifest.customPool} custom pool; ${bytes.size} bytes; sha256 ${manifest.sha256}")
        pack.sets.forEach { println("  ${it.id}: ${it.puzzleIds.size}") }
    }
}

@Serializable
data class TacticsManifest(val schema: Int = 1, val sha256: String, val bytes: Int, val puzzles: Int, val customPool: Int,
    val sets: Map<String, Int>, val source: String = "https://database.lichess.org/lichess_db_puzzle.csv.zst",
    val license: String = "CC0-1.0", val inputSha256: String, val seed: Long = TacticsGenerator.SEED,
    val rows: Int, val qualityRejected: Int, val illegalRejected: Int, val partialLastRowIgnored: Boolean,
    val note: String = "16 MiB compressed prefix; complete rows only. Quality-filtered and legally replayed; fixed offline training sets.")
