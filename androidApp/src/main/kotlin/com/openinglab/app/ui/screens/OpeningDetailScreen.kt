package com.openinglab.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.openinglab.app.ui.components.ChessBoard
import com.openinglab.app.ui.components.DifficultyPill
import com.openinglab.app.ui.components.Eyebrow
import com.openinglab.app.ui.components.PrimaryAction
import com.openinglab.app.ui.theme.Cream
import com.openinglab.app.ui.theme.DeepMoss
import com.openinglab.app.ui.theme.Divider
import com.openinglab.app.ui.theme.Gold
import com.openinglab.app.ui.theme.Ink
import com.openinglab.app.ui.theme.Leaf
import com.openinglab.app.ui.theme.Moss
import com.openinglab.app.ui.theme.MutedCream
import com.openinglab.shared.chess.BoardPosition
import com.openinglab.shared.model.ChessMove
import com.openinglab.shared.model.Opening
import com.openinglab.shared.model.OpeningSide
import com.openinglab.shared.model.PieceColor

@Composable
fun OpeningDetailScreen(
    opening: Opening,
    onBack: () -> Unit,
    onStart: (PieceColor, String?) -> Unit,
    modifier: Modifier = Modifier,
    onExploreSources: (() -> Unit)? = null,
) {
    val accent = Color(opening.accentHex)
    val source = opening.provenance
    val teaching = opening.teaching
    var routeQuery by rememberSaveable(opening.id) { mutableStateOf("") }
    var routeSide by rememberSaveable(opening.id) { mutableStateOf(if (opening.side == OpeningSide.BLACK) "BLACK" else "WHITE") }
    val routes = remember(opening.id, routeQuery) { opening.variations.filter {
        routeQuery.isBlank() || (it.name + " " + it.category).contains(routeQuery.trim(), ignoreCase = true)
    } }
    LazyColumn(
        modifier = modifier.testTag("opening-detail"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(accent)
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 26.dp)
            ) {
                Row(Modifier.fillMaxWidth().height(58.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Ink) }
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.background(Ink.copy(alpha = .1f), CircleShape).padding(horizontal = 11.dp, vertical = 7.dp)) {
                        Text(opening.eco, color = Ink, style = MaterialTheme.typography.labelMedium)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(opening.family.uppercase(), color = Ink.copy(alpha = .58f), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(5.dp))
                Text(opening.name, color = Ink, style = MaterialTheme.typography.displayLarge)
                Spacer(Modifier.height(10.dp))
                Text(opening.description, color = Ink.copy(alpha = .72f), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.background(Ink.copy(alpha = .1f), CircleShape).padding(horizontal = 12.dp, vertical = 7.dp)) {
                        Text(if (teaching != null) "Opening course" else if (source == null) "Starter coverage" else "Sourced · ${source.license}", color = Ink, style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.background(Ink.copy(alpha = .1f), CircleShape).padding(horizontal = 12.dp, vertical = 7.dp)) {
                        Text("${opening.variations.size} ${if (source == null) "short lines" else "routes"}", color = Ink, style = MaterialTheme.typography.labelLarge)
                    }
                }
                if ((source == null || teaching != null) && onExploreSources != null) {
                    TextButton(onExploreSources, Modifier.testTag("explore-source-variations")) {
                        Text("Explore sourced variations →", color = Ink)
                    }
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 26.dp)) {
                Eyebrow(if (source == null) "Choose your repertoire" else "Choose a study perspective")
                Spacer(Modifier.height(6.dp))
                Text("Which side are you building?", color = Cream, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SideCard(
                        side = PieceColor.WHITE,
                        label = "Play White",
                        sublabel = if (source != null) "Practice source route" else if (opening.side == OpeningSide.WHITE || opening.side == OpeningSide.BOTH) "Recommended" else "Learn the opponent",
                        accent = accent,
                        onClick = { onStart(PieceColor.WHITE, null) },
                        modifier = Modifier.weight(1f),
                    )
                    SideCard(
                        side = PieceColor.BLACK,
                        label = "Play Black",
                        sublabel = if (source != null) "Practice source route" else if (opening.side == OpeningSide.BLACK || opening.side == OpeningSide.BOTH) "Recommended" else "Learn the defence",
                        accent = accent,
                        onClick = { onStart(PieceColor.BLACK, null) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("The position to know", color = Cream, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(13.dp))
                PositionPreview(opening)
                Spacer(Modifier.height(28.dp))
                Text(if (teaching != null || source == null) "Core ideas for both sides" else "Coverage & provenance", color = Cream, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(12.dp))
                opening.keyIdeas.forEachIndexed { index, idea ->
                    Row(Modifier.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(28.dp).background(accent.copy(alpha = .16f), CircleShape), contentAlignment = Alignment.Center) {
                            Text("${index + 1}", color = accent, style = MaterialTheme.typography.labelLarge)
                        }
                        Text(idea, color = Cream, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
                    }
                }
                if (source != null) {
                    Text("${source.title}\nRevision: ${source.revision}\n${source.attribution}\n${source.url}",
                        color = MutedCream, style = MaterialTheme.typography.bodySmall)
                }
                if (teaching != null) {
                    Text("${teaching.sourceRoutes} named source routes · ${teaching.authoredRoutes} authored study continuations · ${teaching.minPlies}–${teaching.maxPlies} half-moves",
                        color = Leaf, modifier = Modifier.testTag("teaching-coverage"))
                    Text("Ashva-authored ${if (teaching.familySpecificGuide) "family" else "general positional"} plans and rules-derived move observations. Not independently expert-reviewed theory, a historical annotation or a guarantee of winning. Source endpoints remain explicit; authored routes are labeled separately.",
                        color = MutedCream, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(28.dp))
                Text(if (teaching != null) "Study lines & named variations" else if (source == null) "Lines in this repertoire" else "Source routes", color = Cream, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(6.dp))
                Text(if (teaching != null) "Choose a study continuation or any named source route. Every route can be replayed, practiced and explored from either perspective." else if (source == null) "Authored starter lines, not the full repertoire or engine-verified winning theory." else
                    "${source.minPlies}–${source.maxPlies} half-moves. All ${opening.variations.size} routes are available; source names are not reviewed strategy or engine recommendations.",
                    color = MutedCream, style = MaterialTheme.typography.bodySmall)
                if (source != null) {
                    OutlinedTextField(routeQuery, { routeQuery = it }, label = { Text("Find a variation or ECO") },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag("route-search"), singleLine = true)
                    Row {
                        TextButton({ routeSide = "WHITE" }, Modifier.testTag("route-white")) { Text(if (routeSide == "WHITE") "✓ White POV" else "White POV") }
                        TextButton({ routeSide = "BLACK" }, Modifier.testTag("route-black")) { Text(if (routeSide == "BLACK") "✓ Black POV" else "Black POV") }
                    }
                    Text("${routes.size} of ${opening.variations.size} routes", color = MutedCream, modifier = Modifier.testTag("route-count"))
                }
                Spacer(Modifier.height(12.dp))
            }
        }

        itemsIndexed(routes, key = { _, variation -> variation.id }) { index, variation ->
            VariationCard(
                number = index + 1,
                title = variation.name,
                category = variation.category,
                description = variation.description,
                accent = accent,
                onClick = {
                    val defaultSide = PieceColor.valueOf(routeSide)
                    onStart(defaultSide, variation.id)
                },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp).testTag("route-${variation.id}"),
            )
        }

        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 26.dp)) {
                if (opening.historicalGame != null) {
                    Eyebrow("Historical preview · unverified metadata", color = Gold)
                    Spacer(Modifier.height(8.dp))
                    HistoricalGameCard(opening)
                    Text("Example card only: no source-verified score or game replay is available here yet.",
                        color = MutedCream, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(24.dp))
                PrimaryAction(
                    text = if (teaching != null) "Start opening course" else if (source != null) "Practice source route" else "Start this repertoire",
                    onClick = {
                        val side = if (opening.side == OpeningSide.BLACK) PieceColor.BLACK else PieceColor.WHITE
                        onStart(side, null)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Rounded.PlayArrow,
                    color = accent,
                )
            }
        }
    }
}

@Composable
private fun SideCard(side: PieceColor, label: String, sublabel: String, accent: Color, onClick: () -> Unit, modifier: Modifier) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick),
        color = DeepMoss,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Divider),
    ) {
        Column(Modifier.padding(17.dp)) {
            Text(if (side == PieceColor.WHITE) "♔" else "♚", color = accent, style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(10.dp))
            Text(label, color = Cream, style = MaterialTheme.typography.titleMedium)
            Text(sublabel, color = MutedCream, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PositionPreview(opening: Opening) {
    val previewMoves = remember(opening.id) { opening.mainLine.steps.take(minOf(10, opening.mainLine.steps.size)) }
    val position = remember(previewMoves) {
        previewMoves.fold(BoardPosition.starting()) { board, move -> board.apply(ChessMove.fromUci(move.uci)) }
    }
    Surface(color = DeepMoss, shape = RoundedCornerShape(22.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Divider)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            ChessBoard(position, modifier = Modifier.size(148.dp), showCoordinates = false)
            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                Eyebrow("After ${previewMoves.size / 2}. ${previewMoves.lastOrNull()?.san.orEmpty()}")
                Spacer(Modifier.height(7.dp))
                Text(opening.mainLine.name, color = Cream, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(5.dp))
                Text(opening.mainLine.description, color = MutedCream, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun VariationCard(number: Int, title: String, category: String, description: String, accent: Color, onClick: () -> Unit, modifier: Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick),
        color = DeepMoss,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Divider),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(accent.copy(alpha = .14f), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                Text(number.toString().padStart(2, '0'), color = accent, style = MaterialTheme.typography.labelLarge)
            }
            Column(Modifier.padding(horizontal = 13.dp).weight(1f)) {
                Eyebrow(category, color = accent)
                Text(title, color = Cream, style = MaterialTheme.typography.titleMedium)
                Text(description, color = MutedCream, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = MutedCream, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun HistoricalGameCard(opening: Opening) {
    val game = opening.historicalGame ?: return
    Surface(color = Moss, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(19.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.History, null, tint = Gold)
                Text("  ${game.event}, ${game.year}", color = MutedCream, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(13.dp))
            Text("${game.white}  ${game.result}  ${game.black}", color = Cream, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(game.lesson, color = MutedCream, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
