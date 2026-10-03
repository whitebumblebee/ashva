// SPDX-License-Identifier: Apache-2.0
package com.openinglab.shared.lesson

import com.openinglab.shared.chess.BoardPosition
import com.openinglab.shared.chess.PositionStatus
import com.openinglab.shared.chess.sanAndPlay
import com.openinglab.shared.model.*
import kotlin.math.abs

enum class TeachingEvidence { BOARD_FACT, CONDITIONAL_PLAN }

data class TeachingPoint(val topic: String, val text: String, val evidence: TeachingEvidence)
data class PawnBreakCandidate(val uci: String, val san: String, val contactSquares: List<String>)
data class SideTeaching(val side: PieceColor, val points: List<TeachingPoint>)
data class PositionTeaching(
    val fen: String,
    val pointOfView: PieceColor,
    val status: PositionStatus,
    val focus: String,
    val own: SideTeaching,
    val opponent: SideTeaching,
    val pawnBreaks: List<PawnBreakCandidate>,
    val geometricMotifs: List<TeachingPoint>,
) {
    companion object { const val VERSION = "ashva-position-teaching/1" }
}

/** Original conditional coaching anchored to exact board facts. Neither an engine nor GM intention. */
object PositionTeacher {
    fun inspect(board: BoardPosition, pov: PieceColor, checkpoint: () -> Unit = {}): PositionTeaching {
        checkpoint()
        require(board.pieces.size <= 64)
        val status = board.status()
        val reduced = board.pieces.values.none { it.type == PieceType.QUEEN } &&
            board.pieces.values.count { it.type !in listOf(PieceType.PAWN, PieceType.KING) } <= 6
        val startingMinors = PieceColor.entries.sumOf { side -> homeMinors(side).count { square ->
            board.pieceAt(square)?.let { it.color == side && it.type in listOf(PieceType.KNIGHT, PieceType.BISHOP) } == true
        } }
        val focus = when {
            status !in listOf(PositionStatus.ONGOING, PositionStatus.CHECK) -> "The rules report ${status.name.lowercase().replace('_', ' ')}. Do not continue a terminal game; a source result may also describe resignation or agreement."
            status == PositionStatus.CHECK -> "${board.sideToMove.label()} is in check. Answer the check legally before applying a strategic plan."
            reduced -> "Reduced-material lens: no queens and at most six non-pawn, non-king pieces remain. This is a teaching heuristic, not an endgame tablebase result."
            startingMinors >= 4 -> "Development lens: $startingMinors minor pieces occupy their starting squares. Occupancy is a board fact, not proof that they have never moved."
            else -> "Coordination lens: compare king safety, pawn breaks and the opponent's immediate counterplay. This is not a position evaluation."
        }
        fun sideReport(side: PieceColor): SideTeaching {
            checkpoint()
            val name = side.label()
            val pawns = board.pieces.filterValues { it == Piece(side, PieceType.PAWN) }.keys.sorted()
            val enemyPawns = board.pieces.filterValues { it == Piece(side.opposite, PieceType.PAWN) }.keys
            val doubled = pawns.groupBy { it[0] }.filterValues { it.size > 1 }.keys.sorted()
            val isolated = pawns.filter { p -> pawns.none { abs(it[0] - p[0]) == 1 } }
            val passed = pawns.filter { p -> enemyPawns.none { e -> abs(e[0] - p[0]) <= 1 && ahead(e[1], p[1], side) } }
            val chains = pawns.flatMap { p -> pawns.filter { q -> abs(q[0] - p[0]) == 1 && q[1] - p[1] == direction(side) }.map { "$p–$it" } }
            val blocked = pawns.filter { p -> p[0] in "cdef" &&
                board.pieceAt("${p[0]}${p[1] + direction(side)}") == Piece(side.opposite, PieceType.PAWN) }
            val openFiles = ('a'..'h').filter { f -> board.pieces.none { (s, p) -> s[0] == f && p.type == PieceType.PAWN } }
            val halfOpen = ('a'..'h').filter { f -> pawns.none { it[0] == f } && enemyPawns.any { it[0] == f } }
            val minorsAtHome = homeMinors(side).filter { board.pieceAt(it)?.let { p -> p.color == side && p.type in listOf(PieceType.BISHOP, PieceType.KNIGHT) } == true }
            val attacked = board.pieces.filter { (s, p) -> p.color == side && p.type != PieceType.KING && board.isSquareAttacked(s, side.opposite) }
            val points = mutableListOf<TeachingPoint>()
            fun fact(topic: String, text: String) { points += TeachingPoint(topic, text, TeachingEvidence.BOARD_FACT) }
            fun plan(topic: String, text: String) { points += TeachingPoint(topic, text, TeachingEvidence.CONDITIONAL_PLAN) }
            fact("Material", "$name has " + PieceType.entries.filter { it != PieceType.KING }.joinToString(", ") { type ->
                "${board.pieces.values.count { it == Piece(side, type) }} ${type.name.lowercase()}s"
            } + ". Counts alone do not determine who is better.")
            fact("Pawn structure", "${name}'s pawns: ${pawns.listOrNone()}. Doubled files: ${doubled.joinToString(", ").ifEmpty { "none" }}. Isolated pawns: ${isolated.listOrNone()}. Passed pawns: ${passed.listOrNone()} (no enemy pawn ahead on the same or adjacent file).")
            if (chains.isNotEmpty()) fact("Pawn chains", "Diagonal pawn links: ${chains.listOrNone()}. These are geometric pawn defenses; a pinned pawn may be unable to capture.")
            if (blocked.isNotEmpty()) {
                fact("Locked centre", "Central pawns blocked directly by an enemy pawn: ${blocked.listOrNone()}.")
                plan("Break the chain carefully", "Compare a supported pawn lever against the base or head of the enemy chain, but first calculate the resulting captures and lines toward both kings. A locked centre does not make wing attacks automatically safe.")
            }
            if (isolated.isNotEmpty() || doubled.isNotEmpty()) plan("Structure trade-off", "With isolated or doubled pawns, compare active-piece play and supporting pawn advances against exchanges that leave a blockade. Neither structure is automatically weak; inspect the actual squares and defenders above.")
            if (passed.isNotEmpty()) plan("Passed-pawn plan", "For ${passed.listOrNone()}, compare safe advancement, blockade squares and king/rook support. A passed pawn is not automatically a promotion or a win; check the opponent's checks and counter-passed pawns.")
            fact("Files", "Open files: ${openFiles.joinToString(", ").ifEmpty { "none" }}. Half-open files for $name: ${halfOpen.joinToString(", ").ifEmpty { "none" }}.")
            if (openFiles.isNotEmpty() || halfOpen.isNotEmpty()) plan("Rook coordination", "Compare rook access to these files and useful entry squares. Calculate trades and defended entry squares before assuming an open file belongs to you.")
            if (minorsAtHome.isNotEmpty()) {
                fact("Development", "Minor pieces on starting squares: ${minorsAtHome.listOrNone()}.")
                plan("Improve coordination", "Compare useful development with the opponent's forcing checks/captures. Moving a piece off its starting square is not enough: ask what it attacks, defends and obstructs.")
            }
            if (attacked.isNotEmpty()) {
                fact("Attacked pieces", attacked.entries.sortedBy { it.key }.joinToString("; ") { (s, p) ->
                    "${p.type.name.lowercase()} $s: attackers ${board.attackersOf(s, side.opposite).listOrNone()}, friendly geometric defenders ${board.attackersOf(s, side).listOrNone()}"
                } + ". Geometric attacks include pinned pieces; these are not proof of legal or winning captures.")
                plan("Threat check", "Before continuing your plan, calculate the opponent's legal captures, checks and intermediate moves. Counting attackers and defenders is not a substitute for calculating the exchange sequence.")
            }
            val king = board.pieces.entries.single { it.value == Piece(side, PieceType.KING) }.key
            fact("King", "$name king: $king${if (board.isInCheck(side)) ", in check" else ", not in check"}. Remaining castling rights: ${board.castlingRights.filter { it.color == side }.joinToString { it.fen.toString() }.ifEmpty { "none" }}. Rights alone do not make castling legal or safe.")
            plan(if (reduced) "Endgame priorities" else "King-safety trade-off", if (reduced)
                "Compare safe king activity, pawn support and checks. Before simplifying, check pawn races, stalemate and the resulting material; this lens is not opposition, zugzwang or tablebase proof."
                else "Before opening the centre or pushing pawns near your king, compare the exposed diagonals/files and the opponent's forcing replies. Do not infer safety solely from a castled king.")
            return SideTeaching(side, points)
        }
        val ongoing = status in listOf(PositionStatus.ONGOING, PositionStatus.CHECK)
        val breaks = if (!ongoing) emptyList() else board.legalMoves().mapNotNull { move ->
            checkpoint()
            if (board.pieceAt(move.from)?.type != PieceType.PAWN || move.to[0] !in "cdef") return@mapNotNull null
            val contacts = board.pieces.filter { (s, p) -> p == Piece(board.sideToMove.opposite, PieceType.PAWN) &&
                ((s == move.to) || (abs(s[0] - move.to[0]) == 1 && s[1] - move.to[1] == direction(board.sideToMove))) }.keys.sorted()
            if (contacts.isEmpty()) null else PawnBreakCandidate(move.uci, board.sanAndPlay(move).san, contacts)
        }
        return PositionTeaching(board.toFen(), pov, status, focus, sideReport(pov), sideReport(pov.opposite), breaks,
            motifs(board, checkpoint))
    }

    private fun motifs(board: BoardPosition, checkpoint: () -> Unit): List<TeachingPoint> = buildList {
        // Both sides are described geometrically; never manufacture a legal turn for the other side.
        board.pieces.entries.sortedBy { it.key }.forEach { (from, piece) ->
            checkpoint()
            if (piece.type !in listOf(PieceType.PAWN, PieceType.KING)) {
                val targets = board.pieces.filter { (s, p) -> p.color != piece.color && p.type != PieceType.PAWN && from in board.attackersOf(s, piece.color) }.keys.sorted()
                if (targets.size >= 2) add(TeachingPoint("Multiple attack", "${piece.color.label()} ${piece.type.name.lowercase()} on $from geometrically attacks ${targets.listOrNone()}. This fork-like pattern may be defended or pinned; it is not proof of winning material.", TeachingEvidence.BOARD_FACT))
            }
            if (piece.type in listOf(PieceType.BISHOP, PieceType.ROOK, PieceType.QUEEN)) {
                val directions = when (piece.type) {
                    PieceType.BISHOP -> listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)
                    PieceType.ROOK -> listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
                    else -> listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1, -1 to 0, 1 to 0, 0 to -1, 0 to 1)
                }
                directions.forEach { (df, dr) ->
                    var f = from[0].code + df; var r = from[1].code + dr
                    var blocker: String? = null
                    while (f in 'a'.code..'h'.code && r in '1'.code..'8'.code) {
                        val s = "${f.toChar()}${r.toChar()}"
                        val target = board.pieceAt(s)
                        if (target != null) {
                            if (target.color == piece.color) break
                            if (blocker == null) {
                                if (target.type == PieceType.KING) break
                                blocker = s
                            } else {
                                if (target.type == PieceType.KING) add(TeachingPoint("King-ray pin", "${piece.color.label()} ${piece.type.name.lowercase()} $from, enemy piece $blocker and enemy king $s share an otherwise clear ray. The pinned piece cannot expose its king, but may move along the ray or capture the attacker if legal. This is not a claim that it can be won.", TeachingEvidence.BOARD_FACT))
                                break
                            }
                        }
                        f += df; r += dr
                    }
                }
            }
        }
    }

    private fun direction(side: PieceColor) = if (side == PieceColor.WHITE) 1 else -1
    private fun ahead(a: Char, b: Char, side: PieceColor) = if (side == PieceColor.WHITE) a > b else a < b
    private fun homeMinors(side: PieceColor) = if (side == PieceColor.WHITE) listOf("b1", "g1", "c1", "f1") else listOf("b8", "g8", "c8", "f8")
    private fun List<String>.listOrNone() = joinToString(", ").ifEmpty { "none" }
    private fun PieceColor.label() = name.lowercase().replaceFirstChar { it.uppercase() }
}

data class GroundedContinuation(val positions: List<BoardPosition>, val explanations: List<MoveStep>) {
    companion object {
        /** Reject mismatched notation instead of attaching plausible prose to a different move. */
        fun build(initial: BoardPosition, uci: List<String>, san: List<String>, checkpoint: () -> Unit = {}): GroundedContinuation {
            require(uci.size == san.size && uci.size in 1..256)
            var board = initial
            val positions = mutableListOf(board)
            val explanations = uci.mapIndexed { index, text ->
                checkpoint()
                val move = ChessMove.fromUci(text)
                require(board.sanAndPlay(move).san == san[index]) { "Continuation notation mismatch" }
                val (idea, next) = PositionCoach.explainAndPlay(board, move, san[index])
                board = next; positions += board
                idea
            }
            return GroundedContinuation(positions, explanations)
        }
    }
}
