package com.openinglab.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import com.openinglab.app.R
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.openinglab.app.ui.theme.BoardDark
import com.openinglab.app.ui.theme.BoardLastMove
import com.openinglab.app.ui.theme.BoardLight
import com.openinglab.app.ui.theme.BoardSelect
import com.openinglab.app.ui.theme.Ink
import com.openinglab.shared.chess.BoardPosition
import com.openinglab.shared.model.Piece
import com.openinglab.shared.model.PieceColor
import com.openinglab.shared.model.PieceType

val LocalBoardCoordinates = androidx.compose.runtime.staticCompositionLocalOf { true }

@Composable
fun ChessBoard(
    position: BoardPosition,
    modifier: Modifier = Modifier,
    perspective: PieceColor = PieceColor.WHITE,
    selectedSquare: String? = null,
    legalTargets: Set<String> = emptySet(),
    hintSquares: Set<String> = emptySet(),
    onSquareTap: (String) -> Unit = {},
    showCoordinates: Boolean = true,
    inputEnabled: Boolean = true,
) {
    val coordinates = showCoordinates && LocalBoardCoordinates.current
    val haptics = LocalHapticFeedback.current
    val files = if (perspective == PieceColor.WHITE) ('a'..'h').toList() else ('a'..'h').reversed()
    val ranks = if (perspective == PieceColor.WHITE) (8 downTo 1).toList() else (1..8).toList()
    val lastSquares = position.lastMove?.let { setOf(it.from, it.to) }.orEmpty()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .shadow(18.dp, RoundedCornerShape(6.dp), ambientColor = Color.Black.copy(alpha = .45f), spotColor = Color.Black.copy(alpha = .6f))
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(6.dp)),
    ) {
        val pieceSize = maxWidth / 8 * .92f
        Column(Modifier.fillMaxSize()) {
            ranks.forEach { rank ->
                Row(Modifier.weight(1f)) {
                    files.forEach { file ->
                        val square = "$file$rank"
                        val light = ((file - 'a') + rank) % 2 == 1
                        val baseColor = if (light) BoardLight else BoardDark
                        val overlay = when {
                            square == selectedSquare -> BoardSelect.copy(alpha = .88f)
                            square in hintSquares -> BoardSelect.copy(alpha = .72f)
                            square in lastSquares -> BoardLastMove
                            else -> Color.Transparent
                        }
                        val labelColor = if (light) BoardDark else BoardLight
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .background(baseColor)
                                .background(overlay)
                                .testTag("square-$square")
                                .semantics(mergeDescendants = true) {
                                    val piece = position.pieceAt(square)
                                    contentDescription = "$square, ${piece?.let { "${it.color.name.lowercase()} ${it.type.name.lowercase()}" } ?: "empty"}"
                                    stateDescription = when {
                                        square in hintSquares -> "Expected move"
                                        square == selectedSquare -> "Selected"
                                        square in legalTargets -> "Legal destination"
                                        else -> ""
                                    }
                                }
                                .clickable(
                                    enabled = inputEnabled,
                                    interactionSource = null,
                                    indication = null,
                                ) {
                                    haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                                    onSquareTap(square)
                                },
                        ) {
                            AnimatedContent(
                                targetState = position.pieceAt(square),
                                transitionSpec = {
                                    (fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) + scaleIn(initialScale = .78f)) togetherWith
                                        (fadeOut() + scaleOut(targetScale = .78f))
                                },
                                label = "piece-$square",
                                modifier = Modifier.align(Alignment.Center),
                            ) { piece ->
                                if (piece != null) ChessPiece(piece, pieceSize)
                            }

                            if (square in legalTargets) {
                                val occupied = position.pieceAt(square) != null
                                Box(
                                    Modifier
                                        .align(Alignment.Center)
                                        .fillMaxSize(if (occupied) .88f else .27f)
                                        .then(
                                            if (occupied) Modifier.border(3.dp, Ink.copy(alpha = .42f), RoundedCornerShape(50))
                                            else Modifier.background(Ink.copy(alpha = .34f), RoundedCornerShape(50))
                                        )
                                )
                            }

                            if (coordinates && file == files.first()) {
                                Text(
                                    text = rank.toString(),
                                    color = labelColor,
                                    style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Black),
                                    modifier = Modifier.align(Alignment.TopStart).padding(start = 3.dp, top = 1.dp),
                                )
                            }
                            if (coordinates && rank == ranks.last()) {
                                Text(
                                    text = file.toString(),
                                    color = labelColor,
                                    style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Black),
                                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 3.dp, bottom = 1.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChessPiece(piece: Piece, size: Dp) {
    // Celtic piece set by Maurizio Monge (MIT), rasterised from the Lichess SVGs; see THIRD_PARTY_NOTICES.md.
    val image = when (piece.color to piece.type) {
        PieceColor.WHITE to PieceType.KING -> R.drawable.piece_wk
        PieceColor.WHITE to PieceType.QUEEN -> R.drawable.piece_wq
        PieceColor.WHITE to PieceType.ROOK -> R.drawable.piece_wr
        PieceColor.WHITE to PieceType.BISHOP -> R.drawable.piece_wb
        PieceColor.WHITE to PieceType.KNIGHT -> R.drawable.piece_wn
        PieceColor.WHITE to PieceType.PAWN -> R.drawable.piece_wp
        PieceColor.BLACK to PieceType.KING -> R.drawable.piece_bk
        PieceColor.BLACK to PieceType.QUEEN -> R.drawable.piece_bq
        PieceColor.BLACK to PieceType.ROOK -> R.drawable.piece_br
        PieceColor.BLACK to PieceType.BISHOP -> R.drawable.piece_bb
        PieceColor.BLACK to PieceType.KNIGHT -> R.drawable.piece_bn
        else -> R.drawable.piece_bp
    }
    Image(painterResource(image), contentDescription = null, modifier = Modifier.size(size).clearAndSetSemantics {})
}

/** Every piece on light and dark squares; preview uses the same composable as the board. */
@androidx.compose.ui.tooling.preview.Preview(name = "Piece contrast · both colours and squares", widthDp = 240, heightDp = 360)
@Composable
private fun PieceContrastPreview() {
    com.openinglab.app.ui.theme.OpeningLabTheme {
        Column {
            PieceType.entries.forEach { type -> Row {
                PieceColor.entries.forEach { color ->
                    listOf(BoardLight, BoardDark).forEach { square ->
                        Box(Modifier.size(60.dp).background(square), contentAlignment = Alignment.Center) {
                            ChessPiece(Piece(color, type), 56.dp)
                        }
                    }
                }
            } }
        }
    }
}
