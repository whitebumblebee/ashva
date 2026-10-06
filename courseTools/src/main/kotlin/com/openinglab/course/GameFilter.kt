// SPDX-License-Identifier: Apache-2.0
package com.openinglab.course

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/** One kept game: SAN tokens only (comments/variations/clocks removed); provenance retained. */
data class FilteredGame(
    val id: String, val result: String, val whiteElo: Int?, val blackElo: Int?, val speed: String,
    val date: String, val white: String, val black: String, val event: String, val site: String, val san: List<String>,
) {
    fun toTsv(): String = listOf(id, result, whiteElo?.toString() ?: "", blackElo?.toString() ?: "", speed, date, white, black,
        event, site, san.joinToString(" ")).joinToString("\t") { it.replace('\t', ' ').replace('\n', ' ') }

    companion object {
        fun fromTsv(line: String): FilteredGame {
            val f = line.split('\t')
            require(f.size == 11) { "Malformed filtered game row" }
            return FilteredGame(f[0], f[1], f[2].toIntOrNull(), f[3].toIntOrNull(), f[4], f[5], f[6], f[7], f[8], f[9],
                f[10].split(' ').filter { it.isNotEmpty() })
        }
    }
}

data class FilterRule(
    val name: String,
    val prefix: List<String>,
    val minElo: Int,
    val maxElo: Int,
    val minEstimatedSeconds: Int,
    val maxPlies: Int,
)

data class FilterStats(var games: Long = 0, var kept: Long = 0, var rejectedSpeed: Long = 0, var rejectedRating: Long = 0,
                       var rejectedPrefix: Long = 0, var rejectedOther: Long = 0, var truncatedTail: Boolean = false)

object GameFilter {
    val RUY_PREFIX = listOf("e4", "e5", "Nf3", "Nc6", "Bb5")
    private val results = setOf("1-0", "0-1", "1/2-1/2")

    /** Strip {comments}, ;comments, (variations), $NAGs, move numbers and results; keep SAN tokens. */
    fun sanTokens(movetext: String, limit: Int = Int.MAX_VALUE): List<String> {
        val out = ArrayList<String>()
        var depth = 0
        var i = 0
        val token = StringBuilder()
        fun flush() {
            if (token.isEmpty()) return
            val t = token.toString(); token.setLength(0)
            if (depth > 0 || t.startsWith("$") || t in results || t == "*") return
            val stripped = t.replace(Regex("^\\d+\\.+"), "")
            if (stripped.isNotEmpty() && !stripped.all { it.isDigit() || it == '.' }) out += stripped
        }
        while (i < movetext.length && out.size < limit) {
            val c = movetext[i]
            when {
                c == '{' -> { flush(); val end = movetext.indexOf('}', i); i = if (end < 0) movetext.length else end }
                c == ';' -> { flush(); val end = movetext.indexOf('\n', i); i = if (end < 0) movetext.length else end }
                c == '(' -> { flush(); depth++ }
                c == ')' -> { flush(); depth = maxOf(0, depth - 1) }
                c.isWhitespace() -> flush()
                else -> token.append(c)
            }
            i++
        }
        flush()
        return if (out.size > limit) out.subList(0, limit) else out
    }

    fun estimatedSeconds(timeControl: String?): Int? {
        val match = Regex("^(\\d+)\\+(\\d+)$").find(timeControl ?: return null) ?: return null
        return match.groupValues[1].toInt() + 40 * match.groupValues[2].toInt()
    }

    fun speed(seconds: Int?): String = when {
        seconds == null -> "unknown"; seconds < 180 -> "bullet"; seconds < 480 -> "blitz"; seconds < 1500 -> "rapid"; else -> "classical"
    }

    /** Streams PGN text; [emit] receives kept games. Incomplete trailing games (truncated prefix) are dropped. */
    fun filter(reader: BufferedReader, rule: FilterRule, stats: FilterStats, emit: (FilteredGame) -> Unit) {
        val headers = HashMap<String, String>()
        val movetext = StringBuilder()
        fun finish(complete: Boolean) {
            if (headers.isEmpty()) return
            if (!complete) { stats.truncatedTail = true; headers.clear(); movetext.setLength(0); return }
            stats.games++
            val keep = evaluate(headers, movetext, rule, stats)
            if (keep != null) { stats.kept++; emit(keep) }
            headers.clear(); movetext.setLength(0)
        }
        var line = reader.readLine()
        var sawMoves = false
        while (line != null) {
            if (line.length > 2 && line[0] == '[' && line[1].isLetter() && line.endsWith("]")) {
                if (sawMoves) { finish(true); sawMoves = false }
                val space = line.indexOf(' ')
                if (space > 1) headers[line.substring(1, space)] = line.substring(space + 1, line.length - 1).trim().removeSurrounding("\"")
            } else if (line.isNotBlank()) {
                movetext.append(line).append(' ')
                sawMoves = true
            }
            line = reader.readLine()
        }
        // A complete final game ends with its result token; anything else is a truncated tail.
        val tail = movetext.trim()
        finish(sawMoves && results.any { tail.endsWith(it) } )
    }

    private fun evaluate(headers: Map<String, String>, movetext: CharSequence, rule: FilterRule, stats: FilterStats): FilteredGame? {
        val result = headers["Result"]
        if (result !in results || headers["Termination"] == "Abandoned" || headers["Variant"]?.let { it != "Standard" } == true ||
            headers.containsKey("FEN")) { stats.rejectedOther++; return null }
        val seconds = estimatedSeconds(headers["TimeControl"])
        if (rule.minEstimatedSeconds > 0 && (seconds == null || seconds < rule.minEstimatedSeconds)) { stats.rejectedSpeed++; return null }
        val white = headers["WhiteElo"]?.toIntOrNull(); val black = headers["BlackElo"]?.toIntOrNull()
        if (white == null || black == null || white !in rule.minElo..rule.maxElo || black !in rule.minElo..rule.maxElo) {
            stats.rejectedRating++; return null
        }
        // Cheap prefix rejection before tokenizing the whole score.
        val head = sanTokens(movetext.substring(0, minOf(movetext.length, 400)), rule.prefix.size)
        if (head != rule.prefix) { stats.rejectedPrefix++; return null }
        val san = sanTokens(movetext.toString(), rule.maxPlies)
        val site = headers["Site"].orEmpty()
        val id = headers["GameURL"] ?: site.takeIf { it.startsWith("https://lichess.org/") } ?: "${headers["Event"]}|${headers["Round"]}|${headers["White"]}|${headers["Black"]}"
        return FilteredGame(id, result!!, white, black, speed(seconds), headers["Date"] ?: headers["UTCDate"].orEmpty(),
            headers["White"].orEmpty(), headers["Black"].orEmpty(), headers["Event"].orEmpty(), site, san)
    }

    /** Decompresses ordered zstd chunk files (a possibly truncated prefix) through the zstd CLI. */
    fun openChunks(chunks: List<File>): Pair<Process, BufferedReader> {
        require(chunks.isNotEmpty() && chunks.all { it.isFile })
        val process = ProcessBuilder("zstd", "-dc", "--no-progress").redirectError(ProcessBuilder.Redirect.DISCARD).start()
        Thread {
            process.outputStream.use { out -> for (chunk in chunks) chunk.inputStream().use { it.copyTo(out, 1 shl 20) } }
        }.apply { isDaemon = true; start() }
        return process to BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8), 1 shl 20)
    }
}
