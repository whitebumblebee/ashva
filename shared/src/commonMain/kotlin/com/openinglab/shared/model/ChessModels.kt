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
        require(validSquare(from) && validSquare(to) && from != to) { "Invalid move squares: $from$to" }
        require(promotion == null || promotion == PieceType.QUEEN || promotion == PieceType.ROOK || promotion == PieceType.BISHOP || promotion == PieceType.KNIGHT) { "Invalid promotion piece" }
    }

    val uci: String = from + to + when (promotion) {
        PieceType.QUEEN -> "q"
        PieceType.ROOK -> "r"
        PieceType.BISHOP -> "b"
        PieceType.KNIGHT -> "n"
        else -> ""
    }

    companion object {
        private fun validSquare(value: String): Boolean =
            value.length == 2 && value[0] in 'a'..'h' && value[1] in '1'..'8'

        fun fromUci(value: String): ChessMove {
            require(value.length in 4..5 && value[0] in 'a'..'h' && value[1] in '1'..'8' &&
                value[2] in 'a'..'h' && value[3] in '1'..'8' && (value.length == 4 || value[4] in "qrbn")) { "Invalid UCI move: $value" }
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
    /** Provenance label shown with generated course text, e.g. "Generated · engine-checked". */
    val label: String = "",
    /** Who chose this move, e.g. "Chosen in: 2600+ 711 · Lichess 1600–2200 185 games". */
    val players: String = "",
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
    val authoredContinuation: Boolean = false,
    /** Set only for deep-course content; null keeps the historical authored/sourced mapping. */
    val origin: VariationOrigin? = null,
)

/** Distinguishes generated course lines from an immutable original game and its engine branches. */
enum class VariationOrigin { COURSE_LINE, ORIGINAL_GAME, ENGINE_LINE }

data class TeachingCoverage(
    val version: String,
    val sourceRoutes: Int,
    val authoredRoutes: Int,
    val familySpecificGuide: Boolean,
    val minPlies: Int,
    val maxPlies: Int,
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

data class OpeningProvenance(
    val packId: String, val revision: String, val title: String, val url: String,
    val license: String, val attribution: String, val minPlies: Int, val maxPlies: Int,
)

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
    val historicalGame: HistoricalGame? = null,
    val recognitionPly: Int = 2,
    val provenance: OpeningProvenance? = null,
    val teaching: TeachingCoverage? = null,
) {
    val mainLine: Variation get() = variations.first()
    val lessonCount: Int get() = variations.sumOf { it.steps.size / 2 }
}
