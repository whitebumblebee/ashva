package com.openinglab.shared.chess

import com.openinglab.shared.model.ChessMove
import com.openinglab.shared.model.Piece
import com.openinglab.shared.model.PieceColor
import com.openinglab.shared.model.PieceType
import kotlin.math.abs

enum class CastlingRight(val fen: Char, val color: PieceColor, val rookSquare: String) {
    WHITE_KING('K', PieceColor.WHITE, "h1"), WHITE_QUEEN('Q', PieceColor.WHITE, "a1"),
    BLACK_KING('k', PieceColor.BLACK, "h8"), BLACK_QUEEN('q', PieceColor.BLACK, "a8"),
}

enum class PositionStatus {
    ONGOING, CHECK, CHECKMATE, STALEMATE, INSUFFICIENT_MATERIAL, FIVEFOLD_REPETITION, SEVENTY_FIVE_MOVES,
}

/** Standard chess only. Move history belongs to the game, not to transposition identity. */
data class BoardPosition(
    val pieces: Map<String, Piece>,
    val sideToMove: PieceColor = PieceColor.WHITE,
    val lastMove: ChessMove? = null,
    val castlingRights: Set<CastlingRight> = emptySet(),
    val enPassantTarget: String? = null,
    val halfmoveClock: Int = 0,
    val fullmoveNumber: Int = 1,
    val repetitionHistory: List<String> = emptyList(),
) {
    fun pieceAt(square: String): Piece? = pieces[square]

    fun legalMoves(from: String? = null): List<ChessMove> = pieces.entries.asSequence()
        .filter { (square, piece) -> piece.color == sideToMove && (from == null || square == from) }
        .flatMap { (square, piece) ->
            pseudoTargets(square, piece).asSequence().flatMap { target ->
                if (piece.type == PieceType.PAWN && target[1] in "18") {
                    PROMOTIONS.asSequence().map { ChessMove(square, target, it) }
                } else sequenceOf(ChessMove(square, target))
            }
        }
        .filter { !applyUnchecked(it, recordHistory = false).isInCheck(sideToMove) }
        .toList()

    fun legalTargets(from: String): Set<String> = legalMoves(from).map { it.to }.toSet()
    fun isLegal(move: ChessMove): Boolean = move in legalMoves(move.from)

    /** Reject illegal moves rather than silently corrupting the board. Promotion must be explicit. */
    fun apply(move: ChessMove): BoardPosition {
        require(isLegal(move)) { "Illegal move ${move.uci} in ${toFen()}" }
        return applyUnchecked(move, recordHistory = true)
    }

    fun isInCheck(color: PieceColor = sideToMove): Boolean {
        val king = pieces.entries.singleOrNull { it.value == Piece(color, PieceType.KING) }?.key
            ?: return true
        return isSquareAttacked(king, color.opposite)
    }

    /** Attacks, unlike legal moves, include pinned pieces and pawn diagonals on empty squares. */
    fun isSquareAttacked(target: String, by: PieceColor): Boolean = pieces.any { (from, piece) ->
        if (piece.color != by) false else {
            val df = file(target) - file(from)
            val dr = rank(target) - rank(from)
            when (piece.type) {
                PieceType.PAWN -> abs(df) == 1 && dr == if (by == PieceColor.WHITE) 1 else -1
                PieceType.KNIGHT -> abs(df) * abs(dr) == 2
                PieceType.KING -> maxOf(abs(df), abs(dr)) == 1
                PieceType.BISHOP -> abs(df) == abs(dr) && df != 0 && clearRay(from, target)
                PieceType.ROOK -> ((df == 0) xor (dr == 0)) && clearRay(from, target)
                PieceType.QUEEN -> ((abs(df) == abs(dr) && df != 0) || ((df == 0) xor (dr == 0))) && clearRay(from, target)
            }
        }
    }

    /** FIDE repetition ignores a phantom en-passant square when no legal capture is available. */
    val positionKey: String get() = "${placement()} ${turn()} ${rights()} ${legalEnPassant() ?: "-"}"
    val repetitionCount: Int get() = repetitionHistory.count { it == positionKey } + 1
    fun canClaimThreefold(move: ChessMove? = null): Boolean = (move?.let(::apply) ?: this).repetitionCount >= 3
    fun canClaimFiftyMoves(move: ChessMove? = null): Boolean = (move?.let(::apply) ?: this).halfmoveClock >= 100

    fun status(): PositionStatus {
        val checked = isInCheck()
        if (legalMoves().isEmpty()) return if (checked) PositionStatus.CHECKMATE else PositionStatus.STALEMATE
        if (hasInsufficientMaterial()) return PositionStatus.INSUFFICIENT_MATERIAL
        if (repetitionCount >= 5) return PositionStatus.FIVEFOLD_REPETITION
        if (halfmoveClock >= 150) return PositionStatus.SEVENTY_FIVE_MOVES
        return if (checked) PositionStatus.CHECK else PositionStatus.ONGOING
    }

    /** Conservative dead-position detection; do not confuse inability to force mate with inability to mate. */
    fun hasInsufficientMaterial(): Boolean {
        val material = pieces.filterValues { it.type != PieceType.KING }
        if (material.isEmpty()) return true
        if (material.size == 1) return material.values.single().type in listOf(PieceType.BISHOP, PieceType.KNIGHT)
        return material.values.all { it.type == PieceType.BISHOP } &&
            material.keys.map { (file(it) + rank(it)) % 2 }.distinct().size == 1
    }

    fun toFen(): String = "${placement()} ${turn()} ${rights()} ${enPassantTarget ?: "-"} $halfmoveClock $fullmoveNumber"

    private fun legalEnPassant(): String? = enPassantTarget?.takeIf { target ->
        legalMoves().any { it.to == target && pieceAt(it.from)?.type == PieceType.PAWN && it.from[0] != target[0] }
    }

    private fun applyUnchecked(move: ChessMove, recordHistory: Boolean): BoardPosition {
        val piece = pieces.getValue(move.from)
        val updated = pieces.toMutableMap()
        val captured = updated.remove(move.to)
        updated.remove(move.from)
        val enPassant = piece.type == PieceType.PAWN && move.to == enPassantTarget && captured == null && move.from[0] != move.to[0]
        if (enPassant) updated.remove("${move.to[0]}${move.from[1]}")
        if (piece.type == PieceType.KING && abs(file(move.from) - file(move.to)) == 2) {
            val kingSide = move.to[0] == 'g'
            val rookFrom = "${if (kingSide) 'h' else 'a'}${move.from[1]}"
            updated["${if (kingSide) 'f' else 'd'}${move.from[1]}"] = updated.remove(rookFrom)!!
        }
        updated[move.to] = if (move.promotion != null) piece.copy(type = move.promotion) else piece
        val remainingRights = castlingRights.filterNot { right ->
            (piece.type == PieceType.KING && right.color == piece.color) ||
                (piece.type == PieceType.ROOK && right.rookSquare == move.from) || right.rookSquare == move.to
        }.toSet()
        val irreversible = piece.type == PieceType.PAWN || captured != null || remainingRights != castlingRights
        return BoardPosition(
            pieces = updated.toMap(), sideToMove = sideToMove.opposite, lastMove = move,
            castlingRights = remainingRights,
            enPassantTarget = if (piece.type == PieceType.PAWN && abs(rank(move.from) - rank(move.to)) == 2)
                square(file(move.from), (rank(move.from) + rank(move.to)) / 2) else null,
            halfmoveClock = if (piece.type == PieceType.PAWN || captured != null) 0 else halfmoveClock + 1,
            fullmoveNumber = fullmoveNumber + if (sideToMove == PieceColor.BLACK) 1 else 0,
            repetitionHistory = if (!recordHistory || irreversible) emptyList() else repetitionHistory + positionKey,
        )
    }

    private fun pseudoTargets(from: String, piece: Piece): Set<String> {
        val f = file(from)
        val r = rank(from)
        val targets = when (piece.type) {
            PieceType.PAWN -> pawnTargets(f, r, piece.color)
            PieceType.KNIGHT -> KNIGHT_OFFSETS.mapNotNull { (df, dr) -> square(f + df, r + dr) }.toSet()
            PieceType.KING -> KING_OFFSETS.mapNotNull { (df, dr) -> square(f + df, r + dr) }.toSet() + castleTargets(from, piece.color)
            PieceType.BISHOP -> rayTargets(f, r, DIAGONALS)
            PieceType.ROOK -> rayTargets(f, r, STRAIGHTS)
            PieceType.QUEEN -> rayTargets(f, r, DIAGONALS + STRAIGHTS)
        }
        return targets.filter { pieces[it]?.color != piece.color && pieces[it]?.type != PieceType.KING }.toSet()
    }

    private fun pawnTargets(f: Int, r: Int, color: PieceColor): Set<String> = buildSet {
        val step = if (color == PieceColor.WHITE) 1 else -1
        square(f, r + step)?.takeIf { pieces[it] == null }?.let {
            add(it)
            if (r == if (color == PieceColor.WHITE) 2 else 7)
                square(f, r + 2 * step)?.takeIf { pieces[it] == null }?.let(::add)
        }
        for (df in listOf(-1, 1)) square(f + df, r + step)?.let { target ->
            if (pieces[target]?.color == color.opposite) add(target)
            if (target == enPassantTarget && pieces[target] == null &&
                pieces[square(f + df, r)] == Piece(color.opposite, PieceType.PAWN)) add(target)
        }
    }

    private fun castleTargets(from: String, color: PieceColor): Set<String> = buildSet {
        val home = if (color == PieceColor.WHITE) '1' else '8'
        if (from != "e$home" || isInCheck(color)) return@buildSet
        for (right in castlingRights.filter { it.color == color }) {
            if (pieces[right.rookSquare] != Piece(color, PieceType.ROOK)) continue
            val kingSide = right.rookSquare[0] == 'h'
            val emptyFiles = if (kingSide) "fg" else "bcd"
            if (emptyFiles.any { pieces["$it$home"] != null }) continue
            val transit = "${if (kingSide) 'f' else 'd'}$home"
            val destination = "${if (kingSide) 'g' else 'c'}$home"
            val transitBoard = copy(pieces = (pieces - from) + (transit to Piece(color, PieceType.KING)))
            if (!transitBoard.isSquareAttacked(transit, color.opposite)) add(destination)
        }
    }

    private fun rayTargets(f: Int, r: Int, directions: List<Pair<Int, Int>>): Set<String> = buildSet {
        for ((df, dr) in directions) {
            var nf = f + df
            var nr = r + dr
            while (true) {
                val target = square(nf, nr) ?: break
                add(target)
                if (pieces[target] != null) break
                nf += df
                nr += dr
            }
        }
    }

    private fun clearRay(from: String, to: String): Boolean {
        val df = (file(to) - file(from)).compareTo(0)
        val dr = (rank(to) - rank(from)).compareTo(0)
        var f = file(from) + df
        var r = rank(from) + dr
        while (square(f, r) != to) {
            if (pieces[square(f, r)] != null) return false
            f += df
            r += dr
        }
        return true
    }

    private fun placement(): String = (8 downTo 1).joinToString("/") { r ->
        buildString {
            var empty = 0
            for (f in 1..8) {
                val piece = pieces[square(f, r)]
                if (piece == null) empty++ else {
                    if (empty > 0) append(empty)
                    empty = 0
                    append(piece.fenChar())
                }
            }
            if (empty > 0) append(empty)
        }
    }
    private fun turn() = if (sideToMove == PieceColor.WHITE) "w" else "b"
    private fun rights() = CastlingRight.entries.filter { it in castlingRights }.map { it.fen }.joinToString("").ifEmpty { "-" }

    companion object {
        const val START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
        val PROMOTIONS = listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)
        private val DIAGONALS = listOf(1 to 1, 1 to -1, -1 to 1, -1 to -1)
        private val STRAIGHTS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
        private val KNIGHT_OFFSETS = listOf(1 to 2, 2 to 1, 2 to -1, 1 to -2, -1 to -2, -2 to -1, -2 to 1, -1 to 2)
        private val KING_OFFSETS = (-1..1).flatMap { df -> (-1..1).map { dr -> df to dr } }.filterNot { it == 0 to 0 }
        fun starting(): BoardPosition = fromFen(START_FEN)

        fun fromFen(fen: String): BoardPosition {
            val fields = fen.trim().split(Regex("\\s+"))
            require(fields.size == 6) { "FEN requires six fields" }
            val rows = fields[0].split('/')
            require(rows.size == 8) { "FEN requires eight ranks" }
            val pieces = mutableMapOf<String, Piece>()
            rows.forEachIndexed { index, row ->
                var f = 1
                row.forEach { token ->
                    if (token in '1'..'8') f += token.digitToInt() else {
                        val type = when (token.lowercaseChar()) {
                            'p' -> PieceType.PAWN; 'n' -> PieceType.KNIGHT; 'b' -> PieceType.BISHOP
                            'r' -> PieceType.ROOK; 'q' -> PieceType.QUEEN; 'k' -> PieceType.KING
                            else -> throw IllegalArgumentException("Invalid FEN piece: $token")
                        }
                        val target = square(f++, 8 - index) ?: throw IllegalArgumentException("FEN rank too long")
                        require(type != PieceType.PAWN || target[1] !in "18") { "Pawn on promotion rank" }
                        pieces[target] = Piece(if (token.isUpperCase()) PieceColor.WHITE else PieceColor.BLACK, type)
                    }
                }
                require(f == 9) { "FEN rank must contain eight squares" }
            }
            for (color in PieceColor.entries) require(pieces.values.count { it == Piece(color, PieceType.KING) } == 1) { "FEN requires one king per side" }
            val side = when (fields[1]) { "w" -> PieceColor.WHITE; "b" -> PieceColor.BLACK; else -> throw IllegalArgumentException("Invalid FEN turn") }
            val rights = if (fields[2] == "-") emptySet() else fields[2].map { token ->
                CastlingRight.entries.singleOrNull { it.fen == token } ?: throw IllegalArgumentException("Unsupported castling flag: $token")
            }.also { require(it.distinct().size == it.size) { "Duplicate castling flags" } }.toSet()
            for (right in rights) {
                val home = if (right.color == PieceColor.WHITE) "e1" else "e8"
                require(pieces[home] == Piece(right.color, PieceType.KING) && pieces[right.rookSquare] == Piece(right.color, PieceType.ROOK)) { "Inconsistent castling rights" }
            }
            val ep = fields[3].takeUnless { it == "-" }
            if (ep != null) {
                require(ep.matches(Regex("[a-h][36]")) && ep[1] == if (side == PieceColor.WHITE) '6' else '3') { "Invalid en-passant target" }
                val pawnRank = if (side == PieceColor.WHITE) '5' else '4'
                val originRank = if (side == PieceColor.WHITE) '7' else '2'
                require(pieces[ep] == null && pieces["${ep[0]}$pawnRank"] == Piece(side.opposite, PieceType.PAWN) && pieces["${ep[0]}$originRank"] == null) { "Inconsistent en-passant state" }
            }
            val halfmoves = fields[4].toIntOrNull()
            val fullmoves = fields[5].toIntOrNull()
            require(halfmoves != null && halfmoves >= 0 && fullmoves != null && fullmoves >= 1) { "Invalid FEN clocks" }
            val board = BoardPosition(pieces.toMap(), side, castlingRights = rights, enPassantTarget = ep, halfmoveClock = halfmoves, fullmoveNumber = fullmoves)
            require(!board.isInCheck(side.opposite)) { "The side that just moved cannot remain in check" }
            return board
        }

        private fun file(square: String): Int = square[0] - 'a' + 1
        private fun rank(square: String): Int = square[1].digitToInt()
        private fun square(file: Int, rank: Int): String? =
            if (file in 1..8 && rank in 1..8) "${('a'.code + file - 1).toChar()}$rank" else null
    }
}

internal fun Piece.fenChar(): Char {
    val token = when (type) {
        PieceType.PAWN -> 'p'; PieceType.KNIGHT -> 'n'; PieceType.BISHOP -> 'b'
        PieceType.ROOK -> 'r'; PieceType.QUEEN -> 'q'; PieceType.KING -> 'k'
    }
    return if (color == PieceColor.WHITE) token.uppercaseChar() else token
}
