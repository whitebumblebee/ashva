package com.openinglab.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.openinglab.app.ui.IdentifierUiState
import com.openinglab.app.ui.components.ChessBoard
import com.openinglab.app.ui.components.Eyebrow
import com.openinglab.app.ui.components.PrimaryAction
import com.openinglab.app.ui.components.InfoNote
import com.openinglab.app.ui.components.ExpandableText
import com.openinglab.app.ui.theme.Cream
import com.openinglab.app.ui.theme.DeepMoss
import com.openinglab.app.ui.theme.Divider
import com.openinglab.app.ui.theme.Gold
import com.openinglab.app.ui.theme.Ink
import com.openinglab.app.ui.theme.Leaf
import com.openinglab.app.ui.theme.Moss
import com.openinglab.app.ui.theme.MutedCream
import com.openinglab.shared.model.Opening
import com.openinglab.shared.model.PieceColor
import com.openinglab.shared.model.PieceType
import com.openinglab.shared.chess.OpeningMatchKind
import com.openinglab.shared.chess.PositionStatus

@Composable
fun IdentifierScreen(
    state: IdentifierUiState,
    examples: List<Opening>,
    onBack: () -> Unit,
    onSquareTap: (String) -> Unit,
    onUndo: () -> Unit,
    onReset: () -> Unit,
    onExample: (String) -> Unit,
    onOpenMatch: (String) -> Unit,
    onPromotion: (PieceType) -> Unit,
    onCancelPromotion: () -> Unit,
    onImport: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    developerMode: Boolean = false,
) {
    var showImport by rememberSaveable { mutableStateOf(false) }
    var importText by rememberSaveable { mutableStateOf("") }
    var importFen by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 34.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().height(62.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Cream) }
                Text("Opening identifier", color = Cream, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onUndo, enabled = state.moves.isNotEmpty()) { Icon(Icons.AutoMirrored.Rounded.Undo, "Undo", tint = if (state.moves.isNotEmpty()) Cream else MutedCream.copy(alpha = .35f)) }
                IconButton(onReset) { Icon(Icons.Rounded.Refresh, "Reset", tint = Cream) }
                IconButton({ showImport = true }, enabled = !state.isLoading) { Icon(Icons.Rounded.FileUpload, "Import PGN or FEN", tint = Cream) }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Eyebrow("Recreate the game")
                Spacer(Modifier.height(5.dp))
                Text("What did your\nopponent play?", color = Cream, style = MaterialTheme.typography.displayMedium)
                Spacer(Modifier.height(9.dp))
                InfoNote("Enter moves to identify the opening.", "Play the moves from memory. The opening and its branch update as the position takes shape.", color = MutedCream, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(18.dp))
                ChessBoard(
                    position = state.position,
                    selectedSquare = state.selectedSquare,
                    legalTargets = state.legalTargets,
                    onSquareTap = onSquareTap,
                )
                Spacer(Modifier.height(13.dp))
                MoveHistory(state)
                Spacer(Modifier.height(8.dp))
                Text(positionLabel(state), color = Gold, style = MaterialTheme.typography.bodySmall)
                state.error?.let { Text(it, color = Gold, style = MaterialTheme.typography.bodySmall) }
                Spacer(Modifier.height(16.dp))
                AnimatedContent(
                    targetState = state.match.opening,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "opening-result",
                ) { match ->
                    if (match != null) {
                        MatchCard(match, state, developerMode) { onOpenMatch(match.id) }
                    } else {
                        WaitingCard(state, onOpenMatch)
                    }
                }
                Spacer(Modifier.height(22.dp))
                Eyebrow("Or try an example", color = Gold)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    examples.take(5).forEach { opening ->
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .background(Moss)
                                .border(1.dp, Divider, CircleShape)
                                .clickable { onExample(opening.id) }
                                .padding(horizontal = 14.dp, vertical = 9.dp)
                        ) {
                            Text(opening.name, color = Cream, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
    if (state.pendingPromotion != null) {
        AlertDialog(
            onDismissRequest = onCancelPromotion,
            title = { Text("Choose promotion") },
            text = { Text("Promote to a queen, rook, bishop, or knight. The move is applied only after your choice.") },
            confirmButton = {
                Column {
                    listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT).forEach { type ->
                        TextButton({ onPromotion(type) }) { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    }
                }
            },
            dismissButton = { TextButton(onCancelPromotion) { Text("Cancel") } },
        )
    }
    if (showImport) {
        AlertDialog(
            onDismissRequest = { showImport = false },
            title = { Text("Import position or game") },
            text = {
                Column {
                    Row {
                        TextButton({ importFen = false }) { Text(if (!importFen) "✓ PGN" else "PGN") }
                        TextButton({ importFen = true }) { Text(if (importFen) "✓ FEN" else "FEN") }
                    }
                    OutlinedTextField(
                        value = importText, onValueChange = { importText = it },
                        label = { Text(if (importFen) "Six-field FEN" else "One complete PGN game") },
                        minLines = 3, maxLines = 7,
                    )
                    InfoNote("Standard chess · PGN loads the final position.", "Standard chess only. This tool loads a PGN’s final position. Use Players & GM games to replay a full score.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton({ onImport(importText, importFen); showImport = false }, enabled = importText.isNotBlank()) { Text("Load") } },
            dismissButton = { TextButton({ showImport = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun MoveHistory(state: IdentifierUiState) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.moves.isEmpty()) Text("${if (state.position.sideToMove == PieceColor.WHITE) "White" else "Black"} to move — tap a piece", color = MutedCream, style = MaterialTheme.typography.bodyMedium)
        val initialOffset = if (state.initialPosition.sideToMove == PieceColor.WHITE) 0 else 1
        state.sanMoves.forEachIndexed { index, san ->
            val ply = index + initialOffset
            if (ply % 2 == 0 || index == 0) Text("${state.initialPosition.fullmoveNumber + ply / 2}${if (ply % 2 == 0) "." else "..."}", color = MutedCream, style = MaterialTheme.typography.bodySmall)
            Box(Modifier.background(Moss, RoundedCornerShape(8.dp)).padding(horizontal = 9.dp, vertical = 6.dp)) {
                Text(san, color = Cream, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun WaitingCard(state: IdentifierUiState, onOpenMatch: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(DeepMoss, RoundedCornerShape(22.dp)).border(1.dp, Divider, RoundedCornerShape(22.dp)).padding(19.dp)
    ) {
        Eyebrow(if (state.match.kind == OpeningMatchKind.EMPTY) "Ready" else "Available offline catalogs", color = Leaf)
        Spacer(Modifier.height(5.dp))
        Text(
            when (state.match.kind) {
                OpeningMatchKind.EMPTY -> "Enter moves or import a game."
                OpeningMatchKind.AMBIGUOUS -> "${state.match.candidates.size} opening candidates share this position."
                OpeningMatchKind.INVALID -> "The supplied game or position is invalid."
                OpeningMatchKind.OUT_OF_BOOK -> "This position has left the current offline book."
                else -> "No named opening is established in this catalog."
            },
            color = Cream,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(4.dp))
        if (state.match.kind == OpeningMatchKind.AMBIGUOUS) Text(state.match.candidates.joinToString { it.name }, color = MutedCream)
        else InfoNote("Position matching includes transpositions",
            if (state.match.kind == OpeningMatchKind.AMBIGUOUS) state.match.candidates.joinToString { it.name }
                else "Position-based matching handles transpositions. Installed taxonomy names cover their recorded endpoint positions, not every intermediate or possible continuation.",
            color = MutedCream,
            style = MaterialTheme.typography.bodySmall,
        )
        state.match.candidates.forEach { candidate ->
            TextButton({ onOpenMatch(candidate.id) }) { Text("Explore ${candidate.name}", color = Leaf) }
        }
    }
}

@Composable
private fun MatchCard(opening: Opening, state: IdentifierUiState, developerMode: Boolean, onOpen: () -> Unit) {
    val accent = Color(opening.accentHex)
    Column(Modifier.fillMaxWidth().background(accent, RoundedCornerShape(22.dp)).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.background(Ink.copy(alpha = .12f), CircleShape).padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text(if (state.match.kind == OpeningMatchKind.OUT_OF_BOOK) "LAST KNOWN OPENING" else "KNOWN BOOK POSITION", color = Ink, style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.weight(1f))
            Text(opening.eco, color = Ink.copy(alpha = .62f), style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(14.dp))
        Text(opening.name, color = Ink, style = MaterialTheme.typography.headlineLarge)
        Text(state.match.variationName ?: opening.family, color = Ink.copy(alpha = .65f), style = MaterialTheme.typography.titleMedium)
        if (state.match.kind == OpeningMatchKind.OUT_OF_BOOK) {
            Text("Recognized at half-move ${state.match.matchedPly ?: 0}; the current continuation is not a named position in the available catalog.", color = Ink.copy(alpha = .72f), style = MaterialTheme.typography.bodySmall)
        }
        if (state.match.variationNames.size > 1) {
            ExpandableText("Possible labels: ${state.match.variationNames.joinToString()}", color = Ink.copy(alpha = .72f), style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(8.dp))
        ExpandableText(if (!developerMode && opening.provenance != null) "Study this opening from either side and explore its variations."
            else opening.description, color = Ink.copy(alpha = .72f), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        PrimaryAction(if (opening.provenance == null) "Learn this repertoire" else if (developerMode) "Explore source routes" else "Explore variations", onOpen, modifier = Modifier.fillMaxWidth(), icon = Icons.AutoMirrored.Rounded.ArrowForward, color = Ink)
    }
}

private fun positionLabel(state: IdentifierUiState): String {
    if (state.isLoading) return "Validating imported game…"
    val side = if (state.position.sideToMove == PieceColor.WHITE) "White" else "Black"
    return when (state.positionStatus) {
        PositionStatus.CHECK -> "$side is in check"
        PositionStatus.CHECKMATE -> "$side is checkmated"
        PositionStatus.STALEMATE -> "Stalemate"
        PositionStatus.INSUFFICIENT_MATERIAL -> "Draw: insufficient mating material"
        PositionStatus.FIVEFOLD_REPETITION -> "Draw: fivefold repetition"
        PositionStatus.SEVENTY_FIVE_MOVES -> "Draw: seventy-five-move rule"
        PositionStatus.ONGOING -> when {
            state.position.canClaimThreefold() -> "Threefold repetition draw can be claimed"
            state.position.canClaimFiftyMoves() -> "Fifty-move draw can be claimed"
            else -> "$side to move"
        }
    }
}
