// SPDX-License-Identifier: Apache-2.0
package com.openinglab.course

import com.openinglab.shared.chess.BoardPosition
import com.openinglab.shared.chess.parseSanAndPlay
import kotlinx.serialization.json.Json
import java.io.File

internal val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = false }
internal val cacheDir = File(".course-cache")
internal val engineDir = File(".engine-cache/service")

private fun usage(): Nothing {
    System.err.println("""
        Usage: courseTools <command>
          filter-club                      Filter club games from the downloaded prefix chunks
          filter-master                    Filter master games from downloaded broadcast months
          stats <course> <chapter>         Print club/master statistics at a chapter root
          engine <SAN moves...>            Analyse a position (multipv 5) and probe threat/prevents for the last move
          tree <course>                    Build line trees for every chapter (Phase 5)
          facts <course>                   Compute engine facts for every tree edge (Phase 3/7)
          context <course> <chapter>       Print a writer digest for every edge (agent-mode writer input)
          check <course>                   Check writer claims and auto facts (Phase 7)
          pack <course>                    Render, validate and publish the course pack (Phase 1/8)
          evals <course>                   Run gold-set/puzzle evaluations (Phase 4)
          game <course> <chapter>          Analyse the pilot GM game (Phase 11)
          toc <course>                     Data-only variation map and table of contents (owner checkpoint)
    """.trimIndent())
    kotlin.system.exitProcess(2)
}

fun main(args: Array<String>) {
    when (args.firstOrNull()) {
        "filter-club" -> filterClub()
        "filter-master" -> filterMaster()
        "stats" -> stats(args.getOrNull(1) ?: usage(), args.getOrNull(2) ?: usage())
        "engine" -> engine(args.drop(1))
        "tree" -> CoursePipeline(args.getOrNull(1) ?: usage()).tree()
        "facts" -> CoursePipeline(args.getOrNull(1) ?: usage()).facts()
        "context" -> CoursePipeline(args.getOrNull(1) ?: usage()).context(args.getOrNull(2) ?: usage())
        "check" -> CoursePipeline(args.getOrNull(1) ?: usage()).check()
        "pack" -> CoursePipeline(args.getOrNull(1) ?: usage()).pack()
        "evals" -> CoursePipeline(args.getOrNull(1) ?: usage()).evals()
        "game" -> CoursePipeline(args.getOrNull(1) ?: usage()).game(args.getOrNull(2) ?: usage())
        "toc" -> runToc(CoursePipeline(args.getOrNull(1) ?: usage()))
        "ideas" -> WinningIdeas.build(CoursePipeline(args.getOrNull(1) ?: usage())).forEach { (c, list) ->
            println("$c: ${list.size} variations; with White patterns ${list.count { it.white.patterns.isNotEmpty() }}, Black patterns ${list.count { it.black.patterns.isNotEmpty() }}") }
        "build" -> CoursePipeline(args.getOrNull(1) ?: usage()).let { p -> p.pool().use { pool ->
            FullCourseBuilder(p, EngineTools(pool, p.config.thresholds.depth), 3) { println(it) }.build() } }
        else -> usage()
    }
}

internal val clubRule = FilterRule("club-1600-2200-5min-plus", GameFilter.RUY_PREFIX, 1600, 2200, 300, 80)
internal val masterRule = FilterRule("master-2200-broadcast", GameFilter.RUY_PREFIX, 2200, 3500, 0, 600)
internal val clubFile = File(cacheDir, "filtered/club-ruy.tsv")
internal val masterFile = File(cacheDir, "filtered/master-ruy.tsv")

/** Contiguous complete chunks only: a missing chunk ends the usable prefix. */
internal fun contiguousChunks(dir: File): List<File> {
    val chunks = mutableListOf<File>()
    var i = 0
    while (true) { val f = File(dir, "chunk-%05d".format(i)); if (!f.isFile) break; chunks += f; i++ }
    return chunks
}

private fun filterClub() {
    val chunks = contiguousChunks(File(cacheDir, "raw/club-2026-09"))
    require(chunks.isNotEmpty()) { "No downloaded club chunks; run node scripts/fetch-course-data.mjs club" }
    val stats = FilterStats()
    clubFile.parentFile.mkdirs()
    val (process, reader) = GameFilter.openChunks(chunks)
    clubFile.bufferedWriter().use { out -> GameFilter.filter(reader, clubRule, stats) { out.write(it.toTsv()); out.newLine() } }
    process.waitFor()
    val bytes = chunks.sumOf { it.length() }
    File(cacheDir, "filtered/club-ruy.stats.json").writeText(json.encodeToString(FilterReport.serializer(),
        FilterReport(clubRule.name, chunks.size, bytes, stats.games, stats.kept, stats.rejectedSpeed, stats.rejectedRating, stats.rejectedPrefix, stats.rejectedOther, stats.truncatedTail)))
    println("Club: ${chunks.size} chunks / $bytes bytes → ${stats.games} games, kept ${stats.kept} Ruy Lopez games (speed −${stats.rejectedSpeed}, rating −${stats.rejectedRating}, other opening −${stats.rejectedPrefix}, other −${stats.rejectedOther}, truncated tail ${stats.truncatedTail})")
}

private fun filterMaster() {
    val months = File(cacheDir, "raw/broadcasts").listFiles()?.filter { it.isDirectory }?.sortedBy { it.name }.orEmpty()
    // The repository's already-reviewed 2020 broadcast exports (content/raw) are included as complete months.
    val retained = listOf("2020-01", "2020-04").map { File("content/raw/lichess-broadcast-$it/broadcast.pgn.zst") }.filter { it.isFile }
    val stats = FilterStats()
    masterFile.parentFile.mkdirs()
    var used = 0
    masterFile.bufferedWriter().use { out ->
        for (file in retained) {
            used++
            val (process, reader) = GameFilter.openChunks(listOf(file))
            GameFilter.filter(reader, masterRule, stats) { out.write(it.toTsv()); out.newLine() }
            process.waitFor()
        }
        for (month in months) {
            val chunks = contiguousChunks(month)
            val expectedComplete = File(month, "chunk-%05d".format(chunks.size)).exists().not() && chunks.isNotEmpty() &&
                month.listFiles()!!.none { it.name.endsWith(".part") }
            if (!expectedComplete) continue
            used++
            val (process, reader) = GameFilter.openChunks(chunks)
            GameFilter.filter(reader, masterRule, stats) { out.write(it.toTsv()); out.newLine() }
            process.waitFor()
        }
    }
    File(cacheDir, "filtered/master-ruy.stats.json").writeText(json.encodeToString(FilterReport.serializer(),
        FilterReport(masterRule.name, used, (months.size + retained.size).toLong(), stats.games, stats.kept, stats.rejectedSpeed, stats.rejectedRating, stats.rejectedPrefix, stats.rejectedOther, stats.truncatedTail)))
    println("Master: $used complete months → ${stats.games} games, kept ${stats.kept} Ruy Lopez games (rating −${stats.rejectedRating}, other opening −${stats.rejectedPrefix}, other −${stats.rejectedOther})")
}

@kotlinx.serialization.Serializable
data class FilterReport(val rule: String, val inputs: Int, val bytes: Long, val games: Long, val kept: Long, val rejectedSpeed: Long,
                        val rejectedRating: Long, val rejectedPrefix: Long, val rejectedOther: Long, val truncatedTail: Boolean)

private fun stats(course: String, chapter: String) {
    val pipeline = CoursePipeline(course)
    val config = pipeline.config.chapters.first { it.id == chapter }
    val (club, master) = pipeline.indexes(config)
    var board = BoardPosition.starting()
    config.rootSan.forEach { board = board.parseSanAndPlay(it).position }
    for ((name, index) in listOf("club" to club, "master" to master)) {
        val moves = index.movesAt(board.positionKey)
        val total = moves.values.sumOf { it.games }
        println("$name: ${index.games} games indexed (${index.rejected} rejected); $total at chapter root")
        moves.entries.sortedByDescending { it.value.games }.take(12).forEach { (uci, s) ->
            println("  $uci ${s.games} (${"%.1f".format(100.0 * s.games / total)}%) score ${"%.3f".format(s.scoreFor(board.sideToMove == com.openinglab.shared.model.PieceColor.WHITE))}")
        }
    }
}

private fun engine(sans: List<String>) {
    var board = BoardPosition.starting()
    val moves = sans.map { val t = board.parseSanAndPlay(it); board = t.position; t.move.uci }
    EnginePool(engineDir, File(cacheDir, "engine/cache.jsonl"), 1, 8).use { pool ->
        val tools = EngineTools(pool)
        val line = Line(moves)
        val a = tools.analyse(line, 5)
        a.lines.forEach { println("#${it.rank} d${it.depth} cp=${it.cp} mate=${it.mate} ${tools.sanLine(line.board, it.pv.take(8)).joinToString(" ")}") }
        if (moves.isNotEmpty()) {
            val before = Line(moves.dropLast(1))
            tools.passed(before.board)?.let { fen ->
                val p = pool.analyse(fen, emptyList(), 20, 5, emptyList())
                p.lines.forEach { println("passed #${it.rank} cp=${it.cp} ${tools.sanLine(BoardPosition.fromFen(fen), it.pv.take(6)).joinToString(" ")}") }
            }
            println("threat: ${tools.threat(line)}")
            println("prevents: ${tools.prevents(before, moves.last())}")
            println("verdict: ${tools.verdict(line)}")
        }
    }
}
