// SPDX-License-Identifier: Apache-2.0
package com.openinglab.shared.course

import com.openinglab.shared.chess.BoardPosition
import com.openinglab.shared.chess.sanAndPlay
import com.openinglab.shared.lesson.LessonGraph
import com.openinglab.shared.model.ChessMove
import com.openinglab.shared.model.Difficulty
import com.openinglab.shared.model.MoveStep
import com.openinglab.shared.model.Opening
import com.openinglab.shared.model.OpeningSide
import com.openinglab.shared.model.PieceColor
import com.openinglab.shared.model.TeachingCoverage
import com.openinglab.shared.model.Variation
import com.openinglab.shared.model.VariationOrigin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Deep course packs (docs/DEEP_COURSE_PLAN.md). Moves and evaluations come from game data and engine analysis;
 * every shown sentence was built from machine-checked claims and carries a provenance label. Generated text is
 * not a human author's or a GM's words.
 */
@Serializable
data class DeepCoursePack(
    val schema: Int,
    val id: String,
    val version: Int,
    val title: String,
    val side: String,
    val level: String,
    val summary: String,
    val provenance: CourseProvenance,
    val chapters: List<CourseChapter>,
    val glossary: List<GlossaryEntry> = emptyList(),
) {
    /** Null for a both-colour course, where the learner chooses the point of view. */
    val learnerSide: PieceColor? get() = if (side == "BOTH") null else PieceColor.valueOf(side)
}

@Serializable
data class CourseProvenance(
    val sources: List<CourseSource>,
    val engine: String,
    val engineBudget: String,
    val writer: String,
    val generatedOn: String,
    val labelPolicy: String,
    val limitations: String,
)

@Serializable
data class CourseSource(val id: String, val title: String, val url: String, val license: String, val games: Int, val note: String)

@Serializable
data class GlossaryEntry(val id: String, val title: String, val text: String, val label: String)

@Serializable
data class CourseChapter(
    val id: String,
    val title: String,
    val kind: String,
    val intro: String,
    /** Ply at which the chapter's own branching starts; earlier nodes are the shared approach moves. */
    val rootPly: Int,
    val nodes: List<CourseNode>,
    val coverage: ChapterCoverage? = null,
    val game: CourseGame? = null,
    /** Named (or important unnamed) variations with how each side wins them. */
    val variations: List<CourseVariation> = emptyList(),
)

@Serializable
data class CourseVariation(
    val name: String,
    val nodeId: String,
    val path: List<String>,
    val games: Int,
    val whiteWins: Int,
    val draws: Int,
    val blackWins: Int,
    val bands: Map<String, Int>,
    val intro: String,
    val introLabel: String,
    val white: CourseSideIdeas,
    val black: CourseSideIdeas,
)

@Serializable
data class CourseSideIdeas(
    val wins: Int, val text: String, val label: String, val examples: List<CourseExample>,
    val plan: String = "",
    val patterns: List<CoursePattern> = emptyList(),
)

@Serializable
data class CoursePattern(val san: String, val winShare: Double, val otherShare: Double)

@Serializable
data class CourseExample(
    val white: String, val black: String, val whiteElo: Int?, val blackElo: Int?, val event: String, val date: String,
    val result: String, val site: String, val uci: List<String>, val anchorPly: Int,
)

@Serializable
data class ChapterCoverage(
    val lines: Int,
    val minPlies: Int,
    val maxPlies: Int,
    val reachCovered: Double,
    val cutoff: Double,
    val capped: Int,
    val verdicts: Map<String, Int>,
    val note: String,
)

@Serializable
data class CourseGame(
    val white: String, val black: String, val whiteElo: Int?, val blackElo: Int?,
    val event: String, val date: String, val result: String, val site: String, val license: String,
)

@Serializable
data class CourseNode(
    val id: String,
    val parent: String? = null,
    val san: String,
    val uci: String,
    val role: String,
    val title: String,
    val text: String,
    val principle: String = "",
    val label: String,
    /** Probability of reaching this node in club games from the chapter root, when measured. */
    val weight: Double = 0.0,
    val evidence: NodeEvidence? = null,
    val verdict: Verdict? = null,
    val glossary: List<String> = emptyList(),
    /** Variation name of this position (deepest named ancestor), from the CC0 opening list. */
    val opening: String? = null,
)

@Serializable
data class NodeEvidence(
    val clubGames: Int = 0,
    val clubScore: Double? = null,
    val masterGames: Int = 0,
    val masterScore: Double? = null,
    /** Evaluation after the move, centipawns from White's point of view; null when mate is reported. */
    val evalCp: Int? = null,
    val mate: Int? = null,
    val depth: Int = 0,
    /** Games per strength band that chose this move (e.g. "2600+", "Lichess 1600–2200"). */
    val bands: Map<String, Int> = emptyMap(),
)

@Serializable
data class Verdict(val result: String, val evalCp: Int? = null, val whitePlan: String, val blackPlan: String, val label: String)

object CourseRoles {
    val repertoire = setOf("MAIN", "SIDE", "DEVIATION", "TRAP", "PUNISH", "AVOID", "ENGINE")
    val game = setOf("ORIGINAL", "BETTER", "ALTERNATIVE", "REFUTATION")
    val verdicts = setOf("WHITE_BETTER", "BLACK_BETTER", "EQUAL", "UNCLEAR", "GAME_RESULT", "TRANSPOSES")
    fun display(role: String) = when (role) {
        "MAIN" -> "Main line"; "SIDE" -> "Alternative"; "DEVIATION" -> "Club deviation"; "TRAP" -> "Common mistake"
        "ENGINE" -> "Engine continuation"
        "PUNISH" -> "Punish it"; "AVOID" -> "Avoid"; "ORIGINAL" -> "Played in the game"; "BETTER" -> "Better was"
        "ALTERNATIVE" -> "Tempting alternative"; "REFUTATION" -> "If instead"; else -> role
    }
    fun verdict(result: String) = when (result) {
        "WHITE_BETTER" -> "White is better"; "BLACK_BETTER" -> "Black is better"; "EQUAL" -> "Equal"
        "UNCLEAR" -> "Unclear"; "GAME_RESULT" -> "Game result"; "TRANSPOSES" -> "Transposes into another line"; else -> result
    }
}

/** Fail-closed structural and legal validation; returns the checked boards for each node. */
object DeepCourseValidator {
    const val VERSION = 1
    const val MAX_CHAPTERS = 64
    const val MAX_NODES = 20_000
    const val MAX_TEXT = 4_000
    private val idPattern = Regex("[a-z0-9][a-z0-9-]{0,63}")
    private val uciPattern = Regex("[a-h][1-8][a-h][1-8][qrbn]?")

    fun validate(pack: DeepCoursePack, checkpoint: () -> Unit = {}): Map<String, Map<String, BoardPosition>> {
        validateStructure(pack, checkpoint)
        return pack.chapters.associate { it.id to replayChapter(it, checkpoint = checkpoint) }
    }

    /** Cheap checks for summaries. Legal replay is still required before a chapter can be opened. */
    fun validateStructure(pack: DeepCoursePack, checkpoint: () -> Unit = {}) {
        require(pack.schema == 1) { "Unsupported course schema ${pack.schema}" }
        require(idPattern.matches(pack.id) && pack.version > 0)
        require(pack.side in setOf("WHITE", "BLACK", "BOTH"))
        require(pack.chapters.size in 1..MAX_CHAPTERS && pack.chapters.map { it.id }.distinct().size == pack.chapters.size)
        require(pack.provenance.sources.isNotEmpty() && pack.provenance.labelPolicy.isNotBlank())
        require(pack.glossary.map { it.id }.distinct().size == pack.glossary.size)
        val glossaryIds = pack.glossary.map { it.id }.toSet()
        require(pack.chapters.sumOf { it.nodes.size } <= MAX_NODES) { "Course exceeds node bound" }
        for (chapter in pack.chapters) {
            require(idPattern.matches(chapter.id))
            require(chapter.kind in setOf("REPERTOIRE", "GAME"))
            val roles = if (chapter.kind == "GAME") CourseRoles.game + "MAIN" else CourseRoles.repertoire
            require(chapter.nodes.isNotEmpty() && chapter.nodes.map { it.id }.distinct().size == chapter.nodes.size)
            val plies = mutableMapOf<String, Int>()
            val children = chapter.nodes.groupBy { it.parent }
            for (node in chapter.nodes) {
                checkpoint()
                // Parents precede children, so the tree is acyclic and replayable in file order.
                val parentPly = node.parent?.let { requireNotNull(plies[it]) { "Node ${node.id} precedes its parent" } } ?: 0
                require(uciPattern.matches(node.uci)) { "Node ${node.id} has invalid UCI" }
                require(node.role in roles) { "Unknown role ${node.role}" }
                require(node.label.isNotBlank() && node.title.isNotBlank() && node.text.isNotBlank()) { "Node ${node.id} lacks text or label" }
                require(node.text.length <= MAX_TEXT && node.principle.length <= MAX_TEXT)
                require(node.weight in 0.0..1.0 && node.glossary.all { it in glossaryIds })
                plies[node.id] = parentPly + 1
                node.verdict?.let { require(it.result in CourseRoles.verdicts && it.label.isNotBlank()) }
            }
            val leaves = chapter.nodes.filter { children[it.id].isNullOrEmpty() }
            if (chapter.kind == "REPERTOIRE") require(leaves.all { it.verdict != null }) { "Every repertoire line must end in a verdict" }
            else require(chapter.game != null && chapter.nodes.count { it.role == "ORIGINAL" } > 0)
            for (v in chapter.variations) {
                require(v.nodeId in plies && v.name.isNotBlank() && v.introLabel.isNotBlank()) { "Variation ${v.name} has no node" }
                for (ideas in listOf(v.white, v.black)) {
                    require(ideas.plan.length <= MAX_TEXT && ideas.patterns.size <= 12)
                    require(ideas.patterns.all { it.san.isNotBlank() && it.san.length <= 16 && it.winShare in 0.0..1.0 && it.otherShare in 0.0..1.0 })
                }
                for (ex in v.white.examples + v.black.examples) require(ex.uci.size in 1..400 && ex.uci.all(uciPattern::matches))
            }
            require(leaves.all { plies.getValue(it.id) > chapter.rootPly }) { "A line ends before the chapter root" }
        }
    }

    /** File-order parents retain exact clocks and repetition history; each node is replayed once. */
    internal fun replayChapter(chapter: CourseChapter, previouslyValidated: Boolean = false, checkpoint: () -> Unit = {}): Map<String, BoardPosition> {
        val initial = BoardPosition.starting()
        val boards = HashMap<String, BoardPosition>(chapter.nodes.size)
        for (node in chapter.nodes) {
            checkpoint()
            val before = node.parent?.let(boards::getValue) ?: initial
            // UCI is supplied in the pack. Canonical SAN equality still checks disambiguation/check/mate.
            val move = ChessMove.fromUci(node.uci)
            boards[node.id] = if (previouslyValidated) before.advanceKnownLegal(move) else {
                val transition = before.sanAndPlay(move)
                require(transition.san == node.san) { "Node ${node.id} notation mismatch" }
                transition.position
            }
        }
        return boards
    }
}

/** Home/overview need metadata and counts, never an Opening or a LessonGraph. */
data class DeepCourseChapterSummary(val course: DeepCoursePack, val chapter: CourseChapter, val openingId: String, val lineCount: Int)

/** A chapter presented through the existing Study/Practice/branch player, plus course-only metadata. */
data class DeepCourseChapterView(
    val course: DeepCoursePack,
    val chapter: CourseChapter,
    val opening: Opening,
    /** Variation ID → probability that club games follow this line (sums to the covered share). */
    val lineWeights: Map<String, Double>,
    val roles: Map<String, String>,
    /** Line ID → node IDs on that line, to open the most-reached line through a variation. */
    val lineNodes: Map<String, Set<String>> = emptyMap(),
    val nodePositions: Map<String, BoardPosition> = emptyMap(),
) {
    val linesByNode: Map<String, List<String>> = buildMap {
        val index = mutableMapOf<String, MutableList<String>>()
        lineNodes.forEach { (line, nodes) -> nodes.forEach { index.getOrPut(it) { mutableListOf() }.add(line) } }
        index.forEach { (node, lines) -> put(node, lines.toList()) }
    }
    val variationTree = CourseVariationTree(chapter.variations)
    private val nodesByLine = chapter.nodes.associateBy { it.id }.let { byId ->
        lineNodes.mapValues { (_, nodes) -> nodes.map(byId::getValue) }
    }
    private val mostReachedByNode = linesByNode.mapValues { (_, lines) ->
        lines.maxWithOrNull(compareBy<String> { nodesByLine[it]?.lastOrNull()?.verdict?.result != "TRANSPOSES" }
            .thenBy { lineWeights[it] ?: 0.0 }.thenBy { nodesByLine[it]?.size ?: 0 })
    }
    private val popularityByNode = chapter.nodes.groupBy { it.parent }.values.flatMap { siblings ->
        val masterTotal = siblings.sumOf { it.evidence?.masterGames ?: 0 }.toDouble()
        val clubTotal = siblings.sumOf { it.evidence?.clubGames ?: 0 }.toDouble()
        siblings.map { node ->
            val share = when {
                masterTotal > 0 -> (node.evidence?.masterGames ?: 0) / masterTotal
                clubTotal > 0 -> (node.evidence?.clubGames ?: 0) / clubTotal
                else -> 0.0
            }
            node.id to when { share >= .5 -> "Main move"; share >= .15 -> "Popular"; else -> null }
        }
    }.toMap()
    fun lineThrough(nodeId: String): String? = mostReachedByNode[nodeId]
    fun nodeOnLine(lineId: String, ply: Int): CourseNode? = nodesByLine[lineId]?.getOrNull(ply - 1)
    fun anchorPly(lineId: String, nodeId: String): Int? = nodesByLine[lineId]?.indexOfFirst { it.id == nodeId }
        ?.takeIf { it >= 0 }?.plus(1)
    /** Share of replies at this exact position, never cumulative reach from the chapter start. */
    fun movePopularity(nodeId: String): String? = popularityByNode[nodeId]
    fun lineEndingSummary(lineId: String): String = friendlyLineEnding(nodesByLine[lineId]?.lastOrNull()?.verdict)
    /** Branch offer label: the move with its number and the variation that move enters, e.g. "4.d3 · Anti-Berlin Variation". */
    fun branchTitle(lineId: String, targetPly: Int): String? = nodeOnLine(lineId, targetPly + 1)?.let { node ->
        DeepCourseCatalog.moveLabel(targetPly + 1, node.san) + (node.opening?.let { " · " + DeepCourseCatalog.shortName(it).substringAfter(", ").ifBlank { DeepCourseCatalog.shortName(it) } } ?: "")
    }
    /** Master games for the move a branch offer starts with (for ordering offers by how often strong players choose it). */
    fun branchGames(lineId: String, targetPly: Int): Int = nodeOnLine(lineId, targetPly + 1)?.evidence?.masterGames ?: 0
    /** Short facts for a branch offer: master games for that move and the line's ending. */
    fun branchFacts(lineId: String, targetPly: Int): String = listOfNotNull(
        nodeOnLine(lineId, targetPly + 1)?.evidence?.masterGames?.takeIf { it > 0 }?.let { "$it master games" },
        lineEndingSummary(lineId)).joinToString(" · ")
    fun lessonGraph(): LessonGraph = if (nodePositions.isEmpty()) LessonGraph.fromOpening(opening) else {
        val initial = BoardPosition.starting()
        LessonGraph.fromCheckedOpening(opening, lineNodes.mapValues { (_, nodes) -> listOf(initial) + nodes.map(nodePositions::getValue) })
    }
}

class DeepCourseCatalog(val packs: List<DeepCoursePack>, checkpoint: () -> Unit = {}, validateOnDemand: Boolean = false,
    private val validationCache: com.openinglab.shared.data.PackValidationCache? = null) {
    private val checked = buildMap<String, Map<String, BoardPosition>> {
        packs.forEach { pack ->
            if (validateOnDemand) DeepCourseValidator.validateStructure(pack, checkpoint)
            else {
                val positions = DeepCourseValidator.validate(pack, checkpoint)
                pack.chapters.forEach { put(openingId(pack, it), positions.getValue(it.id)) }
            }
        }
    }
    val summaries = packs.flatMap { pack -> pack.chapters.map { chapter ->
        val parents = chapter.nodes.mapNotNull { it.parent }.toSet()
        DeepCourseChapterSummary(pack, chapter, openingId(pack, chapter), chapter.nodes.count { it.id !in parents })
    } }
    private val byOpening = summaries.associate { summary -> summary.openingId to summary }
    private val prepared = MutableStateFlow<Map<String, DeepCourseChapterView>>(emptyMap())
    /** Compatibility for callers explicitly requesting every chapter; startup uses summaries. */
    val chapters: List<DeepCourseChapterView> get() = summaries.map { requireNotNull(chapter(it.openingId)) }
    private val examples = MutableStateFlow<Map<String, Opening>>(emptyMap())
    fun preparedChapter(id: String): DeepCourseChapterView? = prepared.value[id]
    fun preparedOpening(id: String): Opening? = prepared.value[id]?.opening ?: examples.value[id]
    fun chapter(openingId: String, checkpoint: () -> Unit = {}): DeepCourseChapterView? {
        prepared.value[openingId]?.let { return it }
        val summary = byOpening[openingId] ?: return null
        val boards = checked[openingId] ?: DeepCourseValidator.replayChapter(summary.chapter, checkpoint = checkpoint,
            previouslyValidated = validationCache?.let { it.isValidated("pack") || it.isValidated(openingId) } == true)
        checkpoint()
        validationCache?.takeIf { !it.isValidated("pack") && !it.isValidated(openingId) }?.markValidated(openingId)
        val view = present(summary.course, summary.chapter, boards, checkpoint)
        return prepared.updateAndGet { it + (openingId to (it[openingId] ?: view)) }.getValue(openingId)
    }
    /** After Home is ready, finish validation without eagerly constructing lesson presentations. */
    fun validateRemaining(checkpoint: () -> Unit = {}) {
        if (validationCache?.isValidated("pack") == true) return
        for (summary in summaries) {
            checkpoint()
            if (validationCache?.isValidated(summary.openingId) != true) {
                DeepCourseValidator.replayChapter(summary.chapter, checkpoint = checkpoint)
                checkpoint()
                validationCache?.markValidated(summary.openingId)
            }
        }
        checkpoint()
        validationCache?.markValidated("pack")
    }

    fun getOpening(id: String, checkpoint: () -> Unit = {}): Opening? = chapter(id, checkpoint)?.opening ?: exampleOpening(id, checkpoint)

    /** "<chapter opening id>:ex:<variation index>:<w|b>:<n>" → that example game, created once on demand. */
    fun exampleId(view: DeepCourseChapterView, variation: Int, white: Boolean, n: Int) = exampleId(view.opening.id, variation, white, n)
    fun exampleOpening(id: String, checkpoint: () -> Unit = {}): Opening? {
        val marker = id.lastIndexOf(":ex:").takeIf { it > 0 } ?: return null
        val view = chapter(id.substring(0, marker), checkpoint) ?: return null
        val parts = id.substring(marker + 4).split(':')
        if (parts.size != 3) return null
        val v = view.chapter.variations.getOrNull(parts[0].toIntOrNull() ?: return null) ?: return null
        val side = when (parts[1]) { "w" -> v.white; "b" -> v.black; else -> return null }
        val ex = side.examples.getOrNull(parts[2].toIntOrNull() ?: return null) ?: return null
        examples.value[id]?.let { return it }
        val opening = exampleOpening(view.course, view.chapter, id, ex)
        return examples.updateAndGet { it + (id to (it[id] ?: opening)) }.getValue(id)
    }

    companion object {
        const val ID_PREFIX = "course:deep-v1:"
        /** Bound on following transposed positions into other lines when presenting one line. */
        const val MAX_TRANSPOSITION_HOPS = 8
        private val codec = Json { ignoreUnknownKeys = false }
        fun parse(json: String): DeepCoursePack = codec.decodeFromString(DeepCoursePack.serializer(), json)
        fun openingId(pack: DeepCoursePack, chapter: CourseChapter) = "$ID_PREFIX${pack.id}:v${pack.version}:${chapter.id}"

        internal fun present(pack: DeepCoursePack, chapter: CourseChapter, boards: Map<String, BoardPosition> = emptyMap(), checkpoint: () -> Unit = {}): DeepCourseChapterView {
            val byId = chapter.nodes.associateBy { it.id }
            val children = chapter.nodes.groupBy { it.parent }
            val leaves = chapter.nodes.filter { children[it.id].isNullOrEmpty() }
            val game = chapter.kind == "GAME"
            // Preferred continuation: what masters play most (MAIN), then the most-reached move.
            fun bestChild(node: CourseNode): CourseNode? = children[node.id].orEmpty()
                .maxWithOrNull(compareBy<CourseNode>({ it.role == "MAIN" || it.role == "ORIGINAL" }, { it.weight }))
            // A line that ends because its position transposes continues along the line that owns that position.
            val continuers = chapter.nodes.filter { !children[it.id].isNullOrEmpty() }
                .mapNotNull { node -> boards[node.id]?.positionKey?.let { it to node } }.groupBy({ it.first }, { it.second })
            fun spliced(line: List<CourseNode>): List<CourseNode> {
                if (game || continuers.isEmpty()) return line
                var result = line
                val seen = line.mapTo(HashSet()) { it.id }
                repeat(MAX_TRANSPOSITION_HOPS) {
                    val last = result.last()
                    if (last.verdict?.result != "TRANSPOSES") return result
                    val target = boards[last.id]?.positionKey?.let { continuers[it] }?.firstOrNull { it.id !in seen } ?: return result
                    val tail = generateSequence(bestChild(target)) { bestChild(it) }.takeWhile { seen.add(it.id) }.toList()
                    if (tail.isEmpty() || result.size + tail.size > LessonGraph.MAX_PATH_PLIES) return result
                    result = result + tail
                }
                return result
            }
            fun lineOf(leaf: CourseNode): List<CourseNode> = spliced(generateSequence(leaf) { it.parent?.let(byId::getValue) }.toList().reversed())
            // Up to the chapter's own first move follow where most of the chapter's lines go (e.g. 3...Nf6 in the Berlin
            // chapter even though 3...a6 is more popular overall); from there follow the most-played moves.
            val lineCount = HashMap<String, Int>()
            fun linesBelow(node: CourseNode): Int = lineCount.getOrPut(node.id) { children[node.id]?.sumOf(::linesBelow) ?: 1 }
            val depth = HashMap<String, Int>()
            fun plyOf(node: CourseNode): Int = depth.getOrPut(node.id) { 1 + (node.parent?.let { plyOf(byId.getValue(it)) } ?: 0) }
            fun mainChild(node: CourseNode): CourseNode? = if (plyOf(node) <= chapter.rootPly)
                children[node.id].orEmpty().maxWithOrNull(compareBy(::linesBelow)) else bestChild(node)
            val mainLeaf = chapter.nodes.firstOrNull { it.parent == null }?.let { root -> generateSequence(root) { mainChild(it) }.last() }
            val ordered = leaves.map { it to lineOf(it) }.sortedWith(compareBy<Pair<CourseNode, List<CourseNode>>>(
                // The main line follows the most-played moves to the end; then most-reached lines.
                { (leaf, _) -> if (leaf == mainLeaf) 0 else 1 },
                { (leaf, _) -> -leaf.weight }, { (_, line) -> line.size }))
            val openingId = openingId(pack, chapter)
            val roles = mutableMapOf<String, String>()
            val weights = mutableMapOf<String, Double>()
            val usedNames = HashMap<String, Int>()
            val steps = byId.mapValues { (_, n) -> MoveStep(n.uci, n.san, n.title, n.text, n.principle, n.label, players(n)) }
            val variations = ordered.mapIndexed { index, (leaf, line) ->
                checkpoint()
                val defining = line.firstOrNull { it.role != "MAIN" && it.role != "ORIGINAL" && it.role != "ENGINE" && it.role != "PUNISH" }
                val id = "deep:${pack.id}:v${pack.version}:${chapter.id}:${leaf.id}"
                val role = defining?.role ?: if (game) "ORIGINAL" else "MAIN"
                roles[id] = role
                weights[id] = leaf.weight
                val indices = line.withIndex().associate { it.value.id to it.index }
                val label = { node: CourseNode -> moveLabel(indices.getValue(node.id) + 1, node.san) }
                val named = line.lastOrNull { it.opening != null }?.opening
                val name = when {
                    named != null && !game -> {
                        // Variation name, plus the last branching move when the line leaves the named position.
                        val firstNamed = line.indexOfFirst { it.opening == named }
                        val branch = line.lastOrNull { it.role in setOf("SIDE", "DEVIATION", "TRAP") && indices.getValue(it.id) > firstNamed }
                        val base = shortName(named) + (branch?.let { " · ${label(it)}" } ?: "")
                        val n = (usedNames[base] ?: 0) + 1; usedNames[base] = n
                        if (n == 1) base else "$base · ${label(line.last { it.role != "ENGINE" && it.role != "PUNISH" })}" + if (n > 2) " ($n)" else ""
                    }
                    defining == null -> if (game) "The game as played" else "Main line"
                    else -> "${CourseRoles.display(defining.role)} · ${label(defining)}"
                }
                val verdict = line.last().verdict
                val description = buildString {
                    if (verdict != null) append("${CourseRoles.verdict(verdict.result)}${verdict.evalCp?.let { " (${formatEval(it)})" } ?: ""} at the end of this line. ")
                    if (!game && leaf.weight > 0) append("Reached in about ${percent(leaf.weight)} of games from the chapter start. ")
                    append(verdict?.label ?: line.last().label)
                }
                Variation(id, name, CourseRoles.display(role).uppercase(), description,
                    line.map { steps.getValue(it.id) },
                    identifiesOpening = false,
                    whiteIdea = verdict?.whitePlan ?: chapter.intro, blackIdea = verdict?.blackPlan ?: chapter.intro,
                    authoredContinuation = true,
                    origin = when {
                        !game -> VariationOrigin.COURSE_LINE
                        defining == null -> VariationOrigin.ORIGINAL_GAME
                        else -> VariationOrigin.ENGINE_LINE
                    })
            }
            val side = when (pack.learnerSide) { PieceColor.WHITE -> OpeningSide.WHITE; PieceColor.BLACK -> OpeningSide.BLACK; null -> OpeningSide.BOTH }
            val plies = variations.map { it.steps.size }
            val opening = Opening(openingId, "${pack.title}: ${chapter.title}", "Deep course", "", side, Difficulty.INTERMEDIATE,
                chapter.intro, "Generated course · engine-checked claims", 0xFF6D4C41, 0,
                keyIdeas = listOfNotNull(pack.summary, chapter.coverage?.note, pack.provenance.labelPolicy),
                variations = variations, recognitionPly = chapter.rootPly,
                teaching = TeachingCoverage("ashva-deep-course/1", 0, variations.size, true, plies.min(), plies.max()))
            val lineNodes = ordered.associate { (leaf, line) -> "deep:${pack.id}:v${pack.version}:${chapter.id}:${leaf.id}" to line.map { it.id }.toSet() }
            return DeepCourseChapterView(pack, chapter, opening, weights, roles, lineNodes, boards)
        }

        fun shortName(name: String) = name.substringAfter(": ", name)
        fun exampleId(chapterOpeningId: String, variation: Int, white: Boolean, n: Int) = "$chapterOpeningId:ex:$variation:${if (white) "w" else "b"}:$n"
        fun moveLabel(ply: Int, san: String) = "${(ply + 1) / 2}${if (ply % 2 == 1) "." else "..."}$san"

        /** "Who plays this" line from the node's strength-band counts. */
        fun players(node: CourseNode): String {
            val b = node.evidence?.bands.orEmpty().filterValues { it > 0 }
            if (b.isEmpty()) return ""
            return "Chosen in: " + b.entries.joinToString(" · ") { (band, n) -> "$band $n" } + " games"
        }

        /** Example master game as a replayable original-game lesson; built on demand (SAN needs a legal replay). */
        fun exampleOpening(pack: DeepCoursePack, chapter: CourseChapter, id: String, example: CourseExample): Opening {
            var board = com.openinglab.shared.chess.BoardPosition.starting()
            val steps = example.uci.mapIndexed { i, uci ->
                val t = board.sanAndPlay(com.openinglab.shared.model.ChessMove.fromUci(uci)); board = t.position
                val title = if (i + 1 == example.anchorPly) "The variation starts here" else "Played in the game"
                MoveStep(uci, t.san, title, if (i + 1 == example.anchorPly) "From here the game shows how the variation was won." else "Original move from the broadcast score.",
                    "", "Original game · CC BY-SA 4.0 (Lichess broadcast)")
            }
            val name = "${example.white} – ${example.black}"
            val variation = Variation("$id:game", name, "ORIGINAL GAME", "${example.event}, ${example.date}, ${example.result}. ${example.site}", steps,
                identifiesOpening = false, whiteIdea = "Original moves only; study the position ideas in the course.", blackIdea = "Original moves only; study the position ideas in the course.",
                authoredContinuation = true, origin = VariationOrigin.ORIGINAL_GAME)
            return Opening(id, "Example game: $name", "Deep course", "", OpeningSide.BOTH, Difficulty.INTERMEDIATE,
                "${example.event} · ${example.date} · ${example.result}", "Original broadcast score", 0xFF6D4C41, 0, emptyList(), listOf(variation),
                recognitionPly = 0)
        }

        fun formatEval(cp: Int): String = (if (cp >= 0) "+" else "−") + (kotlin.math.abs(cp) / 100.0).toString().let {
            if (it.substringAfter('.').length == 1) it + "0" else it
        }
        private fun percent(value: Double): String = when {
            value >= 0.1 -> "${(value * 100).toInt()}%"
            value >= 0.01 -> "${(value * 1000).toInt() / 10.0}%"
            else -> "<1%"
        }
    }
}
