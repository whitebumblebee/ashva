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

    // The public map and data-class API stay unchanged. Only a real played move copies it.
    // Each legal-move scan owns one scratch array, so concurrent readers never share mutations.
    private val cells: Array<Piece?> by lazy {
        arrayOfNulls<Piece>(64).also { board -> pieces.forEach { (square, piece) -> board[index(square)] = piece } }
    }

    fun legalMoves(from: String? = null): List<ChessMove> {
        val board = cells.copyOf()
        val king = kingIndex(board, sideToMove)
        if (king < 0) return emptyList()
        val moves = ArrayList<ChessMove>(32)
        for ((source, piece) in pieces) {
            if (piece.color != sideToMove || (from != null && source != from)) continue
            for (target in pseudoTargets(source, piece)) {
                // Promotion type cannot change whether our own king is attacked.
                val move = ChessMove(source, target)
                if (!kingSafeAfter(move, board, king)) continue
                if (piece.type == PieceType.PAWN && target[1] in "18") {
                    for (promotion in PROMOTIONS) moves.add(ChessMove(source, target, promotion))
                } else moves.add(move)
            }
        }
        return moves
    }

    private fun kingSafeAfter(move: ChessMove, board: Array<Piece?>, king: Int): Boolean {
        val from = index(move.from)
        val to = index(move.to)
        val moving = requireNotNull(board[from])
        val captured = board[to]
        val ep = moving.type == PieceType.PAWN && move.to == enPassantTarget && captured == null && move.from[0] != move.to[0]
        val epSquare = if (ep) (from / 8) * 8 + to % 8 else -1
        val epPiece = if (ep) board[epSquare] else null
        val castle = moving.type == PieceType.KING && abs(from % 8 - to % 8) == 2
        val rookFrom = if (castle) (from / 8) * 8 + if (to % 8 == 6) 7 else 0 else -1
        val rookTo = if (castle) (from / 8) * 8 + if (to % 8 == 6) 5 else 3 else -1
        val rook = if (castle) board[rookFrom] else null
        val rookDestination = if (castle) board[rookTo] else null
        board[from] = null
        board[to] = moving
        if (ep) board[epSquare] = null
        if (castle) { board[rookFrom] = null; board[rookTo] = rook }
        val safe = !attacked(board, if (moving.type == PieceType.KING) to else king, moving.color.opposite)
        board[from] = moving
        board[to] = captured
        if (ep) board[epSquare] = epPiece
        if (castle) { board[rookFrom] = rook; board[rookTo] = rookDestination }
        return safe
    }

    fun legalTargets(from: String): Set<String> = legalMoves(from).map { it.to }.toSet()
    fun isLegal(move: ChessMove): Boolean = move in legalMoves(move.from)

    /** Reject illegal moves rather than silently corrupting the board. Promotion must be explicit. */
    fun apply(move: ChessMove): BoardPosition {
        require(isLegal(move)) { "Illegal move ${move.uci} in ${toFen()}" }
        return applyUnchecked(move, recordHistory = true)
    }

    /** Internal only: legal-list membership or identical checksum-verified, previously validated pack bytes. */
    internal fun advanceKnownLegal(move: ChessMove): BoardPosition = applyUnchecked(move, recordHistory = true)

    fun isInCheck(color: PieceColor = sideToMove): Boolean {
        val king = kingIndex(cells, color)
        return king < 0 || attacked(cells, king, color.opposite)
    }

    /** Attacks, unlike legal moves, include pinned pieces and pawn diagonals on empty squares. */
    fun isSquareAttacked(target: String, by: PieceColor): Boolean = attacked(cells, index(target), by)

    /** Geometric attackers include pinned pieces, exactly like isSquareAttacked; not legal captures. */
    fun attackersOf(target: String, by: PieceColor): List<String> = pieces.entries
        .filter { (from, piece) -> piece.color == by && attacksSquare(from, piece, target) }
        .map { it.key }.sorted()

    private fun attacksSquare(from: String, piece: Piece, target: String): Boolean {
        val df = file(target) - file(from)
        val dr = rank(target) - rank(from)
        return when (piece.type) {
            PieceType.PAWN -> abs(df) == 1 && dr == if (piece.color == PieceColor.WHITE) 1 else -1
            PieceType.KNIGHT -> abs(df) * abs(dr) == 2
            PieceType.KING -> maxOf(abs(df), abs(dr)) == 1
            PieceType.BISHOP -> abs(df) == abs(dr) && df != 0 && clearRay(from, target)
            PieceType.ROOK -> ((df == 0) xor (dr == 0)) && clearRay(from, target)
            PieceType.QUEEN -> ((abs(df) == abs(dr) && df != 0) || ((df == 0) xor (dr == 0))) && clearRay(from, target)
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
        val sourceRank = rank(target) - if (sideToMove == PieceColor.WHITE) 1 else -1
        // Only the two adjacent pawns can capture here; still use the full king-safety check.
        listOf(-1, 1).any { delta ->
            square(file(target) + delta, sourceRank)?.let { from ->
                pieceAt(from) == Piece(sideToMove, PieceType.PAWN) && isLegal(ChessMove(from, target))
            } == true
        }
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
            // `updated` has no remaining mutable owner; avoid copying the played board twice.
            pieces = updated, sideToMove = sideToMove.opposite, lastMove = move,
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
            if (kingSafeAfter(ChessMove(from, transit), cells.copyOf(), index(from))) add(destination)
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
        private val whitespace = Regex("\\s+")
        private val DIAGONALS = listOf(1 to 1, 1 to -1, -1 to 1, -1 to -1)
        private val STRAIGHTS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
        private val KNIGHT_OFFSETS = listOf(1 to 2, 2 to 1, 2 to -1, 1 to -2, -1 to -2, -2 to -1, -2 to 1, -1 to 2)
        private val KING_OFFSETS = (-1..1).flatMap { df -> (-1..1).map { dr -> df to dr } }.filterNot { it == 0 to 0 }
        fun starting(): BoardPosition = fromFen(START_FEN)

        fun fromFen(fen: String): BoardPosition {
            val fields = fen.trim().split(whitespace)
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
                require(ep.length == 2 && ep[0] in 'a'..'h' && ep[1] in "36" && ep[1] == if (side == PieceColor.WHITE) '6' else '3') { "Invalid en-passant target" }
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

        private val SQUARES = Array(64) { i -> "${('a'.code + i % 8).toChar()}${i / 8 + 1}" }
        private val PAWN_FILES = intArrayOf(-1, 1)
        private val RAYS = DIAGONALS + STRAIGHTS
        private fun index(square: String): Int = (square[1] - '1') * 8 + (square[0] - 'a')
        private fun kingIndex(board: Array<Piece?>, color: PieceColor): Int {
            var king = -1
            for (i in board.indices) {
                val piece = board[i] ?: continue
                if (piece.color == color && piece.type == PieceType.KING) {
                    if (king >= 0) return -1
                    king = i
                }
            }
            return king
        }
        private fun attacked(board: Array<Piece?>, target: Int, by: PieceColor): Boolean {
            val f = target % 8
            val r = target / 8
            val pawnRank = r - if (by == PieceColor.WHITE) 1 else -1
            if (pawnRank in 0..7) for (df in PAWN_FILES) {
                val pf = f + df
                if (pf in 0..7) {
                    val piece = board[pawnRank * 8 + pf]
                    if (piece?.color == by && piece.type == PieceType.PAWN) return true
                }
            }
            for ((df, dr) in KNIGHT_OFFSETS) {
                val nf = f + df; val nr = r + dr
                if (nf in 0..7 && nr in 0..7) {
                    val piece = board[nr * 8 + nf]
                    if (piece?.color == by && piece.type == PieceType.KNIGHT) return true
                }
            }
            for ((df, dr) in RAYS) {
                var nf = f + df; var nr = r + dr; var distance = 1
                while (nf in 0..7 && nr in 0..7) {
                    val piece = board[nr * 8 + nf]
                    if (piece != null) {
                        if (piece.color == by && (piece.type == PieceType.QUEEN ||
                            piece.type == (if (df == 0 || dr == 0) PieceType.ROOK else PieceType.BISHOP) ||
                            (distance == 1 && piece.type == PieceType.KING))) return true
                        break
                    }
                    nf += df; nr += dr; distance++
                }
            }
            return false
        }

        private fun file(square: String): Int = square[0] - 'a' + 1
        private fun rank(square: String): Int = square[1].digitToInt()
        private fun square(file: Int, rank: Int): String? =
            if (file in 1..8 && rank in 1..8) SQUARES[(rank - 1) * 8 + file - 1] else null
    }
}

internal fun Piece.fenChar(): Char {
    val token = when (type) {
        PieceType.PAWN -> 'p'; PieceType.KNIGHT -> 'n'; PieceType.BISHOP -> 'b'
        PieceType.ROOK -> 'r'; PieceType.QUEEN -> 'q'; PieceType.KING -> 'k'
    }
    return if (color == PieceColor.WHITE) token.uppercaseChar() else token
}
