// SPDX-License-Identifier: Apache-2.0
package com.openinglab.course

import com.openinglab.shared.chess.BoardPosition
import com.openinglab.shared.chess.parseSanAndPlay
import com.openinglab.shared.model.ChessMove
import com.openinglab.shared.model.PieceColor
import java.io.BufferedReader
import java.io.File
import java.io.StringReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Deterministic stand-in engine: every move scores [score] unless listed in [scores] (side-to-move centipawns). */
class FakeAnalyser(private val scores: Map<String, Int> = emptyMap(), private val score: Int = 0) : Analyser {
    override fun analyse(fen: String, moves: List<String>, depth: Int, multiPv: Int, searchMoves: List<String>): EngineResult {
        var board = BoardPosition.fromFen(fen)
        moves.forEach { board = board.apply(ChessMove.fromUci(it)) }
        val legal = board.legalMoves().map { it.uci }.filter { searchMoves.isEmpty() || it in searchMoves }
        val ranked = legal.sortedWith(compareByDescending<String> { scores[it] ?: score }.thenBy { it })
        return EngineResult(ranked.take(multiPv).mapIndexed { i, uci -> EngineLine(i + 1, depth, scores[uci] ?: score, null, listOf(uci)) }, depth)
    }
}

class CourseToolsTest {
    private fun uci(vararg san: String): List<String> {
        var b = BoardPosition.starting()
        return san.map { val t = b.parseSanAndPlay(it); b = t.position; t.move.uci }
    }

    @Test fun sanTokensDropCommentsClocksVariationsNagsAndResults() {
        val text = "1. e4 { [%clk 0:10:00] } 1... e5 { [%clk 0:10:00] } 2. Nf3 \$1 (2. Nc3 Nf6) 2... Nc6 ; rest\n3. Bb5 a6 1-0"
        assertEquals(listOf("e4", "e5", "Nf3", "Nc6", "Bb5", "a6"), GameFilter.sanTokens(text))
        assertEquals(listOf("e4", "e5"), GameFilter.sanTokens(text, 2))
        assertEquals("rapid", GameFilter.speed(GameFilter.estimatedSeconds("600+0")))
        assertEquals("blitz", GameFilter.speed(GameFilter.estimatedSeconds("180+2")))
    }

    @Test fun filterKeepsOnlyMatchingCompleteGamesAndCountsEveryRejection() {
        fun game(tc: String, w: Int, b: Int, moves: String, result: String = "1-0", extra: String = "") =
            "[Event \"Rated game\"]\n[Site \"https://lichess.org/x$w$b\"]\n[Result \"$result\"]\n[WhiteElo \"$w\"]\n[BlackElo \"$b\"]\n[TimeControl \"$tc\"]\n$extra\n$moves $result\n\n"
        val pgn = game("600+0", 1800, 1900, "1. e4 e5 2. Nf3 Nc6 3. Bb5 Nf6") +
            game("60+0", 1800, 1900, "1. e4 e5 2. Nf3 Nc6 3. Bb5 a6") +
            game("600+0", 2500, 1900, "1. e4 e5 2. Nf3 Nc6 3. Bb5 a6") +
            game("600+0", 1800, 1900, "1. d4 d5") +
            game("600+0", 1800, 1900, "1. e4 e5 2. Nf3 Nc6 3. Bb5", extra = "[Termination \"Abandoned\"]") +
            "[Event \"Truncated\"]\n[Result \"1-0\"]\n[WhiteElo \"1800\"]\n[BlackElo \"1800\"]\n[TimeControl \"600+0\"]\n\n1. e4 e5 2. Nf3 Nc6 3. Bb5 a"
        val kept = mutableListOf<FilteredGame>()
        val stats = FilterStats()
        GameFilter.filter(BufferedReader(StringReader(pgn)), FilterRule("t", GameFilter.RUY_PREFIX, 1600, 2200, 300, 80), stats) { kept += it }
        assertEquals(1, kept.size)
        assertEquals(listOf("e4", "e5", "Nf3", "Nc6", "Bb5", "Nf6"), kept.single().san)
        assertEquals(5, stats.games); assertEquals(1, stats.rejectedSpeed); assertEquals(1, stats.rejectedRating)
        assertEquals(1, stats.rejectedPrefix); assertEquals(1, stats.rejectedOther); assertTrue(stats.truncatedTail)
        assertEquals(kept.single(), FilteredGame.fromTsv(kept.single().toTsv()))
    }

    @Test fun indexMergesTranspositionsAndRejectsIllegalScores() {
        val dir = kotlin.io.path.createTempDirectory("course-index").toFile()
        val file = File(dir, "games.tsv")
        fun row(id: String, result: String, san: String) = FilteredGame(id, result, 1800, 1800, "rapid", "", "", "", "", "", san.split(' ')).toTsv()
        file.writeText(listOf(
            row("1", "1-0", "e4 e5 Nf3 Nc6 Bb5 Nf6 O-O"), row("2", "0-1", "e4 e5 Nf3 Nc6 Bb5 Nf6 d3"),
            row("3", "1/2-1/2", "e4 e5 Nf3 Nf6 Bb5 Nc6 O-O"), // transposes into the same position before O-O
            row("4", "1-0", "e4 e5 Nf3 Nc6 Bb5 Nf6 Ke3"), // illegal king move: rejected, not partially counted
        ).joinToString("\n"))
        val index = PositionIndex.build("club", listOf(file), listOf("e4", "e5", "Nf3"), 20, threads = 2)
        assertEquals(3, index.games); assertEquals(1, index.rejected)
        var b = BoardPosition.starting(); listOf("e4", "e5", "Nf3", "Nc6", "Bb5", "Nf6").forEach { b = b.parseSanAndPlay(it).position }
        val moves = index.movesAt(b.positionKey)
        assertEquals(2, moves.getValue("e1g1").games)
        assertEquals(0.75, moves.getValue("e1g1").scoreFor(true))
        assertEquals(1, moves.getValue("d2d3").games)
        dir.deleteRecursively()
    }

    @Test fun claimTextMayOnlyMentionMovesBackedByItsCheckedPayload() {
        val checker = ClaimChecker(EngineTools(FakeAnalyser(), 10))
        assertTrue(checker.unsupportedTokens(Claim("PLAN", "White plays d4 and Nbd2.", side = "WHITE", moves = listOf("d4", "Nbd2")), "h3").isEmpty())
        assertEquals(listOf("Bg4"), checker.unsupportedTokens(Claim("PLAN", "White stops ...Bg4 and plays d4.", side = "WHITE", moves = listOf("d4")), "h3"))
        // A general idea may not smuggle in concrete moves or squares.
        assertEquals(listOf("e5"), checker.unsupportedTokens(Claim("IDEA", "Black wants to hit e5."), "h3"))
        assertTrue(checker.unsupportedTokens(Claim("IDEA", "White keeps the bishop pair and develops."), "h3").isEmpty())
    }

    @Test fun structureFactsAndPlanSequencesAreCheckedOnTheBoard() {
        val line = Line(uci("e4", "e5", "Nf3", "Nc6", "Bb5", "a6", "Bxc6", "dxc6"))
        val b = line.board
        assertTrue(ClaimChecker.structureFact(b, "doubled:black:c"))
        assertTrue(ClaimChecker.structureFact(b, "bishop-pair:black"))
        assertTrue(ClaimChecker.structureFact(b, "halfopen:black:d"))
        assertFalse(ClaimChecker.structureFact(b, "majority:white:kingside")) // 4 v 4 until the d-pawns are exchanged
        assertFalse(ClaimChecker.structureFact(b, "queens-off"))
        assertTrue(ClaimChecker.structureFact(b, "piece:white:N:f3"))
        val checker = ClaimChecker(EngineTools(FakeAnalyser(), 10))
        val continuation = uci("e4", "e5", "Nf3", "Nc6", "Bb5", "a6", "Bxc6", "dxc6", "O-O", "f6", "d4", "exd4", "Nxd4").drop(8)
        assertTrue(checker.inSequence(line, continuation, listOf("O-O", "Nxd4"), PieceColor.WHITE))
        assertFalse(checker.inSequence(line, continuation, listOf("Nxd4", "O-O"), PieceColor.WHITE))
        assertFalse(checker.inSequence(line, continuation, listOf("f6"), PieceColor.WHITE))
    }

    @Test fun mistakeClaimsNeedARealEngineLossAndTheEngineRefutation() {
        val before = Line(uci("e4", "e5", "Nf3", "Nc6", "Bb5", "Nf6", "O-O"))
        // Fake engine: ...a6 loses 180 cp for Black; after it, Bxc6 is White's best.
        val tools = EngineTools(FakeAnalyser(mapOf("a7a6" to -180, "b5c6" to 200)), 10)
        val checker = ClaimChecker(tools)
        val node = TreeNode("n", null, before.moves + "a7a6", "a7a6", "a6", "TRAP", 0.1, 30, 0.3, 0, null)
        val ctx = ClaimContext(before, node, emptyList(), PositionIndex("club"), PositionIndex("master"))
        val ok = checker.check(Claim("MISTAKE", "a6 is a mistake: Bxc6 wins material.", moves = listOf("a6", "Bxc6"), from = "before"), ctx)
        assertTrue(ok.passed, ok.reason)
        val wrongRefutation = checker.check(Claim("MISTAKE", "a6 is a mistake: c3 punishes it.", moves = listOf("a6", "c3"), from = "before"), ctx)
        assertFalse(wrongRefutation.passed)
        val notAMistake = checker.check(Claim("MISTAKE", "Be7 is a mistake.", moves = listOf("Be7"), from = "before"), ctx)
        assertFalse(notAMistake.passed)
    }

    @Test fun treeIncludesCommonRepliesAndPunishesAdvantageHandingMistakes() {
        val dir = kotlin.io.path.createTempDirectory("course-tree").toFile()
        val games = mutableListOf<String>()
        repeat(40) { games += FilteredGame("a$it", "1-0", 1800, 1800, "rapid", "", "", "", "", "", "e4 e5 Nf3 Nc6 Bb5 Nf6 O-O Nxe4".split(' ')).toTsv() }
        repeat(30) { games += FilteredGame("b$it", "1-0", 1800, 1800, "rapid", "", "", "", "", "", "e4 e5 Nf3 Nc6 Bb5 Nf6 O-O Bc5".split(' ')).toTsv() }
        repeat(25) { games += FilteredGame("c$it", "1-0", 1800, 1800, "rapid", "", "", "", "", "", "e4 e5 Nf3 Nc6 Bb5 Nf6 O-O a6".split(' ')).toTsv() }
        repeat(1) { games += FilteredGame("d$it", "1-0", 1800, 1800, "rapid", "", "", "", "", "", "e4 e5 Nf3 Nc6 Bb5 Nf6 O-O h6".split(' ')).toTsv() }
        val file = File(dir, "club.tsv").apply { writeText(games.joinToString("\n")) }
        val club = PositionIndex.build("club", listOf(file), emptyList(), 20, 2)
        // Engine: after 4...a6 White is +2.0; everything else is level.
        val engine = object : Analyser {
            override fun analyse(fen: String, moves: List<String>, depth: Int, multiPv: Int, searchMoves: List<String>): EngineResult {
                var b = BoardPosition.fromFen(fen); moves.forEach { b = b.apply(ChessMove.fromUci(it)) }
                val afterA6 = moves.size == 8 && moves.last() == "a7a6"
                val legal = b.legalMoves().map { it.uci }.filter { searchMoves.isEmpty() || it in searchMoves }.sorted()
                val score = if (afterA6) 200 else 0
                return EngineResult(legal.take(multiPv).mapIndexed { i, u -> EngineLine(i + 1, depth, score, null, listOf(u)) }, depth)
            }
        }
        val config = CourseConfig("t", 1, "T", "WHITE", "test", "s", listOf(ChapterConfig("berlin", "Berlin", "e4 e5 Nf3 Nc6 Bb5 Nf6".split(' '))),
            Thresholds(maxChapterPlies = 4, engineTop = 0, depth = 10), mapOf("e2e4 e7e5 g1f3 b8c6 f1b5 g8f6" to "e1g1"))
        val tree = TreeBuilder(EngineTools(engine, 10), club, PositionIndex("master"), config, 2).build(config.chapters.single())
        val replies = tree.children(tree.nodes.first { it.san == "O-O" }.id)
        assertEquals(setOf("Nxe4", "Bc5", "a6"), replies.map { it.san }.toSet()) // h6: 1 game, below the share/count bar
        assertEquals("MAIN", replies.first { it.san == "Nxe4" }.role)
        assertEquals("TRAP", replies.first { it.san == "a6" }.role)
        assertTrue(tree.children(replies.first { it.san == "a6" }.id).all { it.role == "PUNISH" })
        assertTrue(tree.leaves.all { it.verdict != null })
        val report = CoverageReport.of(tree)
        assertEquals(1, report.traps)
        dir.deleteRecursively()
    }

    @Test fun nullMoveProbeIsUnavailableInCheck() {
        val tools = EngineTools(FakeAnalyser(), 10)
        val check = Line(uci("e4", "f6", "d4", "g5", "Qh5"))
        assertNull(tools.passed(check.board))
        assertTrue(tools.passed(Line(uci("e4")).board)!!.contains(" w "))
    }
}
