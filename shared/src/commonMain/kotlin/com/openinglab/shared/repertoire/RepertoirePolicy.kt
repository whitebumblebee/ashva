// SPDX-License-Identifier: Apache-2.0
package com.openinglab.shared.repertoire

import com.openinglab.shared.chess.BoardPosition
import com.openinglab.shared.lesson.LessonGraph
import com.openinglab.shared.model.ChessMove
import com.openinglab.shared.model.PieceColor
import com.openinglab.shared.storage.contentSha256
import kotlinx.serialization.Serializable

/** A learner's choices, never a replacement for the immutable source routes. */
@Serializable
data class RepertoirePolicy(
    val id: String,
    val lessonId: String,
    val contentVersion: String,
    val name: String,
    val side: PieceColor,
    val revision: Int = 0,
    val preferredMoves: Map<String, String> = emptyMap(),
    val opponentReplies: Map<String, List<String>> = emptyMap(),
    val schemaVersion: Int = 1,
) {
    fun validate() {
        require(schemaVersion == 1 && revision >= 0) { "Unsupported repertoire version" }
        require(id.length in 1..512 && lessonId.length in 1..512 && name.length in 1..240)
        require(contentVersion.matches(Regex("[a-f0-9]{64}")))
        require(preferredMoves.size + opponentReplies.size <= LessonGraph.MAX_TOTAL_PLIES)
        for ((key, move) in preferredMoves) { require(key.length in 1..200); ChessMove.fromUci(move) }
        for ((key, moves) in opponentReplies) {
            require(key.length in 1..200 && moves.size <= 218 && moves.distinct().size == moves.size)
            moves.forEach { ChessMove.fromUci(it) }
        }
    }
}

data class RepertoireOption(val uci: String, val san: String, val routeNames: List<String>)
enum class RepertoireGapKind { CHOOSE_LEARNER_MOVE, INCLUDE_OPPONENT_REPLY, SOURCE_CONTINUATION }
data class RepertoireGap(val pathId: String, val ply: Int, val positionKey: String, val kind: RepertoireGapKind, val moveUci: String? = null)
data class RepertoireCoverage(
    val eligiblePathIds: List<String>,
    val gaps: List<RepertoireGap>,
    val sourceBoundaries: Int,
    val excludedOpponentReplies: Int,
    /** Only actual admitted prefixes, not dormant choices after an excluded/conflicting move. */
    val reachedPositionKeys: Set<String> = emptySet(),
)

/** Finite source-prefix traversal: repetition and transpositions never create an infinite book. */
class RepertoireBook(val graph: LessonGraph) {
    // Narration edits must not silently change the move-policy identity.
    val contentVersion: String = contentSha256(buildString {
        append(graph.id).append('\n').append(graph.initialPosition.toFen()).append('\n')
        graph.paths.values.sortedBy { it.id }.forEach { path ->
            append(path.id).append(':'); path.moves.forEach { append(it.move.uci).append(',') }; append('\n')
        }
    }.encodeToByteArray())
    private val boards = linkedMapOf<String, BoardPosition>()
    private val options: Map<String, List<RepertoireOption>>
    private class Prefix(val pathId: String, val ply: Int, val key: String) {
        val children = linkedMapOf<String, Prefix>()
        val endpoints = mutableListOf<String>()
    }
    private val root = Prefix(graph.originalPathId, 0, graph.initialPosition.positionKey)

    init {
        val collected = linkedMapOf<String, LinkedHashMap<String, Pair<String, MutableSet<String>>>>()
        for (path in graph.paths.values) {
            var prefix = root
            for ((ply, board) in path.positions.withIndex()) {
                boards.getOrPut(board.positionKey) { board }
                val step = path.moves.getOrNull(ply) ?: break
                val entries = collected.getOrPut(board.positionKey) { linkedMapOf() }
                entries.getOrPut(step.move.uci) { step.san to linkedSetOf() }.second += path.name
                prefix = prefix.children.getOrPut(step.move.uci) { Prefix(path.id, ply + 1, path.positions[ply + 1].positionKey) }
            }
            prefix.endpoints += path.id
        }
        options = collected.mapValues { (_, values) -> values.map { (uci, value) -> RepertoireOption(uci, value.first, value.second.toList()) } }
    }

    fun optionsAt(key: String): List<RepertoireOption> = options[key].orEmpty()
    fun policyId(side: PieceColor): String = "repertoire:${graph.id}:${side.name}:$contentVersion"

    /** Seed only the selected recorded route, not all possible replies hidden from the learner. */
    fun seed(side: PieceColor, pathId: String): RepertoirePolicy {
        val path = graph.paths.getValue(pathId)
        val own = linkedMapOf<String, String>()
        val replies = linkedMapOf<String, List<String>>()
        path.moves.forEachIndexed { ply, step ->
            val board = path.positions[ply]
            if (board.sideToMove == side) own.getOrPut(board.positionKey) { step.move.uci }
            else replies[board.positionKey] = (replies[board.positionKey].orEmpty() + step.move.uci).distinct()
        }
        return RepertoirePolicy(policyId(side), graph.id, contentVersion, "${graph.title} · ${side.name.lowercase().replaceFirstChar { it.uppercase() }}", side,
            preferredMoves = own, opponentReplies = replies).also(::validate)
    }

    fun validate(policy: RepertoirePolicy) {
        policy.validate()
        require(policy.lessonId == graph.id && policy.contentVersion == contentVersion && policy.id == policyId(policy.side)) {
            "This repertoire belongs to a different content version; saved choices have been retained."
        }
        for ((key, uci) in policy.preferredMoves) require(boards[key]?.sideToMove == policy.side && optionsAt(key).any { it.uci == uci }) {
            "Preferred move is not a recorded learner move in this snapshot"
        }
        for ((key, moves) in policy.opponentReplies) require(boards[key]?.sideToMove != policy.side && boards.containsKey(key) && moves.all { uci -> optionsAt(key).any { it.uci == uci } }) {
            "Reply is not a recorded opponent move in this snapshot"
        }
    }

    fun prefer(policy: RepertoirePolicy, key: String, uci: String): RepertoirePolicy =
        policy.copy(revision = policy.revision + 1, preferredMoves = policy.preferredMoves + (key to uci)).also(::validate)

    fun include(policy: RepertoirePolicy, key: String, uci: String, included: Boolean): RepertoirePolicy {
        val current = policy.opponentReplies[key].orEmpty()
        val next = if (included) (current + uci).distinct() else current - uci
        return policy.copy(revision = policy.revision + 1, opponentReplies = policy.opponentReplies + (key to next)).also(::validate)
    }

    fun includeAll(policy: RepertoirePolicy, key: String): RepertoirePolicy = policy.copy(
        revision = policy.revision + 1, opponentReplies = policy.opponentReplies + (key to optionsAt(key).map { it.uci }),
    ).also(::validate)

    /** Explicit route adoption may replace conflicting learner choices; callers must confirm it. */
    fun adoptRoute(policy: RepertoirePolicy, pathId: String): RepertoirePolicy {
        validate(policy)
        val route = seed(policy.side, pathId)
        return policy.copy(revision = policy.revision + 1,
            preferredMoves = policy.preferredMoves + route.preferredMoves,
            opponentReplies = (policy.opponentReplies.keys + route.opponentReplies.keys).associateWith {
                (policy.opponentReplies[it].orEmpty() + route.opponentReplies[it].orEmpty()).distinct()
            }).also(::validate)
    }

    fun coverage(policy: RepertoirePolicy, checkpoint: () -> Unit = {}): RepertoireCoverage {
        validate(policy)
        val admitted = mutableSetOf<String>()
        val gaps = mutableListOf<RepertoireGap>()
        val excluded = mutableSetOf<Pair<String, String>>()
        val stack = ArrayDeque<Prefix>().apply { add(root) }
        var boundaries = 0
        val reached = linkedSetOf<String>()
        while (stack.isNotEmpty()) {
            checkpoint()
            val prefix = stack.removeLast()
            reached += prefix.key
            admitted += prefix.endpoints
            if (prefix.endpoints.isNotEmpty()) boundaries += prefix.endpoints.size
            if (prefix.children.isEmpty()) continue // A source endpoint is not a promise of complete theory.
            val board = boards.getValue(prefix.key)
            fun gap(kind: RepertoireGapKind, move: String? = null) { gaps += RepertoireGap(prefix.pathId, prefix.ply, prefix.key, kind, move) }
            if (board.sideToMove == policy.side) {
                val preferred = policy.preferredMoves[prefix.key]
                when {
                    preferred == null -> gap(RepertoireGapKind.CHOOSE_LEARNER_MOVE)
                    preferred !in prefix.children -> gap(RepertoireGapKind.SOURCE_CONTINUATION, preferred)
                    else -> stack.add(prefix.children.getValue(preferred))
                }
            } else {
                val included = policy.opponentReplies[prefix.key].orEmpty()
                optionsAt(prefix.key).filter { it.uci !in included }.forEach { excluded += prefix.key to it.uci }
                if (included.isEmpty()) gap(RepertoireGapKind.INCLUDE_OPPONENT_REPLY)
                for (uci in included) {
                    val child = prefix.children[uci]
                    if (child == null) gap(RepertoireGapKind.SOURCE_CONTINUATION, uci) else stack.add(child)
                }
            }
        }
        return RepertoireCoverage(graph.paths.keys.filter { it in admitted }, gaps, boundaries, excluded.size, reached)
    }

    fun practiceGraph(policy: RepertoirePolicy): LessonGraph {
        val paths = coverage(policy).eligiblePathIds
        require(paths.isNotEmpty()) { "No recorded route fits these choices yet; fill an uncovered branch first." }
        return graph.restrictToPaths(paths.toSet(), "${graph.id}:policy:${policy.side}:${policy.revision}:$contentVersion")
    }
}
