// SPDX-License-Identifier: Apache-2.0
package com.openinglab.course

import com.openinglab.shared.chess.sanAndPlay
import java.io.File

/** WINPLAN: every move named must be one of that side's statistically distinctive winning moves for the variation. */
internal fun checkWinPlan(claim: Claim, idea: VariationIdeas?, white: Boolean, checker: ClaimChecker, nodeSan: String): CheckedClaim {
    val unsupported = checker.unsupportedTokens(claim, nodeSan)
    if (unsupported.isNotEmpty()) return CheckedClaim(claim, false, "", "text mentions unsupported moves/squares: $unsupported", false)
    val side = (if (white) idea?.white else idea?.black) ?: return CheckedClaim(claim, false, "", "no win statistics for this variation", false)
    val features = side.patterns.map { it.move }.toSet()
    val missing = claim.moves.filter { m -> WinFeatures.feature(m) !in features }
    return if (claim.moves.isNotEmpty() && missing.isEmpty()) CheckedClaim(claim, true, "game statistics", "", false)
    else CheckedClaim(claim, false, "", "not among the winners' distinctive moves: $missing", false)
}

/** File layout for one course: inputs/evidence under content/courses/<id>/, heavy caches under .course-cache/. */
class CoursePipeline(val courseId: String) {
    init { require(courseId.matches(Regex("[a-z0-9][a-z0-9-]{0,63}"))) }
    val dir = File("content/courses/$courseId")
    val config: CourseConfig = json.decodeFromString(CourseConfig.serializer(), File(dir, "course.config.json").readText())
    fun treeFile(chapter: String) = File(dir, "tree/$chapter.json")
    fun factsFile(chapter: String) = File(dir, "facts/$chapter.json")
    fun claimsFile(chapter: String) = File(dir, "claims/$chapter.json")
    fun checkedFile(chapter: String) = File(dir, "checked/$chapter.json")

    private val indexCache = HashMap<String, Pair<PositionIndex, PositionIndex>>()

    /** Club/master indexes restricted to games that reach the chapter root (keeps memory bounded). */
    fun indexes(chapter: ChapterConfig): Pair<PositionIndex, PositionIndex> = indexCache.getOrPut(chapter.id) {
        require(clubFile.isFile && masterFile.isFile) { "Run filter-club and filter-master first" }
        val prefix = chapter.rootSan
        PositionIndex.build("club", listOf(clubFile), prefix, 80) to PositionIndex.build("master", listOf(masterFile), prefix, 120)
    }

    fun pool(instances: Int = 3, threads: Int = 4) = EnginePool(engineDir, File(cacheDir, "engine/cache.jsonl"), instances, threads)

    fun tree() {
        pool().use { pool ->
            val tools = EngineTools(pool, config.thresholds.depth)
            for (chapter in config.chapters.filter { it.kind == "REPERTOIRE" }) {
                val (club, master) = indexes(chapter)
                val started = System.currentTimeMillis()
                val tree = TreeBuilder(tools, club, master, config, 3) { println(it) }.build(chapter)
                treeFile(chapter.id).also { it.parentFile.mkdirs() }.writeText(json.encodeToString(ChapterTree.serializer(), tree))
                val report = CoverageReport.of(tree)
                File(dir, "tree/${chapter.id}.coverage.json").writeText(json.encodeToString(CoverageReport.serializer(), report))
                println(report.summary())
                println("Engine cache hits ${pool.hits}, misses ${pool.misses}; ${(System.currentTimeMillis() - started) / 1000}s")
            }
        }
    }

    fun loadTree(chapter: String) = json.decodeFromString(ChapterTree.serializer(), treeFile(chapter).readText())
    fun loadFacts(chapter: String): Map<String, EdgeFacts> =
        json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(EdgeFacts.serializer()), factsFile(chapter).readText()).associateBy { it.nodeId }
    fun loadWriting(chapter: String): ChapterWriting? = claimsFile(chapter).takeIf { it.isFile }?.let { json.decodeFromString(ChapterWriting.serializer(), it.readText()) }

    fun facts() {
        pool().use { pool ->
            val tools = EngineTools(pool, config.thresholds.depth)
            for (chapter in config.chapters) {
                val tree = loadTree(chapter.id)
                // Both-colour courses: probe threats/prevented moves for real master choices, mistakes and club moves;
                // engine continuations get light facts (best line and evaluation) only.
                val heavy: (TreeNode) -> Boolean = if (tree.side == "BOTH") { n -> n.role != "ENGINE" && n.role != "PUNISH" && n.moves.size > 6 &&
                    (n.masterGames >= 30 || n.role in setOf("TRAP", "DEVIATION")) } else { _ -> true }
                val facts = FactBuilder.build(tree, tools, 3, { println(it) }, heavy)
                factsFile(chapter.id).also { it.parentFile.mkdirs() }.writeText(
                    json.encodeToString(kotlinx.serialization.builtins.ListSerializer(EdgeFacts.serializer()), facts))
                println("${chapter.id}: facts for ${facts.size} edges; threats ${facts.count { it.threat != null }}, prevents ${facts.count { it.prevents.isNotEmpty() }}; engine hits ${pool.hits} misses ${pool.misses}")
            }
        }
    }

    fun sanPath(node: TreeNode): String {
        var b = com.openinglab.shared.chess.BoardPosition.starting()
        return node.moves.joinToString(" ") { uci -> val t = b.sanAndPlayUci(uci); b = t.second; t.first }
    }

    /** Agent-mode writer input: one block per edge with stats, engine facts and the course continuation. */
    fun context(chapterId: String) {
        val tree = loadTree(chapterId)
        val facts = loadFacts(chapterId)
        val byId = tree.nodes.associateBy { it.id }
        val out = StringBuilder()
        out.appendLine("# Writer digest · ${config.title} · $chapterId · ${tree.nodes.size} nodes")
        for (node in tree.nodes) {
            val f = facts[node.id]
            val parent = node.parent?.let(byId::getValue)
            val kids = tree.children(node.id)
            out.appendLine("## ${sanPath(node)}")
            out.appendLine("role=${node.role} reach=${"%.4f".format(java.util.Locale.ROOT, node.reach)} club=${node.clubGames}/${parent?.positionClubGames ?: 0} score=${node.clubScore?.let { "%.2f".format(java.util.Locale.ROOT, it) }} master=${node.masterGames}/${parent?.positionMasterGames ?: 0}")
            out.appendLine("eval(after, White POV)=${f?.evalAfter} loss=${f?.lossCp} best=${f?.bestSan} threat=${f?.threat}(${f?.threatGainCp}) prevents=${f?.prevents?.joinToString { "${it.san}(${it.dropCp}${if (it.illegalNow) ",illegal" else ""}; ${it.refutation.joinToString(" ")})" }}")
            out.appendLine("engine pv after: ${f?.pvAfter?.joinToString(" ")}")
            if (kids.isNotEmpty()) out.appendLine("children: ${kids.joinToString { "${it.san}[${it.role}, club ${it.clubGames}, master ${it.masterGames}]" }}")
            node.verdict?.let { out.appendLine("LEAF verdict=$it eval=${node.verdictEval} stop=${node.stopReason}") }
        }
        val file = File(cacheDir, "digest/$courseId-$chapterId.md")
        file.parentFile.mkdirs(); file.writeText(out.toString())
        println("Wrote ${file.path} (${tree.nodes.size} nodes)")
    }

    @kotlinx.serialization.Serializable
    data class CheckedNode(val nodeId: String, val sanPath: String, val title: String?, val claims: List<CheckedClaim>,
                           val principle: CheckedClaim? = null, val whitePlan: List<CheckedClaim> = emptyList(),
                           val blackPlan: List<CheckedClaim> = emptyList(), val glossary: List<String> = emptyList())

    @kotlinx.serialization.Serializable
    data class CheckedChapter(val chapter: String, val writer: String, val intro: List<CheckedClaim>, val nodes: List<CheckedNode>,
                              val glossary: List<Pair<GlossaryWriting, List<CheckedClaim>>>, val summary: CheckSummary,
                              val variations: Map<String, CheckedVariation> = emptyMap())

    @kotlinx.serialization.Serializable
    data class CheckedVariation(val intro: List<CheckedClaim>, val white: List<CheckedClaim>, val black: List<CheckedClaim>)

    fun loadIdeas(chapter: String): List<VariationIdeas> = File(dir, "ideas/$chapter.json").takeIf { it.isFile }
        ?.let { json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(VariationIdeas.serializer()), it.readText()) }.orEmpty()

    @kotlinx.serialization.Serializable
    data class CheckSummary(val writerClaims: Int, val writerPassed: Int, val autoClaims: Int, val autoPassed: Int, val failures: List<String>)

    fun check() {
        pool().use { pool ->
            val tools = EngineTools(pool, config.thresholds.depth)
            val checker = ClaimChecker(tools)
            for (chapter in config.chapters) checkChapter(chapter, tools, checker)
        }
    }

    private fun continuation(tree: ChapterTree, node: TreeNode): List<String> {
        val out = mutableListOf<String>()
        var current = node
        while (out.size < 24) {
            val next = tree.children(current.id).let { kids -> kids.firstOrNull { it.role == "MAIN" || it.role == "PUNISH" } ?: kids.firstOrNull() } ?: break
            out += next.uci; current = next
        }
        return out
    }

    private fun checkChapter(chapter: ChapterConfig, tools: EngineTools, checker: ClaimChecker) {
        val tree = loadTree(chapter.id)
        val facts = loadFacts(chapter.id)
        val writing = loadWriting(chapter.id)
        val (club, master) = indexes(chapter)
        val byId = tree.nodes.associateBy { it.id }
        val learner = if (config.side == "BOTH") com.openinglab.shared.model.PieceColor.WHITE else com.openinglab.shared.model.PieceColor.valueOf(config.side)
        val both = tree.side == "BOTH"
        val paths = tree.nodes.associate { sanPath(it) to it }
        writing?.nodes?.keys?.filter { it !in paths }?.let { require(it.isEmpty()) { "Writer claims for unknown nodes: ${it.take(5)}" } }
        val pool = java.util.concurrent.Executors.newFixedThreadPool(3)
        val checked = try {
            tree.nodes.map { node ->
                pool.submit(java.util.concurrent.Callable {
                    val ctx = ClaimContext(Line(node.moves.dropLast(1)), node, continuation(tree, node), club, master)
                    val path = sanPath(node)
                    val w = writing?.nodes?.get(path)
                    val parent = node.parent?.let(byId::getValue)
                    val auto = facts[node.id]?.let { AutoClaims.forNode(node, it, ctx.mover, learner, parent?.positionClubGames ?: 0, parent?.positionMasterGames ?: 0, withStats = !both) }.orEmpty()
                    val leafEval = node.verdict?.let { AutoClaims.evaluation(it, node.verdictEval) }
                    CheckedNode(node.id, path, w?.title,
                        w?.claims.orEmpty().map { checker.check(it, ctx) } + (auto + listOfNotNull(leafEval)).map { checker.check(it, ctx, auto = true) },
                        w?.principle?.let { checker.check(it, ctx) },
                        w?.whitePlan.orEmpty().map { checker.check(it, ctx) }, w?.blackPlan.orEmpty().map { checker.check(it, ctx) },
                        w?.glossary.orEmpty())
                })
            }.map { it.get() }
        } finally { pool.shutdown() }
        val root = tree.nodes.firstOrNull { it.moves.size == tree.rootPly } ?: tree.nodes.first()
        val rootCtx = ClaimContext(Line(root.moves.dropLast(1)), root, continuation(tree, root), club, master)
        val intro = writing?.intro.orEmpty().map { checker.check(it, rootCtx) }
        val glossary = writing?.glossary.orEmpty().map { g -> g to g.claims.map { checker.check(it, rootCtx) } }
        val ideas = loadIdeas(chapter.id).associateBy { it.name }
        val variationChecks = writing?.variations.orEmpty().mapValues { (name, w) ->
            val idea = ideas[name]
            val anchor = idea?.anchorId?.let(byId::get)
            val ctx = anchor?.let { ClaimContext(Line(it.moves.dropLast(1)), it, continuation(tree, it), club, master) }
            fun one(c: Claim, white: Boolean?): CheckedClaim = when {
                c.type == "WINPLAN" -> checkWinPlan(c, idea, white ?: (c.side == "WHITE"), checker, anchor?.san ?: "")
                ctx == null -> CheckedClaim(c, false, "", "variation $name not found", false)
                else -> checker.check(c, ctx)
            }
            CheckedVariation(w.intro.map { one(it, null) }, w.white.map { one(it, true) }, w.black.map { one(it, false) })
        }
        val all = checked.flatMap { it.claims + listOfNotNull(it.principle) + it.whitePlan + it.blackPlan } + intro + glossary.flatMap { it.second } +
            variationChecks.values.flatMap { it.intro + it.white + it.black }
        val writerClaims = all.filter { !it.auto }
        val summary = CheckSummary(writerClaims.size, writerClaims.count { it.passed }, all.count { it.auto }, all.count { it.auto && it.passed },
            all.filter { !it.passed }.map { "${it.claim.type}: ${it.claim.text.take(80)} → ${it.reason}" })
        checkedFile(chapter.id).also { it.parentFile.mkdirs() }.writeText(json.encodeToString(CheckedChapter.serializer(),
            CheckedChapter(chapter.id, writing?.writer ?: "none", intro, checked, glossary, summary, variationChecks)))
        println("${chapter.id}: writer ${summary.writerPassed}/${summary.writerClaims} passed, auto ${summary.autoPassed}/${summary.autoClaims} passed")
        summary.failures.take(40).forEach { println("  FAIL $it") }
    }

    fun loadChecked(chapter: String) = json.decodeFromString(CheckedChapter.serializer(), checkedFile(chapter).readText())

    fun pack() = CoursePacker(this).pack()
    fun evals() = CourseEvals(this).run()
    fun game(chapter: String) = GameChapterBuilder(this).build(chapter)
}

/** SAN for a UCI move plus the resulting position. */
fun com.openinglab.shared.chess.BoardPosition.sanAndPlayUci(uci: String): Pair<String, com.openinglab.shared.chess.BoardPosition> {
    val t = sanAndPlay(com.openinglab.shared.model.ChessMove.fromUci(uci))
    return t.san to t.position
}

@kotlinx.serialization.Serializable
data class CoverageReport(
    val chapter: String, val nodes: Int, val lines: Int, val minPlies: Int, val maxPlies: Int, val medianPlies: Int,
    val verdicts: Map<String, Int>, val stopReasons: Map<String, Int>, val reachCovered: Double, val reachCut: Double,
    val capped: Int, val clubGames: Long, val masterGames: Long, val learnerMoves: Int, val opponentMoves: Int, val traps: Int,
) {
    fun summary() = "$chapter: $nodes nodes, $lines lines, plies $minPlies–$maxPlies (median $medianPlies), verdicts $verdicts, " +
        "stops $stopReasons, reach covered ${"%.4f".format(reachCovered)} (cut ${"%.4f".format(reachCut)}), capped $capped, traps $traps"

    companion object {
        fun of(tree: ChapterTree): CoverageReport {
            val leaves = tree.leaves
            val plies = leaves.map { it.moves.size }.sorted()
            val learner = com.openinglab.shared.model.PieceColor.valueOf(tree.side)
            val chapterNodes = tree.nodes.filter { it.moves.size > tree.rootPly }
            fun mover(n: TreeNode) = if (n.moves.size % 2 == 1) com.openinglab.shared.model.PieceColor.WHITE else com.openinglab.shared.model.PieceColor.BLACK
            return CoverageReport(tree.chapter.id, tree.nodes.size, leaves.size, plies.first(), plies.last(), plies[plies.size / 2],
                leaves.groupingBy { it.verdict ?: "NONE" }.eachCount(), leaves.groupingBy { it.stopReason ?: "NONE" }.eachCount(),
                leaves.filter { it.stopReason == "verdict" || it.stopReason == "terminal" }.sumOf { it.reach },
                leaves.filter { it.stopReason == "reach" }.sumOf { it.reach }, leaves.count { it.stopReason == "cap" },
                tree.clubGames, tree.masterGames, chapterNodes.count { mover(it) == learner }, chapterNodes.count { mover(it) != learner },
                tree.nodes.count { it.role == "TRAP" })
        }
    }
}
