// SPDX-License-Identifier: Apache-2.0
package com.openinglab.course

import com.openinglab.shared.chess.BoardPosition
import com.openinglab.shared.tactics.Puzzle
import java.io.StringReader
import kotlin.test.*

class TacticsGeneratorTest {
    private val header = "PuzzleId,FEN,Moves,Rating,RatingDeviation,Popularity,NbPlays,Themes,GameUrl,OpeningTags,DailyDate\n"
    private fun row(id: String, rating: Int = 1200, rd: Int = 80, popularity: Int = 90, plays: Int = 1000,
        moves: String = "e2e4 e7e5", themes: String = "fork", opening: String = "") =
        "$id,${BoardPosition.START_FEN},$moves,$rating,$rd,$popularity,$plays,$themes,https://lichess.org/example,$opening,\n"
    private fun puzzle(id: String, rating: Int, themes: List<String> = listOf("fork"), opening: String? = null) =
        Puzzle(id, BoardPosition.START_FEN, listOf("e2e4", "e7e5"), rating, themes, opening)

    @Test fun qualityBoundariesLegalReplayAndQuotedCsv() {
        val stats = PuzzleCsvStats()
        val text = header + row("ok") + row("rd", rd = 81) + row("popular", popularity = 89) + row("plays", plays = 999) +
            row("illegal", moves = "e2e5 e7e5") + row("tooLong", moves = "e2e4 e7e5 ".repeat(7).trim()) +
            row("quoted", themes = "\"fork pin\"", opening = "Ruy_Lopez Ruy_Lopez_Berlin_Defense")
        val result = TacticsGenerator.readCsv(StringReader(text), stats)
        assertEquals(listOf("ok", "quoted"), result.map { it.id })
        assertEquals(4, stats.qualityRejected); assertEquals(1, stats.illegalRejected)
        assertEquals(listOf("fork", "pin"), result.last().themes)
        assertEquals("Ruy_Lopez", result.last().opening)
    }
    @Test fun partialFinalRowIsIgnoredEvenWhenItLooksLikeACompleteRow() {
        val stats = PuzzleCsvStats()
        assertEquals(listOf("ok"), TacticsGenerator.readCsv(StringReader(header + row("ok") + row("cut").trimEnd()), stats).map { it.id })
        assertTrue(stats.partialTail); assertEquals(1, stats.rows)
        assertFails { TacticsGenerator.readCsv(StringReader("wrong header\n")) }
    }
    @Test fun compositionIsDeterministicDiverseAndHasAnAdditionalPool() {
        val puzzles = (0 until 80).map { Puzzle("p$it", BoardPosition.START_FEN, listOf("e2e4", "e7e5"),
            1100 + it * 10, listOf(if (it % 2 == 0) "fork" else "pin"), if (it % 3 == 0) "Ruy_Lopez" else null) }
        val rules = listOf(TacticsSetRule("easy", "Easy", 10, 1100..1900, "WOODPECKER"),
            TacticsSetRule("forks", "Forks", 10, 1100..1900, "THEME") { "fork" in it.themes },
            TacticsSetRule("ruy", "Ruy", 10, 1100..1900, "OPENING") { it.opening == "Ruy_Lopez" })
        val a = TacticsGenerator.compose(puzzles, rules, 20)
        assertEquals(a, TacticsGenerator.compose(puzzles.reversed(), rules, 20))
        assertNotEquals(a, TacticsGenerator.compose(puzzles, rules, 20, seed = 99))
        assertEquals(20, a.puzzles.size - a.sets.flatMap { it.puzzleIds }.distinct().size)
        assertTrue(a.sets[1].puzzleIds.all { "fork" in a.byId.getValue(it).themes })
        assertTrue(a.sets[2].puzzleIds.all { a.byId.getValue(it).opening == "Ruy_Lopez" })
        assertEquals(a.sets[1].puzzleIds.map { a.byId.getValue(it).rating }.sorted(), a.sets[1].puzzleIds.map { a.byId.getValue(it).rating })
        val diverse = TacticsGenerator.diverse(puzzles, 10)
        diverse.chunked(2).forEach { assertEquals(setOf("fork", "pin"), it.map { p -> p.themes.single() }.toSet()); assertTrue(it[0].rating <= it[1].rating) }
        assertFails { TacticsGenerator.compose(puzzles, listOf(rules.first().copy(size = 100)), 0) }
    }
    @Test fun everyFixedSetSpansItsRangeEvenWithAnEasyHeavyPool() {
        val themes = listOf("fork", "pin", "discoveredAttack", "mateIn2", "mateIn3", "backRankMate",
            "sacrifice", "endgame", "promotion", "defensiveMove")
        val puzzles = (900..2500 step 25).flatMap { rating ->
            (0 until 64).map { puzzle("p$rating-$it", rating, themes, "Ruy_Lopez_Berlin_Defense") }
        } + (0 until 600).map { puzzle("extra$it", 1100, themes, "Ruy_Lopez") }
        val pack = TacticsGenerator.compose(puzzles, poolSize = 80)
        for ((rule, set) in TacticsGenerator.rules.zip(pack.sets)) {
            val picked = set.puzzleIds.map { pack.byId.getValue(it) }
            val ratings = picked.map { it.rating }
            assertEquals(rule.size, picked.size, rule.id)
            assertTrue(picked.all { it.rating in rule.range && rule.matches(it) }, rule.id)
            assertEquals(ratings.sorted(), ratings, rule.id)
            val width = (rule.range.last - rule.range.first) / 5
            val counts = (0 until 5).map { bucket ->
                val low = rule.range.first + bucket * width
                val high = if (bucket == 4) rule.range.last else low + width - 1
                ratings.count { it in low..high }
            }
            assertTrue(counts.all { it >= rule.size * 0.15 }, "${rule.id}: $counts")
            assertTrue(counts.max() - counts.min() <= 1, "${rule.id}: $counts")
            assertTrue(ratings[ratings.size / 2] in rule.range.first + 2 * width..rule.range.first + 3 * width, rule.id)
        }
        assertEquals(80, pack.puzzles.size - pack.sets.flatMap { it.puzzleIds }.distinct().size)
    }
    @Test fun ratingBucketsRetainMotifRoundRobinWithUnequalThemePopulations() {
        val puzzles = (0 until 5).flatMap { bucket ->
            val low = 1100 + bucket * 200
            (0 until 160).map { puzzle("fork$bucket-$it", low + it % 40) } +
                (0 until 20).map { puzzle("pin$bucket-$it", low + 150 + it, listOf("pin")) }
        }
        val rule = TacticsSetRule("mixed", "Mixed", 100, 1100..2100, "THEME")
        val pack = TacticsGenerator.compose(puzzles, listOf(rule), 0)
        val picked = pack.sets.single().puzzleIds.map { pack.byId.getValue(it) }
        for (bucket in 0 until 5) {
            val band = picked.filter { it.rating in 1100 + bucket * 200 until 1300 + bucket * 200 }
            assertEquals(20, band.size)
            assertEquals(mapOf("fork" to 10, "pin" to 10), band.groupingBy { it.themes.single() }.eachCount())
        }
    }
    @Test fun seededSelectionAlsoSpreadsInsideSingleMotifBuckets() {
        val puzzles = (1100 until 2100).map { puzzle("p$it", it) }
        val rule = TacticsSetRule("forks", "Forks", 100, 1100..2100, "THEME")
        val pack = TacticsGenerator.compose(puzzles, listOf(rule), 0)
        val ratings = pack.sets.single().puzzleIds.map { pack.byId.getValue(it).rating }
        assertTrue(ratings.count { (it - 1100) % 200 >= 100 } in 35..65, "Within-band upper halves: $ratings")
        assertEquals(pack, TacticsGenerator.compose(puzzles.reversed(), listOf(rule), 0))
        assertNotEquals(pack.sets, TacticsGenerator.compose(puzzles, listOf(rule), 0, seed = 99).sets)
    }
    @Test fun shortAndEmptyBucketsBorrowNearestSurplusAfterReservingOtherShares() {
        val capacities = listOf(40, 4, 40, 0, 60)
        val puzzles = capacities.flatMapIndexed { bucket, count ->
            (0 until count).map { puzzle("p$bucket-$it", 1100 + bucket * 200 + it) }
        }
        val rule = TacticsSetRule("forks", "Forks", 100, 1100..2100, "THEME")
        val pack = TacticsGenerator.compose(puzzles, listOf(rule), 0)
        val picked = pack.sets.single().puzzleIds.map { pack.byId.getValue(it) }
        assertEquals(listOf(36, 4, 40, 0, 20), (0 until 5).map { bucket ->
            picked.count { it.rating in 1100 + bucket * 200 until 1300 + bucket * 200 }
        })
        assertEquals(100, picked.map { it.id }.distinct().size)
        assertEquals(picked.map { it.rating }.sorted(), picked.map { it.rating })
        assertEquals(pack, TacticsGenerator.compose(puzzles.reversed(), listOf(rule), 0))
    }
    @Test fun sparseRangesAndInclusiveEndpointsStillFillWithoutDuplicates() {
        val rule = TacticsSetRule("edges", "Edges", 40, 1100..2100, "THEME")
        val eligible = (0 until 20).flatMap { listOf(puzzle("low$it", 1100), puzzle("high$it", 2100)) }
        val puzzles = eligible + listOf(puzzle("outside-low", 1099), puzzle("outside-high", 2101))
        val pack = TacticsGenerator.compose(puzzles, listOf(rule), 0)
        assertEquals(eligible.map { it.id }.toSet(), pack.sets.single().puzzleIds.toSet())
        assertFailsWith<IllegalArgumentException> { TacticsGenerator.compose(puzzles, listOf(rule.copy(size = 41)), 0) }
        val sameRating = TacticsGenerator.compose(eligible.take(1), listOf(rule.copy(size = 1, range = 1100..1100)), 0)
        assertEquals(listOf("low0"), sameRating.sets.single().puzzleIds)
    }
    @Test fun fixedSetContractMatchesTheRequestedCountsAndFilters() {
        val rules = TacticsGenerator.rules
        assertEquals(listOf(222, 762, 144), rules.take(3).map { it.size })
        assertEquals(listOf(1000..1500, 1500..2000, 2000..2500), rules.take(3).map { it.range })
        assertEquals(9, rules.count { it.category == "THEME" })
        assertTrue(rules.filter { it.category == "THEME" }.all { it.size == 100 && it.range == 1100..2100 })
        assertEquals(150, rules.last().size)
        assertEquals(900..2400, rules.last().range)
        val endgame = rules.single { it.id == "endgame-tactics" }
        val p = Puzzle("p", BoardPosition.START_FEN, listOf("e2e4", "e7e5"), 1200, listOf("endgame"))
        assertFalse(endgame.matches(p)); assertTrue(endgame.matches(p.copy(themes = listOf("endgame", "promotion"))))
    }
}
