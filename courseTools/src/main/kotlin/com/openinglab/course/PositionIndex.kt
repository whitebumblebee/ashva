// SPDX-License-Identifier: Apache-2.0
package com.openinglab.course

import com.openinglab.shared.chess.BoardPosition
import com.openinglab.shared.chess.parseSanAndPlay
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Results for one move from one normalized position (transpositions merged). */
class MoveStat(var games: Int = 0, var whiteWins: Int = 0, var draws: Int = 0, var blackWins: Int = 0, var ratingSum: Long = 0) {
    /** Score for the side that made the move, 0..1. */
    fun scoreFor(whiteMoved: Boolean): Double =
        if (games == 0) 0.5 else ((if (whiteMoved) whiteWins else blackWins) + 0.5 * draws) / games
    fun add(result: String, rating: Int) {
        games++; ratingSum += rating
        when (result) { "1-0" -> whiteWins++; "0-1" -> blackWins++; else -> draws++ }
    }
    fun merge(other: MoveStat) {
        games += other.games; whiteWins += other.whiteWins; draws += other.draws; blackWins += other.blackWins; ratingSum += other.ratingSum
    }
}

/**
 * Position → move statistics for one population (club or master). Every SAN token is legally replayed with the
 * shared rules; games with an illegal or ambiguous token are counted as rejected, never partially guessed.
 */
class PositionIndex(val population: String) {
    val positions = ConcurrentHashMap<String, MutableMap<String, MoveStat>>()
    var games = 0L; private set
    var rejected = 0L; private set
    val sources = mutableListOf<String>()

    fun movesAt(positionKey: String): Map<String, MoveStat> = positions[positionKey].orEmpty()
    fun total(positionKey: String): Int = movesAt(positionKey).values.sumOf { it.games }

    companion object {
        /** Builds an index from filtered TSV files, keeping only games that start with [prefix]. */
        fun build(population: String, files: List<File>, prefix: List<String>, maxPlies: Int, threads: Int = Runtime.getRuntime().availableProcessors()): PositionIndex {
            val index = PositionIndex(population)
            val rows = files.flatMap { file -> file.readLines().filter { it.isNotBlank() } }
            val rejected = java.util.concurrent.atomic.AtomicLong()
            val kept = java.util.concurrent.atomic.AtomicLong()
            val pool = java.util.concurrent.Executors.newFixedThreadPool(threads)
            try {
                val partials = rows.chunked(maxOf(1, rows.size / (threads * 4) + 1)).map { chunk ->
                    pool.submit<Map<String, MutableMap<String, MoveStat>>> {
                        val local = HashMap<String, MutableMap<String, MoveStat>>()
                        for (row in chunk) {
                            val game = FilteredGame.fromTsv(row)
                            if (game.san.size < prefix.size || game.san.subList(0, prefix.size) != prefix) continue
                            val rating = ((game.whiteElo ?: 0) + (game.blackElo ?: 0)) / 2
                            val updates = ArrayList<Pair<String, String>>()
                            var board = BoardPosition.starting()
                            val ok = runCatching {
                                for (token in game.san.take(maxPlies)) {
                                    val t = board.parseSanAndPlay(token)
                                    updates += board.positionKey to t.move.uci
                                    board = t.position
                                }
                            }.isSuccess
                            if (!ok) { rejected.incrementAndGet(); continue }
                            kept.incrementAndGet()
                            for ((key, uci) in updates) local.getOrPut(key) { HashMap() }.getOrPut(uci) { MoveStat() }.add(game.result, rating)
                        }
                        local
                    }
                }.map { it.get() }
                for (partial in partials) for ((key, moves) in partial) {
                    val target = index.positions.getOrPut(key) { HashMap() }
                    for ((uci, stat) in moves) target.getOrPut(uci) { MoveStat() }.merge(stat)
                }
            } finally { pool.shutdown() }
            index.games = kept.get(); index.rejected = rejected.get()
            index.sources += files.map { it.path }
            return index
        }
    }
}
