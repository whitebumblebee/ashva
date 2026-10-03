// SPDX-License-Identifier: Apache-2.0
package com.openinglab.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.openinglab.app.ui.*
import com.openinglab.app.ui.theme.*
import com.openinglab.shared.games.*
import com.openinglab.shared.model.PieceColor

@Composable
fun GameLibraryScreen(state: GameLearningUiState, onBack: () -> Unit, onSources: () -> Unit,
    onFilter: (GameLibraryFilter) -> Unit, onFollow: (String, Boolean) -> Unit,
    onImport: (String) -> Unit, onOpen: (String, PieceColor) -> Unit, onRetry: () -> Unit,
    modifier: Modifier = Modifier) {
    var importDialog by rememberSaveable { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") } // Never put a potentially large private PGN in a saved-state Bundle.
    var draftError by remember { mutableStateOf(false) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var shownPlayers by rememberSaveable { mutableIntStateOf(30) }
    val ready = state.library as? GameLibraryUiState.Ready
    val selected = state.filter.playerId
    LazyColumn(modifier.testTag("game-library-list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item("header") {
            TextButton(onBack) { Text("Back", color = Leaf) }
            Text("Players & GM games", color = Cream, style = MaterialTheme.typography.headlineMedium)
            Text("Full original scores from installed sources and your private PGNs. Names, titles and FIDE IDs are reported, not independently authenticated; this is not a complete career archive.", color = MutedCream)
            Row {
                TextButton(onSources, Modifier.testTag("game-library-sources")) { Text("Offline sources") }
                TextButton({ importDialog = true }, Modifier.testTag("game-import")) { Text("Import private PGN") }
            }
            state.message?.let { Text(it, color = Gold, modifier = Modifier.testTag("game-library-message")) }
            if (state.importing) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("game-import-loading"))
        }
        item("search") {
            OutlinedTextField(state.filter.query, { onFilter(state.filter.copy(query = it.take(240))) }, label = { Text("Search games or players") },
                modifier = Modifier.fillMaxWidth().testTag("game-search"), singleLine = true)
            Row {
                FilterChip(state.filter.followedOnly, { onFilter(state.filter.copy(followedOnly = !state.filter.followedOnly)) }, { Text("Followed players") }, Modifier.testTag("game-followed-only"))
                Spacer(Modifier.width(8.dp))
                TextButton({ advanced = !advanced }, Modifier.testTag("game-filters")) { Text("Filters") }
            }
            if (advanced) {
                OutlinedTextField(state.filter.event, { onFilter(state.filter.copy(event = it.take(240))) }, label = { Text("Event contains") },
                    singleLine = true, modifier = Modifier.fillMaxWidth().testTag("game-event-filter"))
                OutlinedTextField(state.filter.opening, { onFilter(state.filter.copy(opening = it.take(240))) }, label = { Text("Opening or ECO contains") },
                    singleLine = true, modifier = Modifier.fillMaxWidth().testTag("game-opening-filter"))
                // Free text accepts incomplete input; only valid years are submitted, never guessed.
                var yearText by rememberSaveable(state.filter.year) { mutableStateOf(state.filter.year?.toString().orEmpty()) }
                OutlinedTextField(yearText, { yearText = it.take(4) }, label = { Text("Year (1000–9999, blank for all)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth().testTag("game-year-filter"))
                TextButton({ onFilter(state.filter.copy(year = yearText.toIntOrNull())) }, enabled = yearText.isBlank() || yearText.toIntOrNull() in 1000..9999,
                    modifier = Modifier.testTag("game-apply-year")) { Text("Apply year") }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(null, "1-0", "0-1", "1/2-1/2", "*").forEach { result ->
                        FilterChip(state.filter.result == result, { onFilter(state.filter.copy(result = result)) }, { Text(result ?: "All results") })
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(state.filter.playerColor == null, { onFilter(state.filter.copy(playerColor = null)) }, { Text("Either color") })
                    PieceColor.entries.forEach { side -> FilterChip(state.filter.playerColor == side,
                        { onFilter(state.filter.copy(playerColor = side)) }, { Text("Selected player as ${side.name.lowercase()}") }, enabled = selected != null) }
                }
                FilterChip(state.filter.reportedGmOnly, { onFilter(state.filter.copy(reportedGmOnly = !state.filter.reportedGmOnly)) }, { Text("Source reports a GM title") })
                Text("Color filters require selecting a player below. Missing years/titles are unknown, not inferred from event or name.", color = MutedCream)
                TextButton({ onFilter(GameLibraryFilter()); yearText = "" }, Modifier.testTag("game-clear-filters")) { Text("Clear filters") }
            }
        }
        item("followers-header") {
            Text("Your followed players · ${state.followers.size}", color = Leaf, style = MaterialTheme.typography.titleMedium)
            if (state.followers.isEmpty()) Text("No players added yet. Search and follow the players you choose; Ashva does not preselect favorites.", color = MutedCream,
                modifier = Modifier.testTag("game-empty-following"))
        }
        items(state.followers, key = { "followed-${it.id}" }) { player ->
            Text(player.names.joinToString(" / "), color = Cream)
            Text(player.identityStatus, color = MutedCream, style = MaterialTheme.typography.bodySmall)
            if (ready != null && ready.library.players.none { it.reference.id == player.id }) Text("No score for this retained identity is currently installed. Following has not been removed.", color = Gold)
            Row {
                TextButton({ onFilter(state.filter.copy(playerId = player.id, playerColor = null)) }, Modifier.testTag("game-select-followed-${player.id}")) { Text("Show games") }
                TextButton({ onFollow(player.id, false) }, Modifier.testTag("game-unfollow-${player.id}")) { Text("Unfollow (keep games)") }
            }
        }
        when (val library = state.library) {
            GameLibraryUiState.Loading -> item("loading") { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Checking exact source versions…", color = Cream, modifier = Modifier.testTag("game-library-loading")) }
            GameLibraryUiState.Missing -> item("missing") { Text("No full scores installed. Use Offline sources for reviewed January/April packs, or import one private PGN. Unknown history is not an empty career.", color = Gold, modifier = Modifier.testTag("game-library-missing")) }
            is GameLibraryUiState.Error -> item("error") { Text(library.message, color = Gold, modifier = Modifier.testTag("game-library-error")); TextButton(onRetry) { Text("Retry library") } }
            is GameLibraryUiState.Ready -> {
                item("players-header") { Text("Matching players · ${library.players.size}", color = Leaf, style = MaterialTheme.typography.titleMedium)
                    Text("Showing ${minOf(shownPlayers, library.players.size)}. Exact source/FIDE identities stay separate from similar names and private PGNs.", color = MutedCream) }
                items(library.players.take(shownPlayers), key = { "player-${it.reference.id}" }) { player ->
                    val ref = player.reference
                    Text(ref.names.joinToString(" / "), color = Cream)
                    Text("${player.scoreIds.size} score records · ${ref.identityStatus}${ref.fideId?.let { " · source FIDE ID $it" }.orEmpty()} · reported titles: ${player.reportedTitles.joinToString().ifEmpty { "unknown" }}", color = MutedCream, style = MaterialTheme.typography.bodySmall)
                    Row {
                        TextButton({ onFilter(state.filter.copy(playerId = ref.id, playerColor = null)) }, Modifier.testTag("game-select-player-${ref.id}")) { Text("Show games") }
                        val followed = state.followers.any { it.id == ref.id }
                        TextButton({ onFollow(ref.id, !followed) }, Modifier.testTag("game-follow-${ref.id}")) { Text(if (followed) "Unfollow" else "Follow") }
                    }
                }
                if (shownPlayers < library.players.size) item("more-players") { TextButton({ shownPlayers += 30 }, Modifier.testTag("game-more-players")) { Text("More players") } }
                item("scores-header") {
                    Text("${library.scores.size} matching / ${library.library.scores.size} score records", color = Leaf, modifier = Modifier.testTag("game-library-count"))
                    if (library.searching) LinearProgressIndicator(Modifier.fillMaxWidth())
                    selected?.let { Text("Selected identity: $it", color = Gold); TextButton({ onFilter(state.filter.copy(playerId = null, playerColor = null)) }) { Text("All players") } }
                    if (library.scores.isEmpty()) Text("No score matches these filters in the installed sample. This is not proof that those games were never played.", color = MutedCream)
                }
                items(library.scores, key = { "score-${it.id}" }) { score ->
                    Column(Modifier.fillMaxWidth().testTag("game-card-${score.id}"), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("${score.white.name} – ${score.black.name}", color = Cream, style = MaterialTheme.typography.titleMedium)
                        Text("${score.event} · ${score.tags["Date"] ?: score.tags["UTCDate"] ?: "date unknown"} · ${score.result} · ${score.san.size} half-moves", color = MutedCream)
                        Text(score.openingNames.joinToString(" / ").ifEmpty { "Opening not identified in this source" }, color = MutedCream)
                        Text(when (score) {
                            is LibraryScore.Private -> "PRIVATE USER PGN · unverified annotations/identities · not redistributed or included in public observed counts. ${score.game.id}"
                            is LibraryScore.Broadcast -> score.origins.joinToString("\n") { "${it.manifest.source.title} · ${it.manifest.source.license}\n${it.manifest.packId} · manifest ${it.manifestSha256}" } + "\nRecord ${score.game.id}"
                        }, color = MutedCream, style = MaterialTheme.typography.bodySmall)
                        Row {
                            PieceColor.entries.forEach { side -> TextButton({ onOpen(score.id, side) }, enabled = !library.searching,
                                modifier = Modifier.testTag("game-open-${side.name}-${score.id}")) { Text("Replay as ${side.name.lowercase()}") } }
                        }
                    }
                }
            }
        }
    }
    if (importDialog) AlertDialog(onDismissRequest = { importDialog = false }, title = { Text("Import one private PGN") }, text = {
        Column {
            Text("Local only. Keep permissions for any supplied commentary; Ashva does not redistribute it. Standard chess, ≤256 KiB, ≤4096 mainline half-moves; archives are rejected explicitly.")
            OutlinedTextField(draft, { if (it.length <= PrivateGameRecord.MAX_BYTES) { draft = it; draftError = false } else draftError = true },
                label = { Text("Paste full PGN") }, modifier = Modifier.heightIn(max = 240.dp).testTag("game-pgn-input"))
            if (draftError) Text("Paste is too large; the replacement was rejected, not truncated.")
        }
    }, confirmButton = { TextButton({ onImport(draft); importDialog = false }, enabled = draft.isNotBlank() && !draftError && !state.importing,
        modifier = Modifier.testTag("game-pgn-confirm")) { Text("Import locally") } }, dismissButton = { TextButton({ importDialog = false }) { Text("Cancel") } })
}
