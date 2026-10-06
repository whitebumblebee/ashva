// SPDX-License-Identifier: Apache-2.0
package com.openinglab.course

import com.openinglab.shared.chess.BoardPosition
import com.openinglab.shared.chess.parseSanAndPlay
import com.openinglab.shared.content.OpeningRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FullCourseTest {
    private fun sans(path: String) = path.split(' ')
    private fun line(path: List<String>): Line {
        var b = BoardPosition.starting()
        return Line(path.map { val t = b.parseSanAndPlay(it); b = t.position; t.move.uci })
    }
    private fun index(vararg routes: Pair<String, Int>): BandedIndex {
        val master = PositionIndex("master")
        for ((path, count) in routes) {
            var b = BoardPosition.starting()
            for (s in sans(path)) {
                val t = b.parseSanAndPlay(s)
                val stat = master.positions.getOrPut(b.positionKey) { linkedMapOf() }.getOrPut(t.move.uci) { MoveStat() }
                repeat(count) { stat.add("1-0", 2500) }; b = t.position
            }
        }
        return BandedIndex(mapOf(Band.GM_IM to master))
    }
    private fun record(path: String, name: String): OpeningRecord {
        val san = sans(path); val l = line(san)
        return OpeningRecord(name, "C65", name, "Ruy Lopez", l.moves, san, l.board.toFen(), l.board.positionKey, emptyList())
    }

    @Test fun secondMoveOrderTransposesAndOnlyTheFirstPositionIsExpanded() {
        val nodes = VariationMapBuilder(index(
            "e4 e5 Nf3 Nc6 Bb5 Nf6 O-O" to 12, "e4 e5 Nf3 Nf6 Bb5 Nc6 O-O" to 12), TaxonomyNames(emptyList()), MapRules())
            .build(sans("e4 e5 Nf3"))
        val key = line(sans("e4 e5 Nf3 Nc6 Bb5 Nf6")).board.positionKey
        val arrivals = nodes.filter { it.moves.size == 6 && Line(it.moves).board.positionKey == key }
        assertEquals(2, arrivals.size)
        val first = arrivals.single { it.transposesTo == null }
        val second = arrivals.single { it.transposesTo != null }
        assertEquals(first.id, second.transposesTo)
        assertEquals(listOf("O-O"), nodes.filter { it.parent == first.id }.map { it.san })
        assertTrue(nodes.none { it.parent == second.id })
        assertEquals(1, nodes.count { it.san == "O-O" })
    }

    @Test fun namedTaxonomyRoutesAreKeptWithoutMasterGames() {
        val names = TaxonomyNames(listOf(record("e4 e5 Nf3 Nc6 Bb5 a6", "Ruy Lopez: Morphy Defense")))
        val nodes = VariationMapBuilder(BandedIndex(emptyMap()), names, MapRules()).build(sans("e4 e5 Nf3"))
        assertEquals(sans("e4 e5 Nf3 Nc6 Bb5 a6"), nodes.map { it.san })
        assertEquals(listOf("named", "named", "named"), nodes.drop(3).map { it.source })
        assertTrue(nodes.all { it.masterGames == 0 })
        assertEquals("Ruy Lopez: Morphy Defense", nodes.last().name)
    }

    @Test fun sparseMasterPositionsFollowOnlyTheMostPlayedMove() {
        val nodes = VariationMapBuilder(index("e4 e5 Nf3 Nc6" to 11, "e4 e5 Nf3 Nf6" to 5, "e4 e5 Nf3 d6" to 3),
            TaxonomyNames(emptyList()), MapRules(minMasterPosition = 20)).build(sans("e4 e5 Nf3"))
        assertEquals(19, nodes[2].positionMasterGames)
        assertEquals(listOf("Nc6"), nodes.drop(3).map { it.san })
        val belowCount = VariationMapBuilder(index("e4 e5 Nf3 Nc6" to 2), TaxonomyNames(emptyList()), MapRules()).build(sans("e4 e5 Nf3"))
        assertEquals(3, belowCount.size)
    }

    @Test fun importantUnnamedLinesKeepTheInheritedNameAndMoveWithAMiddleDot() {
        val root = sans("e4 e5 Nf3 Nc6 Bb5 Nf6 O-O")
        val name = "Ruy Lopez: Berlin Defense, Main Line"
        val map = root.indices.map { i -> mapNode(root.take(i + 1), "approach").copy(
            name = if (i == 5) name else null, named = i == 5, positionMasterGames = 1000) } + listOf(
            mapNode(root + "Nxe4", "master", 800).copy(name = "$name, Berlin Wall"),
            mapNode(root + "Bc5", "master", 200).copy(name = null, named = false))
        val config = CourseConfig("tiny", 1, "Tiny", "BOTH", "test", "Synthetic", listOf(ChapterConfig("berlin", "Berlin", root)))
        val variations = TableOfContents.build(config, map).single().variations
        val unnamed = variations.single { !it.named }
        assertEquals("Berlin Defense, Main Line · 4...Bc5", unnamed.name)
        assertEquals((root + "Bc5").joinToString(" "), unnamed.path)
        assertEquals(8, unnamed.ply)
        assertEquals(200, unnamed.masterGames)
        assertEquals("Berlin Defense, Main Line, Berlin Wall", variations.single { it.named }.name)
        val unnamedMap = map.map { it.copy(name = null, named = false) }
        assertEquals("Berlin · 4...Bc5", TableOfContents.build(config, unnamedMap).single().variations.single { it.masterGames == 200 }.name)
    }

    private val closedRoot = sans("e4 e5 Nf3 Nc6 Bb5 a6 Ba4 Nf6 O-O Be7 Re1 b5 Bb3")
    private fun tocMap(routes: List<List<String>>, names: Map<List<String>, String>, important: List<List<String>>): List<MapNode> =
        routes.flatMap { path -> path.indices.map { path.take(it + 1) } }.distinct().map { path ->
            mapNode(path, if (path in important) "master" else "approach", if (path in important) 200 else 0)
                .copy(name = names[path], named = path in names, positionMasterGames = 1000)
        }
    private fun tocConfig(roots: List<List<String>>) = CourseConfig("tiny", 1, "Tiny", "BOTH", "test", "Synthetic",
        roots.mapIndexed { i, root -> ChapterConfig("chapter-$i", "Closed", root) })

    @Test fun collidingUnnamedLabelsExtendAcrossChaptersAndLeaveUniqueLabelsAlone() {
        val d6 = closedRoot + "d6"; val castled = closedRoot + "O-O"
        val first = d6 + "c3"; val second = castled + "c3"; val unique = d6 + "a3"
        val map = tocMap(listOf(first, second, unique, d6 + "d3", castled + "d3"),
            mapOf(closedRoot to "Ruy Lopez: Closed"), listOf(first, second, unique))
        val chapters = TableOfContents.build(tocConfig(listOf(d6, castled)), map)
        assertEquals(listOf(2, 1), chapters.map { it.variations.size })
        val labels = chapters.flatMap { it.variations }.associate { it.path to it.name }
        assertEquals("Closed · 7...d6 8.c3", labels[first.joinToString(" ")])
        assertEquals("Closed · 7...O-O 8.c3", labels[second.joinToString(" ")])
        assertEquals("Closed · 8.a3", labels[unique.joinToString(" ")])
        assertEquals(labels.size, labels.values.toSet().size)
    }

    @Test fun collisionsExtendOnePlyAtATimeAndUseNormalWhiteAndBlackMoveSequences() {
        val cases = listOf(
            listOf("O-O", "c3", "d6") to listOf("O-O", "a3", "d6"),
            listOf("d6", "c3", "O-O", "h3", "Re8", "d4") to listOf("O-O", "c3", "d6", "h3", "Re8", "d4"))
        val expected = listOf(
            listOf("Closed · 8.c3 d6", "Closed · 8.a3 d6"),
            listOf("Closed · 8...O-O 9.h3 Re8 10.d4", "Closed · 8...d6 9.h3 Re8 10.d4"))
        for ((i, routes) in cases.withIndex()) {
            val targets = listOf(closedRoot + routes.first, closedRoot + routes.second)
            val siblings = targets.map { it.dropLast(1) + if (i == 0) "h6" else "d3" }
            val map = tocMap(targets + siblings, mapOf(closedRoot to "Ruy Lopez: Closed"), targets)
            val labels = TableOfContents.build(tocConfig(listOf(closedRoot)), map).single().variations.associate { it.path to it.name }
            assertEquals(expected[i], targets.map { labels[it.joinToString(" ")] })
        }
    }

    @Test fun unnamedLabelsAlsoAvoidNamedVariationsInOtherChapters() {
        val d6 = closedRoot + "d6"; val castled = closedRoot + "O-O"
        val target = d6 + "c3"; val named = castled + "d3"
        val map = tocMap(listOf(target, d6 + "d3", named), mapOf(
            closedRoot to "Ruy Lopez: Closed", named to "Ruy Lopez: Closed · 8.c3"), listOf(target))
        val variations = TableOfContents.build(tocConfig(listOf(d6, castled)), map).flatMap { it.variations }
        assertEquals("Closed · 7...d6 8.c3", variations.single { !it.named }.name)
        assertEquals("Closed · 8.c3", variations.single { it.named }.name)

        // An extension can itself collide with a named label and must be checked again.
        val second = castled + "c3"
        val extendedCollision = tocMap(listOf(target, d6 + "d3", second, named), mapOf(
            closedRoot to "Ruy Lopez: Closed", named to "Ruy Lopez: Closed · 7...d6 8.c3"), listOf(target, second))
        val resolved = TableOfContents.build(tocConfig(listOf(d6, castled)), extendedCollision).flatMap { it.variations }
        assertEquals("Closed · 7...d6 8.c3 (2)", resolved.single { it.path == target.joinToString(" ") }.name)
        assertEquals("Closed · 7...O-O 8.c3", resolved.single { it.path == second.joinToString(" ") }.name)
        assertEquals(resolved.size, resolved.map { it.name }.toSet().size)
    }

    @Test fun exhaustedLabelsAndNamedDuplicatesUseSuffixesInPathOrder() {
        val roots = listOf("h6", "d6", "O-O").map { closedRoot + it }
        val targets = roots.map { it + "c3" }; val siblings = roots.map { it + "d3" }
        val map = tocMap(targets + siblings, roots.associateWith { "Ruy Lopez: Closed" }, targets)
        fun labels(nodes: List<MapNode>, chapterRoots: List<List<String>>) =
            TableOfContents.build(tocConfig(chapterRoots), nodes).flatMap { it.variations }.associate { it.path to it.name }
        val resolved = labels(map, roots)
        for ((i, root) in roots.sortedBy { it.joinToString(" ") }.withIndex()) {
            val suffix = if (i == 0) "" else " (${i + 1})"
            assertEquals("Closed$suffix", resolved[root.joinToString(" ")])
            assertEquals("Closed · 8.c3$suffix", resolved[(root + "c3").joinToString(" ")])
        }
        assertEquals(resolved.size, resolved.values.toSet().size)
        assertEquals(resolved, labels(map.reversed(), roots.reversed()))

        // A unique existing numeric label must also remain reserved.
        val reservedPath = closedRoot + listOf("O-O", "d3")
        val reserved = reservedPath.joinToString(" ")
        val reservedId = TreeBuilder.idFor(line(reservedPath).moves)
        val withReserved = labels(map.map { if (it.id == reservedId)
            it.copy(name = "Ruy Lopez: Closed · 8.c3 (2)", named = true) else it }, roots)
        assertEquals("Closed · 8.c3 (2)", withReserved[reserved])
        assertEquals("Closed · 8.c3 (3)", withReserved[(closedRoot + listOf("d6", "c3")).joinToString(" ")])
        assertEquals("Closed · 8.c3 (4)", withReserved[(closedRoot + listOf("h6", "c3")).joinToString(" ")])
        assertEquals(withReserved.size, withReserved.values.toSet().size)
    }

    @Test fun winningMoveFeaturesNormalizeCapturesPromotionsAndCastling() {
        assertEquals("Nf5", WinFeatures.feature("Nxf5"))
        assertEquals("exd5", WinFeatures.feature("exd5"))
        assertEquals("e8", WinFeatures.feature("e8=Q+"))
        assertEquals("O-O", WinFeatures.feature("O-O"))
        assertEquals("Nd2", WinFeatures.feature("Nbd2"))
        assertEquals("O-O-O", WinFeatures.feature("O-O-O+"))
    }

    private val anchor = IdeaAnchor("c", "King's Gambit", "anchor", sans("e4 e5"), line(sans("e4 e5")).board.positionKey, mapOf("2400–2599" to 40))
    private fun game(id: String, result: String, f4: Boolean, whiteElo: Int = 2500, blackElo: Int = whiteElo) =
        FilteredGame(id, result, whiteElo, blackElo, "classical", "2026.01.01", "White $id", "Black $id", "Synthetic", "",
            // Both legal lines continue 21 plies after the anchor, past the comparison minimum.
            sans(if (f4) "e4 e5 f4 exf4 Nf3 g5 h4 g4 Ne5 Nf6 d4 d6 Nd3 Nxe4 Bxf4 Bg7 Nc3 Nxc3 bxc3 O-O Be2 Re8 O-O"
                else "e4 e5 Nf3 Nc6 Bb5 a6 Ba4 Nf6 O-O Be7 Re1 b5 Bb3 d6 c3 O-O h3 Nb8 d4 Nbd7 Nbd2 Bb7 Nf1"))
    private fun ideas(wins: Int, inWins: Int, others: Int, inOthers: Int): VariationIdeas {
        val games = (0 until wins).map { game("w$it", "1-0", it < inWins) } +
            (0 until others).map { game("o$it", if (it % 2 == 0) "0-1" else "1/2-1/2", it < inOthers) }
        return WinningIdeas.fromGames(games, listOf(anchor)).getValue("c").single()
    }

    @Test fun decisiveGamesReportDistinctiveLaterMovesAndStrongWinningExamples() {
        val games = (0 until 20).map { game("w$it", "1-0", it < 16, 2400 + it * 10) } +
            (0 until 20).map { game("o$it", if (it % 2 == 0) "0-1" else "1/2-1/2", it < 2, 2900) } +
            game("uneven", "1-0", true, 3000, 2300)
        val v = WinningIdeas.fromGames(games, listOf(anchor)).getValue("c").single()
        val h4 = v.white.patterns.single { it.move == "h4" }
        assertEquals(17, h4.inWins); assertEquals(21, h4.wins); assertEquals(2, h4.inOthers); assertEquals(20, h4.others)
        assertEquals(41, v.games); assertEquals(21, v.whiteWins); assertEquals(10, v.draws); assertEquals(10, v.blackWins)
        assertEquals(anchor.bands, v.bands)
        assertEquals(listOf("White w19", "White w18"), v.white.examples.map { it.white })
        assertTrue(v.white.examples.all { it.result == "1-0" && it.anchorPly == 2 })
        assertEquals(line(games[19].san).moves, v.white.examples.first().uci)
        assertEquals(listOf("White o0", "White o2"), v.black.examples.map { it.white })
    }

    @Test fun winningPatternsRespectSampleShareAndTenPointThresholds() {
        fun hasH4(wins: Int, inWins: Int, others: Int, inOthers: Int) = ideas(wins, inWins, others, inOthers).white.patterns.any { it.move == "h4" }
        assertFalse(hasH4(7, 7, 10, 0))
        assertTrue(hasH4(8, 5, 10, 0))
        assertTrue(hasH4(20, 5, 20, 3)) // Exactly 25% share and +10 percentage points.
        assertFalse(hasH4(21, 5, 20, 0))
        assertTrue(hasH4(10, 6, 10, 5)) // 60% minus 50% must not round below the threshold.
        assertFalse(hasH4(10, 6, 100, 51))
        assertFalse(hasH4(10, 4, 10, 0)) // Existing minimum of five wins containing the feature.
    }

    @Test fun winningPatternsCountEachGameOnceWithinTheSideAndWindow() {
        val games = (0 until 8).map { game("w$it", "1-0", true) }
        val v = WinningIdeas.fromGames(games, listOf(anchor), window = 1).getValue("c").single()
        assertTrue(v.white.patterns.isEmpty())
        assertTrue(v.black.patterns.isEmpty())
        val wider = WinningIdeas.fromGames(games, listOf(anchor), window = 3).getValue("c").single()
        assertEquals(listOf("Nf3"), wider.white.patterns.map { it.move })
        assertEquals(8, wider.white.patterns.single().inWins)
        val repeated = (0 until 8).map { game("r$it", "1-0", false).copy(
            san = sans("e4 e5 Nf3 Nc6 Ng1 Nb8 Nf3 Nc6 Ng1 Nb8 Nf3 Nc6 Ng1 Nb8 Nf3 Nc6 Ng1 Nb8 Nf3 Nc6 Ng1 Nb8")) }
        val repeatedPattern = WinningIdeas.fromGames(repeated, listOf(anchor)).getValue("c").single().white.patterns.single { it.move == "Nf3" }
        assertEquals(8, repeatedPattern.inWins) // Later occurrences still count, once per game.
    }

    @Test fun winningPatternsExcludeEachSidesFirstMoveForEitherAnchorTurn() {
        for (result in listOf("1-0", "0-1")) {
            val games = (0 until 8).map { game("w$it", result, true) } +
                (0 until 8).map { game("o$it", "1/2-1/2", false) }
            val v = WinningIdeas.fromGames(games, listOf(anchor)).getValue("c").single()
            val patterns = if (result == "1-0") v.white.patterns else v.black.patterns
            assertFalse(patterns.any { it.move == if (result == "1-0") "f4" else "exf4" })
            assertTrue(patterns.any { it.move == if (result == "1-0") "h4" else "g5" })

            val oddAnchor = anchor.copy(path = sans("e4"), key = line(sans("e4")).board.positionKey)
            val odd = WinningIdeas.fromGames(games.take(8), listOf(oddAnchor)).getValue("c").single()
            val oddPatterns = if (result == "1-0") odd.white.patterns else odd.black.patterns
            assertFalse(oddPatterns.any { it.move == if (result == "1-0") "f4" else "e5" })
            assertTrue(oddPatterns.any { it.move == if (result == "1-0") "Nf3" else "exf4" })
        }

        // Other games' first moves must also be excluded: their initial Nf3 must not dilute the wins' later Nf3.
        val wins = (0 until 8).map { game("w$it", "1-0", true) }
        val others = (0 until 8).map { game("o$it", "1/2-1/2", false) }
        val pattern = WinningIdeas.fromGames(wins + others, listOf(anchor), window = 3).getValue("c").single().white.patterns.single()
        assertEquals(WinPattern("Nf3", 8, 8, 0, 8, san = "Nf3"), pattern)
    }

    @Test fun winningPatternsRetainTheMostFrequentSanWithAlphabeticalTies() {
        val continuation = "d3 Qd6 O-O Be6 Nbd2 Qc5 Nc4 Qb5 Be3 Qb4 a4 Be7 b3 a6"
        fun bishopGame(id: String, capture: Boolean, result: String = "1-0") = game(id, result, false).copy(san = sans(
            (if (capture) "e4 e5 Nf3 Nc6 Bb5 Nf6 Bxc6 dxc6 " else "e4 e5 Nf3 Nf6 Bb5 Ng8 Bc6 dxc6 ") + continuation))
        val captures = (0 until 5).map { bishopGame("capture$it", true) }
        val quiets = (0 until 3).map { bishopGame("quiet$it", false) }
        val losses = (0 until 30).map { bishopGame("loss$it", false, "0-1") } +
            (0 until 70).map { game("other$it", "0-1", true) }
        (captures + quiets + losses).forEach { assertEquals(it.san.size, line(it.san).moves.size) }
        fun pattern(games: List<FilteredGame>) = WinningIdeas.fromGames(games, listOf(anchor)).getValue("c").single().white.patterns.single { it.move == "Bc6" }
        // Only winning moves choose the representative SAN, even with many quiet moves in losses.
        assertEquals("Bxc6", pattern(captures + quiets).san)
        assertEquals("Bc6", pattern(captures.take(4) + quiets + bishopGame("quiet-tie", false)).san)
        assertEquals("Bxc6", pattern(captures + quiets + losses).san)

        val queenLine = sans("e4 e5 d4 d6 dxe5 dxe5 Qxd8+ Kxd8 Nf3 Nc6 Bc4 Be6 Bxe6 fxe6 O-O Nf6 Nc3 Bd6 Be3 Ke7 Rad1 Rhd8")
        assertEquals(queenLine.size, line(queenLine).moves.size)
        val queen = WinningIdeas.fromGames((0 until 8).map { game("queen$it", "1-0", false).copy(san = queenLine) }, listOf(anchor))
            .getValue("c").single().white.patterns.single { it.move == "Qd8" }
        assertEquals("Qxd8", queen.san)

        val mateLine = sans("Nf3 Nc6 Ng1 Nb8 Nf3 Nc6 Ng1 Nb8 Nf3 Nc6 Ng1 Nb8 Nf3 Nc6 Ng1 Nb8 e4 e5 Bc4 Nc6 Qh5 Nf6 Qxf7#")
        assertEquals(mateLine.size, line(mateLine).moves.size)
        val mateAnchor = anchor.copy(path = mateLine.take(2), key = line(mateLine.take(2)).board.positionKey)
        val mate = WinningIdeas.fromGames((0 until 8).map { game("mate$it", "1-0", false).copy(san = mateLine) }, listOf(mateAnchor))
            .getValue("c").single().white.patterns.single { it.move == "Qf7" }
        assertEquals("Qxf7", mate.san)
    }

    @Test fun oldWinningPatternJsonDefaultsSanAndNewJsonRetainsIt() {
        val old = json.decodeFromString(WinPattern.serializer(), """{"move":"Bc6","inWins":8,"wins":8,"inOthers":0,"others":8}""")
        assertEquals("", old.san)
        val updated = old.copy(san = "Bxc6")
        assertEquals(updated, json.decodeFromString(WinPattern.serializer(), json.encodeToString(WinPattern.serializer(), updated)))
    }

    @Test fun examplesPreferBoardGamesThenTheLowerRatingThenThirtyPlyContinuations() {
        val online = listOf("https://CHESS.COM/game", "https://LiChEsS.org/game", "https://CHESS24.com/game").mapIndexed { i, site ->
            game("online$i", "1-0", true, 2800).copy(site = site, event = "SCC_2022")
        }
        val inflated = listOf(game("inflated-white", "1-0", true, 3297, 2700), game("inflated-black", "1-0", true, 2700, 3274))
        val strong = game("strong", "1-0", true, 2700, 2600)
        val weaker = game("weaker", "1-0", false, 2890, 2599)
        val examples = WinningIdeas.fromGames(online + inflated + weaker + strong + strong, listOf(anchor)).getValue("c").single().white.examples
        assertEquals(listOf("White strong", "White weaker"), examples.map { it.white })

        val long = strong.copy(id = "long", white = "White long", san = strong.san + sans("Re6 Nf2 Kh8 Nh3 Re8 Nf2 Re6 Nh3 Re8 Nf2 Re6 Nh3 Re8"))
        assertEquals(long.san.size, line(long.san).moves.size)
        val atThirty = long.copy(id = "thirty", white = "White thirty", san = long.san.take(anchor.path.size + 30))
        val atTwentyNine = long.copy(id = "twenty-nine", white = "White twenty-nine", san = long.san.take(anchor.path.size + 29))
        val byLength = WinningIdeas.fromGames(listOf(atTwentyNine, atThirty, long), listOf(anchor)).getValue("c").single().white.examples
        assertEquals(listOf("White thirty", "White long"), byLength.map { it.white })

        // Rating comes before length, and 2900 is still within the board-game heuristic.
        val boundary = strong.copy(id = "boundary", white = "White boundary", whiteElo = 2900, blackElo = 2900)
        val byRating = WinningIdeas.fromGames(online + inflated + listOf(long, boundary), listOf(anchor)).getValue("c").single().white.examples
        assertEquals(listOf("White boundary", "White long"), byRating.map { it.white })
    }

    @Test fun variationExamplesAvoidEveryAncestorButAllowSiblingsAndRepeatOnlyAsFallback() {
        fun deeper(name: String, path: List<String>, chapter: String = "c") = anchor.copy(
            chapter = chapter, name = name, nodeId = name, path = path, key = line(path).board.positionKey)
        val child = deeper("child", sans("e4 e5 f4"))
        val grandchild = deeper("grandchild", sans("e4 e5 f4 exf4"), chapter = "other")
        val sibling = deeper("sibling", sans("e4 e5 Nf3"))
        val f4Wins = (0 until 6).map { game("f$it", "1-0", true, 2700 - it * 10) }
        val nf3Wins = (0 until 2).map { game("n$it", "1-0", false, 2300 - it * 10) }
        val games = f4Wins + nf3Wins + f4Wins.map { it.copy(id = "black-${it.id}", result = "0-1") }
        // Input order puts descendants before their ancestors; returned order must stay the same.
        val all = WinningIdeas.fromGames(games, listOf(grandchild, sibling, child, anchor))
        assertEquals(listOf(sibling.name, child.name, anchor.name), all.getValue("c").map { it.name })
        val byName = all.values.flatten().associateBy { it.name }
        for (white in listOf(true, false)) {
            fun examples(name: String) = byName.getValue(name).let { if (white) it.white else it.black }.examples.map { it.white }
            assertEquals(listOf("White f0", "White f1"), examples(anchor.name))
            assertEquals(listOf("White f2", "White f3"), examples(child.name))
            assertEquals(listOf("White f4", "White f5"), examples(grandchild.name))
        }
        assertEquals(listOf("White n0", "White n1"), byName.getValue(sibling.name).white.examples.map { it.white })

        val onlyOneFresh = WinningIdeas.fromGames(f4Wins.take(3), listOf(child, anchor)).getValue("c").associateBy { it.name }
        assertEquals(listOf("White f2"), onlyOneFresh.getValue(child.name).white.examples.map { it.white })
        val noFresh = WinningIdeas.fromGames(f4Wins.take(2), listOf(child, anchor)).getValue("c").associateBy { it.name }
        assertEquals(listOf("White f0", "White f1"), noFresh.getValue(child.name).white.examples.map { it.white })

        // A transposed anchor without an ancestor path may reuse those ancestors' games.
        val transposed = deeper("transposed", sans("f4 e5 e4"))
        val siblings = WinningIdeas.fromGames(f4Wins, listOf(child, transposed, anchor)).getValue("c").associateBy { it.name }
        assertEquals(listOf("White f0", "White f1"), siblings.getValue(transposed.name).white.examples.map { it.white })
        val alias = child.copy(name = "alias", nodeId = "alias")
        val aliases = WinningIdeas.fromGames(f4Wins, listOf(child, alias, anchor)).getValue("c").associateBy { it.name }
        assertEquals(aliases.getValue(child.name).white.examples, aliases.getValue(alias.name).white.examples)
    }

    @Test fun shortDrawsCannotMakeAnExtraWinningMoveDistinctiveButLongOthersStillCount() {
        val wins = (0 until 10).map { game("w$it", "1-0", true) }
        val longOthers = (0 until 10).map { game("o$it", if (it % 2 == 0) "0-1" else "1/2-1/2", true) }
        val shortDraws = (0 until 100).map {
            game("d$it", "1/2-1/2", true).let { g -> g.copy(san = g.san.take(anchor.path.size + MIN_CONTINUATION - 1)) }
        }
        fun extract(others: List<FilteredGame>, draws: List<FilteredGame> = emptyList()) =
            WinningIdeas.fromGames(wins + others + draws, listOf(anchor)).getValue("c").single()

        // The wins' final O-O is absent from the short draws, but equally common in the long others.
        val withShortDraws = extract(longOthers, shortDraws)
        assertTrue(withShortDraws.white.patterns.isEmpty())
        assertEquals(extract(longOthers).white.patterns, withShortDraws.white.patterns)
        assertEquals(120, withShortDraws.games)
        assertEquals(105, withShortDraws.draws) // Short draws still count in the overall result totals.

        // At exactly 20 plies after the anchor, games without that last move still enter the comparison.
        val othersWithoutLastMove = longOthers.mapIndexed { i, g ->
            if (i < 5) g else g.copy(san = g.san.take(anchor.path.size + MIN_CONTINUATION))
        }
        val pattern = extract(othersWithoutLastMove, shortDraws).white.patterns.single { it.move == "O-O" }
        assertEquals(WinPattern("O-O", 10, 10, 5, 10, san = "O-O"), pattern)
    }

    private fun mapNode(path: List<String>, source: String, master: Int = 0, club: Int = 0): MapNode {
        val moves = line(path).moves
        return MapNode(TreeBuilder.idFor(moves), moves.dropLast(1).takeIf { it.isNotEmpty() }?.let(TreeBuilder::idFor), moves,
            path.last(), "Ruy Lopez: Berlin Defense", true, master, club, emptyMap(), source, positionMasterGames = 30, positionClubGames = 100)
    }
    private fun builder(analyser: Analyser) = FullCourseBuilder(CourseConfig("tiny", 1, "Tiny", "BOTH", "test", "Synthetic", emptyList(),
        Thresholds(depth = 10)), EngineTools(analyser, 10), 1, {})

    @Test fun approachReachUsesClubSharesThenMasterFallbackAndPersistsThroughEngineLines() {
        val root = sans("e4 e5 Nf3 Nc6 Bb5")
        fun sampled(path: List<String>, master: Int = 0, club: Int = 0, positionMaster: Int = 0, positionClub: Int = 0) =
            mapNode(path, "master", master, club).copy(positionMasterGames = positionMaster, positionClubGames = positionClub)
        val approach = root.indices.map { mapNode(root.take(it + 1), "approach")
            .copy(positionMasterGames = 0, positionClubGames = 0) }
        val berlin = root + "Nf6"
        val morphy = root + "a6"
        val ba4 = morphy + "Ba4"
        val map = approach.dropLast(1) + approach.last().copy(
            clubGames = 300, masterGames = 200, positionClubGames = 200, positionMasterGames = 100) + listOf(
            sampled(berlin, master = 20, club = 100),
            sampled(morphy, master = 80, club = 60, positionMaster = 40, positionClub = 29),
            sampled(berlin + "O-O", master = 2, club = 60),
            sampled(berlin + "d3", master = 16, club = 30),
            sampled(berlin + "Nc3", master = 1),
            sampled(ba4, master = 30, club = 20),
            sampled(morphy + "Bxc6", master = 10, club = 9),
            sampled(ba4 + "Nf6", master = 24, club = 8),
            sampled(ba4 + "d6", master = 3, club = 4))
        val (nodes, _) = builder(FakeAnalyser(score = 200)).build(map)
        val byId = nodes.associateBy { it.id }
        fun reach(path: List<String>) = byId.getValue(TreeBuilder.idFor(line(path).moves)).reach
        val tolerance = 1e-9

        assertTrue(approach.all { byId.getValue(it.id).reach == 1.0 })
        // Available position counts win over the parent's own counts; club wins over master.
        assertEquals(100.0 / 200, reach(berlin), tolerance)
        assertEquals(60.0 / 200, reach(morphy), tolerance)
        // Missing position counts use the games that reached the parent (100 club / 20 master).
        assertEquals((100.0 / 200) * (60.0 / 100), reach(berlin + "O-O"), tolerance)
        assertEquals((100.0 / 200) * (30.0 / 100), reach(berlin + "d3"), tolerance)
        assertEquals((100.0 / 200) * (1.0 / 20), reach(berlin + "Nc3"), tolerance)
        // A measured 29-game club position is below threshold even though 60 club games reached it.
        assertEquals((60.0 / 200) * (30.0 / 40), reach(ba4), tolerance)
        assertEquals((60.0 / 200) * (10.0 / 40), reach(morphy + "Bxc6"), tolerance)
        // Sparse club data falls back to the parent's own master count on the next move.
        assertEquals((60.0 / 200) * (30.0 / 40) * (24.0 / 30), reach(ba4 + "Nf6"), tolerance)
        assertEquals((60.0 / 200) * (30.0 / 40) * (3.0 / 30), reach(ba4 + "d6"), tolerance)

        val children = nodes.groupBy { it.parent }
        val leaves = nodes.filter { children[it.id].isNullOrEmpty() }
        assertEquals(6, leaves.size)
        assertTrue(leaves.all { it.reach > 0.0 && it.stopReason == "verdict" })
        for (parent in nodes) assertTrue(children[parent.id].orEmpty().sumOf { it.reach } <= parent.reach + tolerance,
            "Child reaches exceed ${parent.san}'s reach")
        val engine = nodes.filter { it.role == "ENGINE" }
        assertEquals(leaves.size, engine.size)
        for (node in engine) assertEquals(byId.getValue(node.parent!!).reach, node.reach, tolerance)
    }

    @Test fun rolesPreferMasterMainLineAndClassifyClubLossAtOneHundredFifty() {
        val root = sans("e4 e5 Nf3 Nc6 Bb5 Nf6 O-O")
        val map = root.indices.map { mapNode(root.take(it + 1), "approach") } + listOf(
            mapNode(root + "Nxe4", "master", 20), mapNode(root + "Bc5", "master", 10),
            mapNode(root + "a6", "club", club = 45), mapNode(root + "h6", "club", club = 60))
        val (nodes, extras) = builder(FakeAnalyser(mapOf("a7a6" to 51, "h7h6" to 50), score = 200)).build(map)
        val replies = nodes.filter { it.moves.size == 8 }
        assertEquals(mapOf("Nxe4" to "MAIN", "Bc5" to "SIDE", "a6" to "DEVIATION", "h6" to "TRAP"), replies.associate { it.san to it.role })
        assertEquals(0, replies.single { it.san == "h6" }.sinceTrap)
        for (reply in replies) {
            val extension = nodes.filter { it.moves.size > reply.moves.size && it.moves.take(reply.moves.size) == reply.moves }
            val punish = reply.role == "TRAP"
            assertEquals(if (punish) 3 else 1, extension.size)
            assertTrue(extension.all { it.role == if (punish) "PUNISH" else "ENGINE" })
            assertTrue(extension.all { it.reach == reply.reach })
            assertTrue(extension.dropLast(1).all { it.verdict == null })
            val last = extension.last()
            assertNotNull(last.verdict); assertEquals("verdict", last.stopReason)
            assertTrue(nodes.none { it.parent == last.id })
            assertEquals("Ruy Lopez: Berlin Defense", extras.getValue(last.id).opening)
            if (punish) assertEquals(listOf(1, 2, 3), extension.map { it.sinceTrap })
        }
    }

    @Test fun engineContinuationWaitsForStableVerdictAndDoesNotExpandTranspositions() {
        val root = mapNode(sans("e4 e5"), "master", 20)
        val analyser = object : Analyser {
            override fun analyse(fen: String, moves: List<String>, depth: Int, multiPv: Int, searchMoves: List<String>): EngineResult =
                FakeAnalyser(score = if (moves.size < 4) 60 else 200).analyse(fen, moves, depth, multiPv, searchMoves)
        }
        val (nodes, _) = builder(analyser).build(listOf(root))
        assertEquals(listOf(2, 3, 4), nodes.map { it.moves.size })
        assertNull(nodes[1].verdict)
        assertNotNull(nodes.last().verdict)
        assertEquals("verdict", nodes.last().stopReason)
        val (transposed, extras) = builder(FakeAnalyser(score = 200)).build(listOf(root.copy(transposesTo = "other")))
        assertEquals(1, transposed.size)
        assertEquals("other", extras.getValue(root.id).transposesTo)
    }

    @Test fun winPlanNeedsEveryMoveFeatureInTheSelectedSidesPatterns() {
        val checker = ClaimChecker(EngineTools(FakeAnalyser(), 10))
        val idea = ideas(10, 8, 10, 0).copy(white = SideWins(10, listOf(WinPattern("f4", 8, 10, 0, 10), WinPattern("Nf5", 6, 10, 0, 10, san = "Nxf5")), emptyList()),
            black = SideWins(10, listOf(WinPattern("exd5", 8, 10, 0, 10)), emptyList()))
        val claim = Claim("WINPLAN", "White plays f4 and Nxf5.", side = "WHITE", moves = listOf("f4", "Nxf5"))
        assertTrue(checkWinPlan(claim, idea, true, checker, "e5").passed)
        assertTrue(checkWinPlan(claim.copy(text = "White plays f4 and Nf5.", moves = listOf("f4", "Nf5")), idea, true, checker, "e5").passed)
        assertFalse(checkWinPlan(claim.copy(moves = listOf("f4", "Nf5", "d4")), idea, true, checker, "e5").passed)
        assertFalse(checkWinPlan(claim, idea, false, checker, "e5").passed)
        assertTrue(checkWinPlan(Claim("WINPLAN", "Black plays exd5.", moves = listOf("exd5")), idea, false, checker, "e5").passed)
        assertFalse(checkWinPlan(claim.copy(text = "White plays f4 and d4."), idea, true, checker, "e5").passed)
        assertFalse(checkWinPlan(claim.copy(text = "White seeks activity.", moves = emptyList()), idea, true, checker, "e5").passed)
        assertFalse(checkWinPlan(claim, null, true, checker, "e5").passed)
    }
}
