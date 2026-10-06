// SPDX-License-Identifier: Apache-2.0
package com.openinglab.course

import com.openinglab.shared.chess.BoardPosition
import com.openinglab.shared.course.DeepCourseCatalog
import com.openinglab.shared.model.ChessMove
import com.openinglab.shared.storage.contentSha256
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class GoldIdea(val chapter: String, val nodeId: String, val sanPath: String, val type: String, val san: String, val strengthCp: Int)

@Serializable
data class EvalResults(
    val ranOn: String,
    val puzzleSample: Int,
    val engineSolvesFirstMove: Double,
    val threatFindsNextSolverMove: Double,
    val checkerRejectsFalseThreats: Double,
    val checkerAcceptsTrueMistakes: Double,
    val goldIdeas: Int,
    val goldIdeaRecall: Double,
    val writerClaimPassRate: Double,
    val autoClaimPassRate: Double,
    val writerClaims: Int,
    val aiJudge: String,
    val notes: List<String>,
)

/**
 * Phase 4: measurement instead of human review. Puzzle checks measure the fact layer and the checker itself;
 * the engine-derived gold set measures whether shown text mentions each strong, engine-confirmed idea.
 */
class CourseEvals(private val p: CoursePipeline) {
    private val evalDir = File(p.dir, "eval")

    fun run() {
        evalDir.mkdirs()
        val puzzles = samplePuzzles(300)
        File(evalDir, "puzzles.json").writeText(json.encodeToString(kotlinx.serialization.builtins.ListSerializer(Puzzle.serializer()), puzzles))
        p.pool().use { pool ->
            val tools = EngineTools(pool, 16)
            val checker = ClaimChecker(tools)
            val jobs = java.util.concurrent.Executors.newFixedThreadPool(3)
            val outcomes = try {
                puzzles.map { pz -> jobs.submit(java.util.concurrent.Callable { evaluate(pz, tools, checker) }) }.map { it.get() }
            } finally { jobs.shutdown() }
            fun rate(sel: (PuzzleOutcome) -> Boolean?) = outcomes.mapNotNull(sel).let { if (it.isEmpty()) 0.0 else it.count { v -> v }.toDouble() / it.size }
            val gold = goldSet()
            File(evalDir, "gold.json").writeText(json.encodeToString(kotlinx.serialization.builtins.ListSerializer(GoldIdea.serializer()), gold))
            val pack = DeepCourseCatalog.parse(File(p.dir, "v${p.config.version}/course.json").readText())
            val texts = pack.chapters.flatMap { c -> c.nodes.map { it.id to (it.text + " " + it.title) } }.toMap()
            val recalled = gold.count { g -> texts[g.nodeId]?.contains(g.san.trimEnd('+', '#')) == true }
            val summaries = p.config.chapters.map { p.loadChecked(it.id).summary }
            val results = EvalResults(java.time.LocalDate.now().toString(), puzzles.size,
                rate { it.engineSolved }, rate { it.threatFound }, rate { it.falseThreatRejected }, rate { it.trueMistakeAccepted },
                gold.size, if (gold.isEmpty()) 0.0 else recalled.toDouble() / gold.size,
                summaries.sumOf { it.writerPassed }.toDouble() / summaries.sumOf { it.writerClaims }.coerceAtLeast(1),
                summaries.sumOf { it.autoPassed }.toDouble() / summaries.sumOf { it.autoClaims }.coerceAtLeast(1),
                summaries.sumOf { it.writerClaims },
                "not run: needs a second model through an owner-approved API (Phase 0 decision D1)",
                listOf("Puzzle metrics use depth 16; course facts use depth ${p.config.thresholds.depth}.",
                    "Gold ideas are engine-confirmed forcing threats, prevented moves (not counted after captures) and common mistakes with strength ≥ 200 cp (mistakes ≥ 150 cp); recall checks the shown node text mentions the move.", "threatFindsNextSolverMove counts puzzles where a null move is legal after the first solution move and the solution continues: does the probe pick the next solution move?"))
            File(evalDir, "results.json").writeText(json.encodeToString(EvalResults.serializer(), results) + "\n")
            println(json.encodeToString(EvalResults.serializer(), results))
            val missed = gold.filter { g -> texts[g.nodeId]?.contains(g.san.trimEnd('+', '#')) != true }
            File(evalDir, "gold-missed.json").writeText(json.encodeToString(kotlinx.serialization.builtins.ListSerializer(GoldIdea.serializer()), missed))
        }
    }

    @Serializable
    data class Puzzle(val id: String, val fen: String, val moves: List<String>, val rating: Int, val themes: List<String>)
    data class PuzzleOutcome(val engineSolved: Boolean, val threatFound: Boolean?, val falseThreatRejected: Boolean?, val trueMistakeAccepted: Boolean?)

    private val themes = setOf("fork", "pin", "skewer", "hangingPiece", "discoveredAttack", "mateIn1", "mateIn2", "trappedPiece")

    private fun samplePuzzles(n: Int): List<Puzzle> {
        val chunks = contiguousChunks(File(cacheDir, "raw/puzzles"))
        require(chunks.isNotEmpty()) { "Puzzle database not downloaded" }
        val (process, reader) = GameFilter.openChunks(chunks)
        val picked = java.util.TreeMap<String, Puzzle>()
        reader.useLines { lines ->
            lines.drop(1).forEach { line ->
                val f = line.split(',')
                if (f.size < 8) return@forEach
                val rating = f[3].toIntOrNull() ?: return@forEach
                val tags = f[7].split(' ')
                if (rating !in 1200..2200 || tags.none { it in themes }) return@forEach
                val key = contentSha256(f[0].encodeToByteArray())
                if (picked.size < n || key < picked.lastKey()) {
                    picked[key] = Puzzle(f[0], f[1], f[2].split(' '), rating, tags)
                    if (picked.size > n) picked.remove(picked.lastKey())
                }
            }
        }
        process.destroy()
        return picked.values.toList()
    }

    private fun evaluate(pz: Puzzle, tools: EngineTools, checker: ClaimChecker): PuzzleOutcome {
        val start = BoardPosition.fromFen(pz.fen)
        // Lines in EngineTools start from the standard position; puzzles start from a FEN, so query the analyser directly.
        val afterBlunder = start.apply(ChessMove.fromUci(pz.moves[0]))
        val a = tools.analyseFen(afterBlunder.toFen(), 16)
        val solved = a.best?.pv?.first() == pz.moves[1]
        val afterSolve = afterBlunder.apply(ChessMove.fromUci(pz.moves[1]))
        val threat = tools.threatFen(afterSolve.toFen())
        // Null-move probe fidelity: only where a pass is legal (opponent not in check) and the solution continues.
        val probeFen = tools.passed(afterSolve)
        val threatFound = if (pz.moves.size >= 4 && probeFen != null) tools.analyseFen(probeFen, 16).best?.pv?.first() == pz.moves[3] else null
        // A THREAT claim naming an arbitrary quiet legal move must be rejected by the threat probe.
        val quiet = afterSolve.let { b -> tools.passedBoard(b)?.legalMoves()?.map { it.uci }?.sorted()?.firstOrNull { it != threat?.moverThreat } }
        val falseRejected = quiet?.let { tools.threatFen(afterSolve.toFen())?.moverThreat != it }
        // The puzzle's first move is a real blunder: the MISTAKE check must accept it.
        val check = tools.compareFen(start.toFen(), pz.moves[0])
        val mistakeAccepted = if (start.legalMoves().size > 1) check >= 100 else null
        return PuzzleOutcome(solved, threatFound, falseRejected, mistakeAccepted)
    }

    private fun goldSet(): List<GoldIdea> = p.config.chapters.flatMap { chapter ->
        val tree = p.loadTree(chapter.id)
        val facts = p.loadFacts(chapter.id)
        tree.nodes.flatMap { node ->
            val f = facts[node.id] ?: return@flatMap emptyList()
            val path = p.sanPath(node)
            buildList {
                if (node.role == "TRAP" && f.lossCp >= 150 && f.pvAfter.isNotEmpty()) add(GoldIdea(chapter.id, node.id, path, "MISTAKE_REFUTATION", f.pvAfter.first(), f.lossCp))
                // Same teaching definitions as the course: threats must be forcing, and "prevents" is not counted
                // after a capture (where the opponent's recapture is forced anyway).
                val forcing = f.threat?.let { t -> 'x' in t || t.endsWith('+') || t.endsWith('#') || '=' in t } == true
                if (forcing && (f.threatGainCp ?: 0) >= 200) add(GoldIdea(chapter.id, node.id, path, "THREAT", f.threat!!, f.threatGainCp!!))
                if ('x' !in node.san) f.prevents.firstOrNull { it.dropCp >= 200 && !it.illegalNow }?.let { add(GoldIdea(chapter.id, node.id, path, "PREVENTS", it.san, it.dropCp)) }
            }
        }
    }.sortedByDescending { it.strengthCp }.take(150)
}
