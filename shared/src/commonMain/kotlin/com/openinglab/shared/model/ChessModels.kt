package com.openinglab.shared.model

enum class PieceColor { WHITE, BLACK;
    val opposite: PieceColor get() = if (this == WHITE) BLACK else WHITE
}

enum class PieceType { KING, QUEEN, ROOK, BISHOP, KNIGHT, PAWN }

data class Piece(val color: PieceColor, val type: PieceType)

data class ChessMove(
    val from: String,
    val to: String,
    val promotion: PieceType? = null,
) {
    init {
        require(from.matches(Regex("[a-h][1-8]")) && to.matches(Regex("[a-h][1-8]")) && from != to) { "Invalid move squares: $from$to" }
        require(promotion == null || promotion in listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)) { "Invalid promotion piece" }
    }

    val uci: String = from + to + when (promotion) {
        PieceType.QUEEN -> "q"
        PieceType.ROOK -> "r"
        PieceType.BISHOP -> "b"
        PieceType.KNIGHT -> "n"
        else -> ""
    }

    companion object {
        fun fromUci(value: String): ChessMove {
            require(value.matches(Regex("[a-h][1-8][a-h][1-8][qrbn]?"))) { "Invalid UCI move: $value" }
            val promotion = when (value.getOrNull(4)) {
                'q' -> PieceType.QUEEN
                'r' -> PieceType.ROOK
                'b' -> PieceType.BISHOP
                'n' -> PieceType.KNIGHT
                else -> null
            }
            return ChessMove(value.substring(0, 2), value.substring(2, 4), promotion)
        }
    }
}

data class MoveStep(
    val uci: String,
    val san: String,
    val title: String,
    val explanation: String,
    val principle: String,
)

data class Variation(
    val id: String,
    val name: String,
    val category: String,
    val description: String,
    val steps: List<MoveStep>,
    val recognitionPly: Int = steps.size,
    val identifiesOpening: Boolean = true,
    val whiteIdea: String = "",
    val blackIdea: String = "",
)

data class HistoricalGame(
    val white: String,
    val black: String,
    val event: String,
    val year: Int,
    val result: String,
    val lesson: String,
)

enum class OpeningSide { WHITE, BLACK, BOTH }

enum class Difficulty { FOUNDATION, INTERMEDIATE, ADVANCED }

data class Opening(
    val id: String,
    val name: String,
    val family: String,
    val eco: String,
    val side: OpeningSide,
    val difficulty: Difficulty,
    val description: String,
    val identity: String,
    val accentHex: Long,
    val progress: Int,
    val keyIdeas: List<String>,
    val variations: List<Variation>,
    val historicalGame: HistoricalGame,
    val recognitionPly: Int = 2,
) {
    val mainLine: Variation get() = variations.first()
    val lessonCount: Int get() = variations.sumOf { it.steps.size / 2 }
}
