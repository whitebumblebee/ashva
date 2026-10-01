package com.openinglab.shared.lesson

import kotlinx.serialization.Serializable

import com.openinglab.shared.chess.BoardPosition
import com.openinglab.shared.chess.PgnGame
import com.openinglab.shared.chess.PgnLine
import com.openinglab.shared.chess.san
import com.openinglab.shared.model.ChessMove
import com.openinglab.shared.model.Opening
import com.openinglab.shared.model.PieceColor

enum class LessonPathKind { AUTHORED, ORIGINAL_GAME, ANNOTATED_VARIATION, ANALYZED_VARIATION }

/** Teaching belongs to a path, not to a transposed board node. */
data class LessonAnnotation(
    val title: String = "",
    val explanation: String = "",
    val principle: String = "",
    val comments: List<String> = emptyList(),
    val nags: List<Int> = emptyList(),
)

data class LessonMove(val move: ChessMove, val san: String, val annotation: LessonAnnotation)
data class LessonEdge(val move: ChessMove, val targetKey: String)
data class LessonNode(val key: String, val continuations: List<LessonEdge>)
data class LessonOrigin(val parentPathId: String, val parentPly: Int)

data class LessonPath(
    val id: String,
    val name: String,
    val kind: LessonPathKind,
    val description: String,
    val moves: List<LessonMove>,
    /** Full path snapshots retain clocks/repetition even when nodes are shared. */
    val positions: List<BoardPosition>,
    val origin: LessonOrigin? = null,
    val result: String? = null,
    val whiteIdea: String = "",
    val blackIdea: String = "",
)

class LessonGraph private constructor(
    val id: String,
    val title: String,
    val initialPosition: BoardPosition,
    val paths: Map<String, LessonPath>,
    val nodes: Map<String, LessonNode>,
    val originalPathId: String,
) {
    fun start(side: PieceColor, pathId: String = originalPathId): LessonReplay = LessonReplay.start(this, side, pathId)

    companion object {
        const val MAX_PATH_PLIES = 4096
        const val MAX_TOTAL_PLIES = 50_000
        const val MAX_PATHS = 1000

        fun fromOpening(opening: Opening): LessonGraph {
            val initial = BoardPosition.starting()
            val paths = opening.variations.map { variation ->
                val positions = mutableListOf(initial)
                val moves = variation.steps.map { step ->
                    val move = ChessMove.fromUci(step.uci)
                    val before = positions.last()
                    val san = before.san(move)
                    positions += before.apply(move)
                    LessonMove(move, san, LessonAnnotation(step.title, step.explanation, step.principle))
                }
                LessonPath(variation.id, variation.name, LessonPathKind.AUTHORED, variation.description, moves, positions.toList(),
                    whiteIdea = variation.whiteIdea, blackIdea = variation.blackIdea)
            }
            return build(opening.id, opening.name, initial, paths, opening.mainLine.id)
        }

        /** Original main line stays separate from annotation/hypothetical variations. */
        fun fromPgn(game: PgnGame, id: String = "imported-game"): LessonGraph {
            val paths = mutableListOf<LessonPath>()
            var totalPlies = 0
            fun addLine(line: PgnLine, pathId: String, prefix: List<LessonMove>, origin: LessonOrigin?, depth: Int) {
                require(depth <= 64 && paths.size < MAX_PATHS) { "Lesson has too many or too deeply nested variations" }
                require(prefix.size + line.plies.size <= MAX_PATH_PLIES) { "Lesson path is too long" }
                totalPlies += prefix.size + line.plies.size
                require(totalPlies <= MAX_TOTAL_PLIES) { "Lesson graph exceeds move limit" }
                var board = game.initialPosition
                val positions = mutableListOf(board)
                val moves = prefix.toMutableList()
                for (move in prefix) { board = board.apply(move.move); positions += board }
                for (ply in line.plies) {
                    moves += LessonMove(ply.move, board.san(ply.move), LessonAnnotation(
                        explanation = ply.comments.joinToString("\n"), comments = ply.comments.toList(), nags = ply.nags.toList()))
                    board = board.apply(ply.move)
                    positions += board
                }
                paths += LessonPath(pathId,
                    if (origin == null) "Original game" else "Variation ${pathId.removePrefix("original/")}",
                    if (origin == null) LessonPathKind.ORIGINAL_GAME else LessonPathKind.ANNOTATED_VARIATION,
                    line.leadingComments.joinToString("\n"), moves.toList(), positions.toList(), origin, line.result)
                for ((i, ply) in line.plies.withIndex()) for ((j, variation) in ply.variations.withIndex()) {
                    val branchPly = prefix.size + i
                    addLine(variation, "$pathId/${i + 1}.${j + 1}", moves.take(branchPly), LessonOrigin(pathId, branchPly), depth + 1)
                }
            }
            addLine(game.line, "original", emptyList(), null, 0)
            val title = "${game.tags["White"] ?: "White"} – ${game.tags["Black"] ?: "Black"}"
            return build(id, title, game.initialPosition, paths, "original")
        }

        private fun build(id: String, title: String, initial: BoardPosition, paths: List<LessonPath>, original: String): LessonGraph {
            require(paths.isNotEmpty() && paths.size <= MAX_PATHS)
            require(paths.map { it.id }.distinct().size == paths.size) { "Duplicate lesson path IDs" }
            require(paths.any { it.id == original })
            require(paths.sumOf { it.moves.size } <= MAX_TOTAL_PLIES)
            val edges = linkedMapOf<String, MutableSet<LessonEdge>>()
            for (path in paths) {
                require(path.moves.size <= MAX_PATH_PLIES && path.positions.size == path.moves.size + 1)
                for ((i, position) in path.positions.withIndex()) {
                    val outgoing = edges.getOrPut(position.positionKey) { linkedSetOf() }
                    path.moves.getOrNull(i)?.let { outgoing += LessonEdge(it.move, path.positions[i + 1].positionKey) }
                }
            }
            return LessonGraph(id, title, initial, paths.associateBy { it.id },
                edges.mapValues { (key, outgoing) -> LessonNode(key, outgoing.toList()) }, original)
        }
    }
}

data class LessonBranch(val pathId: String, val targetPly: Int, val name: String, val description: String, val nextMove: LessonMove, val kind: LessonPathKind)
@Serializable
data class ReplayBranchVisit(val fromPly: Int, val targetPathId: String, val targetPly: Int)
/** Compact stable IDs/cursors; a platform can persist this without serializing the graph. */
@Serializable
data class ReplaySnapshot(val rootPathId: String, val playerSide: PieceColor, val ply: Int, val branches: List<ReplayBranchVisit> = emptyList())

private data class ReturnFrame(
    val pathId: String, val moves: List<LessonMove>, val positions: List<BoardPosition>, val ply: Int,
    val visit: ReplayBranchVisit,
)

/** Finite, explicit routes: never recursively traverse a cyclic position graph for playback. */
class LessonReplay private constructor(
    val graph: LessonGraph,
    val rootPathId: String,
    val pathId: String,
    val moves: List<LessonMove>,
    private val positions: List<BoardPosition>,
    val ply: Int,
    val playerSide: PieceColor,
    private val returns: List<ReturnFrame>,
) {
    val position: BoardPosition get() = positions[ply]
    val path: LessonPath get() = graph.paths.getValue(pathId)
    val atStart: Boolean get() = ply == 0
    val atEnd: Boolean get() = ply == moves.size
    val canReturn: Boolean get() = returns.isNotEmpty()
    val nextMove: LessonMove? get() = moves.getOrNull(ply)
    val lastMove: LessonMove? get() = moves.getOrNull(ply - 1)
    val branchDepth: Int get() = returns.size

    fun jump(ply: Int): LessonReplay {
        require(ply in 0..moves.size) { "Replay cursor out of bounds" }
        return LessonReplay(graph, rootPathId, pathId, moves, positions, ply, playerSide, returns)
    }
    fun first() = jump(0)
    fun previous() = jump((ply - 1).coerceAtLeast(0))
    fun next() = jump((ply + 1).coerceAtMost(moves.size))
    fun last() = jump(moves.size)
    fun withSide(side: PieceColor) = LessonReplay(graph, rootPathId, pathId, moves, positions, ply, side, returns)

    fun branches(): List<LessonBranch> {
        if (returns.size >= MAX_BRANCH_DEPTH) return emptyList()
        val key = position.positionKey
        return graph.paths.values.asSequence().filter { it.id != pathId }.flatMap { path ->
            path.moves.indices.asSequence().filter { i ->
                i >= (path.origin?.parentPly ?: 0) &&
                    path.positions[i].positionKey == key && path.moves[i].move != nextMove?.move
            }.map { i -> LessonBranch(path.id, i, path.name, path.description, path.moves[i], path.kind) }
        }.distinctBy { it.pathId to it.nextMove.move }.toList()
    }

    fun diverge(branch: LessonBranch): LessonReplay {
        require(branch in branches()) { "Branch is not available at this position" }
        val target = graph.paths.getValue(branch.pathId)
        val route = moves.take(ply) + target.moves.drop(branch.targetPly)
        require(route.size <= LessonGraph.MAX_PATH_PLIES) { "Branched route exceeds move limit" }
        // Keep the actual played prefix and its history, not the transposed target path's history.
        val boards = positions.take(ply + 1).toMutableList()
        for (move in route.drop(ply)) boards += boards.last().apply(move.move)
        val visit = ReplayBranchVisit(ply, branch.pathId, branch.targetPly)
        val frame = ReturnFrame(pathId, moves, positions, ply, visit)
        return LessonReplay(graph, rootPathId, target.id, route, boards.toList(), ply, playerSide, returns + frame)
    }

    fun returnToBranch(): LessonReplay {
        val frame = returns.lastOrNull() ?: return this
        return LessonReplay(graph, rootPathId, frame.pathId, frame.moves, frame.positions, frame.ply, playerSide, returns.dropLast(1))
    }
    fun snapshot() = ReplaySnapshot(rootPathId, playerSide, ply, returns.map { it.visit })

    companion object {
        const val MAX_BRANCH_DEPTH = 16
        internal fun start(graph: LessonGraph, side: PieceColor, pathId: String): LessonReplay {
            val path = graph.paths.getValue(pathId)
            return LessonReplay(graph, pathId, pathId, path.moves, path.positions, 0, side, emptyList())
        }
        fun restore(graph: LessonGraph, snapshot: ReplaySnapshot): LessonReplay {
            require(snapshot.branches.size <= MAX_BRANCH_DEPTH)
            var replay = graph.start(snapshot.playerSide, snapshot.rootPathId)
            for (visit in snapshot.branches) {
                replay = replay.jump(visit.fromPly)
                val branch = replay.branches().singleOrNull { it.pathId == visit.targetPathId && it.targetPly == visit.targetPly }
                    ?: throw IllegalArgumentException("Saved branch no longer exists in this lesson")
                replay = replay.diverge(branch)
            }
            return replay.jump(snapshot.ply)
        }
    }
}
