// SPDX-License-Identifier: Apache-2.0
package com.openinglab.course

import com.openinglab.shared.chess.BoardPosition
import com.openinglab.shared.course.ChapterCoverage
import com.openinglab.shared.course.CourseChapter
import com.openinglab.shared.course.CourseGame
import com.openinglab.shared.course.CourseNode
import com.openinglab.shared.course.CourseProvenance
import com.openinglab.shared.course.CourseSource
import com.openinglab.shared.course.CourseSideIdeas
import com.openinglab.shared.course.CoursePattern
import com.openinglab.shared.course.CourseExample
import com.openinglab.shared.course.CourseVariation
import com.openinglab.shared.course.DeepCourseCatalog
import com.openinglab.shared.course.DeepCoursePack
import com.openinglab.shared.course.DeepCourseValidator
import com.openinglab.shared.course.GlossaryEntry
import com.openinglab.shared.course.NodeEvidence
import com.openinglab.shared.course.Verdict
import com.openinglab.shared.lesson.PositionCoach
import com.openinglab.shared.model.ChessMove
import com.openinglab.shared.model.PieceColor
import com.openinglab.shared.storage.contentSha256
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class CourseManifest(val id: String, val version: Int, val sha256: String, val bytes: Int, val chapters: Int, val nodes: Int,
                          val lines: Int, val generatedOn: String, val writerClaimsPassed: Int, val writerClaims: Int,
                          val autoClaimsPassed: Int, val autoClaims: Int)

/** Phase 8 render + Phase 1 publish: prose comes only from checker-passed claims, with provenance labels. */
class CoursePacker(private val p: CoursePipeline) {
    private val compact = Json { prettyPrint = false; encodeDefaults = true }

    companion object {
        private fun label(evidence: Collection<String>): String {
            val parts = evidence.map {
                when (it) { "engine-checked" -> "engine-checked"; "course line" -> "engine-checked"; "game statistics" -> "game statistics"
                    "board fact" -> "board facts"; "generated explanation" -> "general idea, not engine-checked"; else -> it }
            }.distinct().sortedBy { listOf("engine-checked", "game statistics", "board facts", "general idea, not engine-checked").indexOf(it) }
            return "Generated · " + parts.ifEmpty { listOf("board facts") }.joinToString(" · ")
        }

        /** Anchor introductions replace automatic move prose; only checked writer claims follow them. */
        internal fun explanation(claims: List<CheckedClaim>, intros: List<Pair<String, List<CheckedClaim>>>, fallback: String): Pair<String, String> {
            val writerPassed = claims.filter { !it.auto && it.passed }
            val passedIntros = intros.map { (name, intro) -> name to intro.filter { it.passed } }.filter { it.second.isNotEmpty() }
            if (passedIntros.isNotEmpty()) {
                val text = (passedIntros.map { (name, intro) -> "${DeepCourseCatalog.shortName(name)}: " + intro.joinToString(" ") { it.claim.text } } +
                    writerPassed.map { it.claim.text }).joinToString(" ")
                return text to label(passedIntros.flatMap { it.second }.map { it.evidence } + writerPassed.map { it.evidence })
            }
            val autoPassed = claims.filter { it.auto && it.passed && it.claim.type != "EVALUATION" }
                .filter { a -> writerPassed.none { it.claim.type == a.claim.type } }
            val used = writerPassed + autoPassed.take(2)
            return used.joinToString(" ") { it.claim.text }.ifBlank { fallback } to label(used.map { it.evidence })
        }

        internal fun sideIdeas(s: SideWins, white: Boolean, claims: List<CheckedClaim>): CourseSideIdeas {
            val who = if (white) "White" else "Black"
            val written = claims.filter { it.passed }
            val stats = when {
                s.wins < 8 -> "Only ${s.wins} decisive master wins for $who from here, too few to show a reliable pattern."
                s.patterns.isEmpty() -> "In ${s.wins} master games $who won from here, but no single move stands out: the winners used many different plans. Replay the example games to see them."
                else -> "In ${s.wins} master games $who won from here, the winners most distinctively played " +
                    s.patterns.take(5).joinToString("; ") { "${it.san.ifBlank { it.move }} (${(it.winShare * 100).toInt()}% of wins vs ${(it.otherShare * 100).toInt()}% of other games)" } + "."
            }
            val text = (written.joinToString(" ") { it.claim.text } + " " + stats).trim()
            return CourseSideIdeas(s.wins, text, label(written.map { it.evidence } + "game statistics"),
                s.examples.map { e -> CourseExample(e.white, e.black, e.whiteElo, e.blackElo, e.event, e.date, e.result, e.site, e.uci, e.anchorPly) },
                plan = written.filter { it.claim.type == "WINPLAN" }.joinToString(" ") { it.claim.text },
                patterns = s.patterns.map { CoursePattern(it.san.ifBlank { it.move }, it.winShare, it.otherShare) })
        }
    }

    private fun autoTitle(node: TreeNode, learner: PieceColor?, mover: PieceColor, punisher: Boolean = false): String = if (learner == null) when (node.role) {
        "MAIN" -> "Most played: ${node.san}"
        "SIDE" -> "Alternative: ${node.san}"
        "DEVIATION" -> "Club move: ${node.san}"
        "TRAP" -> "Common mistake: ${node.san}"
        "PUNISH" -> if (punisher) "Punish it: ${node.san}" else "Best defence: ${node.san}"
        "ENGINE" -> "Engine line: ${node.san}"
        else -> node.san
    } else when (node.role) {
        "MAIN" -> if (mover == learner) "Your move: ${node.san}" else "Main reply: ${node.san}"
        "DEVIATION" -> "Alternative: ${node.san}"
        "TRAP" -> "Common mistake: ${node.san}"
        "PUNISH" -> if (mover == learner) "Punish it: ${node.san}" else "Best defence: ${node.san}"
        "ORIGINAL" -> "Played: ${node.san}"
        "BETTER" -> "Better was: ${node.san}"
        "ALTERNATIVE" -> "Tempting: ${node.san}"
        "REFUTATION" -> "If instead: ${node.san}"
        else -> node.san
    }

    fun pack() {
        val config = p.config
        val learner = if (config.side == "BOTH") null else PieceColor.valueOf(config.side)
        val chapters = mutableListOf<CourseChapter>()
        val glossary = linkedMapOf<String, GlossaryEntry>()
        var writer = "none"
        var counts = IntArray(4)
        val allExtra = File(p.dir, "extra").listFiles().orEmpty().flatMap {
            json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(NodeExtra.serializer()), it.readText()) }.associateBy { it.id }
        for (chapterConfig in config.chapters) {
            val tree = p.loadTree(chapterConfig.id)
            val facts = p.loadFacts(chapterConfig.id)
            val checked = p.loadChecked(chapterConfig.id)
            writer = checked.writer
            counts[0] += checked.summary.writerPassed; counts[1] += checked.summary.writerClaims
            counts[2] += checked.summary.autoPassed; counts[3] += checked.summary.autoClaims
            val checkedById = checked.nodes.associateBy { it.nodeId }
            val extra = File(p.dir, "extra/${chapterConfig.id}.json").takeIf { it.isFile }
                ?.let { json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(NodeExtra.serializer()), it.readText()) }.orEmpty().associateBy { it.id }
            val treeById = tree.nodes.associateBy { it.id }
            val ideas = p.loadIdeas(chapterConfig.id).filter { it.anchorId in treeById }
            val anchorIntros = ideas.groupBy { it.anchorId }.mapValues { (_, variations) ->
                variations.map { it.name to checked.variations[it.name]?.intro.orEmpty() }
            }
            fun trapMover(n: TreeNode): PieceColor? = generateSequence(n) { it.parent?.let(treeById::get) }.firstOrNull { it.role == "TRAP" }
                ?.let { t -> if (t.moves.size % 2 == 1) PieceColor.WHITE else PieceColor.BLACK }
            for ((g, claims) in checked.glossary) if (claims.isNotEmpty() && claims.all { it.passed })
                glossary[g.id] = GlossaryEntry(g.id, g.title, claims.joinToString(" ") { it.claim.text }, label(claims.map { it.evidence }))
            val nodes = tree.nodes.map { node ->
                val before = Line(node.moves.dropLast(1)).board
                val mover = before.sideToMove
                val c = checkedById.getValue(node.id)
                val coach = PositionCoach.explain(before, ChessMove.fromUci(node.uci), node.san)
                val (text, explanationLabel) = explanation(c.claims, anchorIntros[node.id].orEmpty(), coach.explanation)
                val f = facts[node.id]
                val leaf = tree.children(node.id).isEmpty()
                val transposes = extra[node.id]?.transposesTo
                val verdict = if (!leaf) null else if (transposes != null) {
                    val target = extra[transposes]?.opening ?: allExtra[transposes]?.opening
                    val text = "This position also arises by another move order" + (target?.let { " (${com.openinglab.shared.course.DeepCourseCatalog.shortName(it)})" } ?: "") +
                        ". Its continuations are taught there; at this point the lesson offers them as branches."
                    Verdict("TRANSPOSES", f?.evalAfter ?: node.evalCp, text, text, label(listOf("board fact")))
                } else {
                    val evalClaim = c.claims.firstOrNull { it.auto && it.passed && it.claim.type == "EVALUATION" }
                    val white = c.whitePlan.filter { it.passed }
                    val black = c.blackPlan.filter { it.passed }
                    val pv = f?.pvAfter.orEmpty().take(6)
                    val engineLine = if (pv.isEmpty()) "" else " The engine's preferred continuation is ${numbered(node.moves, pv)}."
                    val result = if (chapterConfig.kind == "GAME" && node.role == "ORIGINAL") "GAME_RESULT" else node.verdict ?: "UNCLEAR"
                    Verdict(result, node.verdictEval,
                        (white.joinToString(" ") { it.claim.text }.ifBlank { (evalClaim?.claim?.text ?: "") + engineLine }).trim().ifBlank { "Play on with the usual plans for this structure." },
                        (black.joinToString(" ") { it.claim.text }.ifBlank { (evalClaim?.claim?.text ?: "") + engineLine }).trim().ifBlank { "Play on with the usual plans for this structure." },
                        label((white + black).map { it.evidence } + listOf("engine-checked")))
                }
                CourseNode(node.id, node.parent, node.san, node.uci, node.role,
                    c.title ?: autoTitle(node, learner, mover, punisher = trapMover(node)?.let { it != mover } == true), text,
                    c.principle?.takeIf { it.passed }?.claim?.text ?: "", explanationLabel, node.reach.coerceIn(0.0, 1.0),
                    NodeEvidence(node.clubGames, node.clubScore, node.masterGames, node.masterScore,
                        f?.evalAfter?.takeIf { kotlin.math.abs(it) < 50_000 } ?: node.evalCp, null, node.depth, extra[node.id]?.bands.orEmpty()),
                    verdict, c.glossary.filter { it in glossary }, extra[node.id]?.opening)
            }
            val coverage = File(p.dir, "tree/${chapterConfig.id}.coverage.json").takeIf { it.isFile }?.let {
                json.decodeFromString(CoverageReport.serializer(), it.readText())
            }
            val game = File(p.dir, "game/${chapterConfig.id}.meta.json").takeIf { it.isFile }?.let { json.decodeFromString(CourseGame.serializer(), it.readText()) }
            val intro = checked.intro.filter { it.passed }.joinToString(" ") { it.claim.text }.ifBlank { config.summary }
            val variations = ideas.map { v ->
                val w = checked.variations[v.name]
                val introClaims = w?.intro.orEmpty().filter { it.passed }
                CourseVariation(com.openinglab.shared.course.DeepCourseCatalog.shortName(v.name), v.anchorId, v.path, v.games, v.whiteWins, v.draws, v.blackWins, v.bands,
                    introClaims.joinToString(" ") { it.claim.text }.ifBlank { "Master games: ${v.games} (White won ${v.whiteWins}, ${v.draws} draws, Black won ${v.blackWins})." },
                    label(introClaims.map { it.evidence } + "game statistics"),
                    sideIdeas(v.white, true, w?.white.orEmpty()), sideIdeas(v.black, false, w?.black.orEmpty()))
            }
            val computedCoverage = if (tree.side != "BOTH") null else tree.leaves.let { leaves ->
                val plies = leaves.map { it.moves.size }
                ChapterCoverage(leaves.size, plies.min(), plies.max(), leaves.filter { it.stopReason in setOf("verdict", "terminal") }.sumOf { it.reach }.coerceAtMost(1.0),
                    0.0, leaves.count { it.stopReason == "cap" }, leaves.groupingBy { it.verdict ?: "TRANSPOSES" }.eachCount(),
                    "${leaves.size} lines from ${p.config.chapters.size} chapters' master (2200+) and club games; every line follows master practice as deep as the games go, then engine play until a verdict, or ends where it transposes into another line.")
            }
            chapters += CourseChapter(chapterConfig.id, chapterConfig.title, chapterConfig.kind, intro, tree.rootPly, nodes,
                coverage?.let { ChapterCoverage(it.lines, it.minPlies, it.maxPlies, it.reachCovered, tree.thresholds.reachCutoff, it.capped, it.verdicts,
                    "${it.lines} lines from ${it.clubGames} club and ${it.masterGames} master games; every line ends at an engine verdict, the reach cutoff or a reported cap.") } ?: computedCoverage,
                game, variations)
        }
        val identity = Json.parseToJsonElement(File(engineDir, "identity.json").readText()) as kotlinx.serialization.json.JsonObject
        fun id(key: String) = identity[key].toString().trim('"')
        val clubReport = File(cacheDir, "filtered/club-ruy.stats.json").readText().let { json.decodeFromString(FilterReport.serializer(), it) }
        val masterReport = File(cacheDir, "filtered/master-ruy.stats.json").readText().let { json.decodeFromString(FilterReport.serializer(), it) }
        val pack = DeepCoursePack(1, config.id, config.version, config.title, config.side, config.level, config.summary,
            CourseProvenance(
                listOf(
                    CourseSource("lichess-standard-2026-09", "Lichess rated games, September 2026 (first ${clubReport.bytes / (1024 * 1024)} MiB of the export)",
                        "https://database.lichess.org/#standard_games", "CC0-1.0", clubReport.kept.toInt(),
                        "Ruy Lopez games, both players 1600–2200, 5+0 or slower; ${clubReport.games} games scanned. Used only for move frequencies and scores."),
                    CourseSource("lichess-broadcasts", "Lichess official broadcasts (${masterReport.inputs} monthly exports)",
                        "https://database.lichess.org/#broadcasts", "CC-BY-SA-4.0", masterReport.kept.toInt(),
                        "Ruy Lopez games with both players rated 2200+ (bands 2600+, 2400–2599, 2200–2399). Moves and results as broadcast; derived statistics and example games remain CC BY-SA 4.0."),
                ),
                "${id("name")} ${id("version")} (host build ${id("binarySha256").take(12)})",
                "depth ${config.thresholds.depth} (at most ${BuildEngine.MAX_MOVE_TIME_MS / 1000} s per search), MultiPV up to 5, verdicts confirmed at depths ${config.thresholds.depth - 4} and ${config.thresholds.depth}",
                writer, java.time.LocalDate.now().toString(),
                "All text is generated. Each sentence comes from a claim that passed an automatic check: engine-checked (Stockfish confirmed it), game statistics (club/master data), board facts (read from the position), or a general idea that names no concrete moves and is therefore not engine-checked. Not reviewed by a human coach.",
                "Finite pilot: lines follow this course's thresholds, not every possible continuation. Engine verdicts are at a stated depth, not proof. Club data is a partial month. Course data is CC BY-SA 4.0 because it adapts Lichess broadcast data (club statistics come from CC0 Lichess games)."),
            chapters, glossary.values.toList())
        DeepCourseValidator.validate(pack)
        // Record exactly which downloaded bytes and filters produced this pack.
        val inputs = File(p.dir, "inputs").also { it.mkdirs() }
        listOf("raw/club.manifest.json", "raw/broadcasts.manifest.json", "raw/puzzles.manifest.json",
            "filtered/club-ruy.stats.json", "filtered/master-ruy.stats.json").map { File(cacheDir, it) }.filter { it.isFile }
            .forEach { it.copyTo(File(inputs, it.name), overwrite = true) }
        val catalog = DeepCourseCatalog(listOf(pack))
        val text = compact.encodeToString(DeepCoursePack.serializer(), pack)
        val outDir = File(p.dir, "v${config.version}").also { it.mkdirs() }
        File(outDir, "course.json").writeText(text)
        val manifest = CourseManifest(config.id, config.version, contentSha256(text.encodeToByteArray()), text.encodeToByteArray().size,
            chapters.size, chapters.sumOf { it.nodes.size }, catalog.chapters.sumOf { it.opening.variations.size }, pack.provenance.generatedOn,
            counts[0], counts[1], counts[2], counts[3])
        File(outDir, "course.manifest.json").writeText(json.encodeToString(CourseManifest.serializer(), manifest) + "\n")
        println("Packed ${outDir.path}/course.json: ${manifest.bytes} bytes, ${manifest.nodes} nodes, ${manifest.lines} lines; sha256 ${manifest.sha256}")
    }

    private fun numbered(history: List<String>, sans: List<String>): String {
        var ply = history.size
        return sans.joinToString(" ") { san -> val prefix = if (ply % 2 == 0) "${ply / 2 + 1}." else if (san == sans.first()) "${ply / 2 + 1}..." else ""; ply++; prefix + san }
    }
}
