package com.openinglab.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.openinglab.app.content.BundledContent
import com.openinglab.app.ui.AppUiState
import com.openinglab.app.ui.components.InfoNote
import com.openinglab.app.ui.theme.*

@Composable
fun OfflineLibraryScreen(state: AppUiState, onInstall: (String) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier.testTag("offline-library-list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            TextButton(onBack) { Text("Back", color = Leaf) }
            Text("Offline library", color = Cream, style = MaterialTheme.typography.headlineLarge)
            InfoNote("Install bundled packs for offline study.", "Install reviewed data bundled with this build. No network or account is required. Opening names install automatically; the main catalog adds Ashva-authored plans, move facts and study continuations. Data packs alone are not complete theory or expert-reviewed courses.",
                color = MutedCream, style = MaterialTheme.typography.bodyMedium)
        }
        state.packError?.let { error -> item { Text(error, color = Gold, modifier = Modifier.testTag("pack-error")) } }
        items(BundledContent.choices, key = { it.sourceId }) { choice ->
            val job = state.packs.firstOrNull { it.sourceId == choice.sourceId }
            val installed = state.installedPacks.firstOrNull { it.manifest.source.id == choice.sourceId }
            val status = when (job?.state) {
                "LOADING" -> "Installing…"
                "ERROR" -> "Installation failed"
                "DOWNLOADED" -> "Installed for offline use"
                else -> "Not installed"
            }
            Card(colors = CardDefaults.cardColors(containerColor = Moss)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(choice.title, color = Cream, style = MaterialTheme.typography.titleLarge)
                    InfoNote(choice.summary.substringBefore(". "), choice.summary, color = MutedCream)
                    Text(status, color = Leaf, modifier = Modifier.testTag("pack-status-${choice.sourceId}"))
                    if (state.developerMode) Text("License: ${choice.license}", color = MutedCream)
                    job?.error?.let { Text(it, color = Gold) }
                    if (installed != null) {
                        Text("${installed.manifest.coverage.acceptedRecords} records stored" + if (state.developerMode) " · ${installed.manifest.packId}" else "", color = Cream)
                        if (state.developerMode) Text("${installed.manifest.coverage.inputRecords} inputs attempted · ${installed.manifest.coverage.duplicates} duplicates · ${installed.manifest.coverage.quarantined} quarantined. Rejected scores are excluded from observations and teaching; their disposition remains in the source pack.",
                            color = MutedCream, modifier = Modifier.testTag("pack-dispositions-${choice.sourceId}"))
                        if (state.developerMode) Text(installed.notices, color = MutedCream, style = MaterialTheme.typography.bodySmall)
                    }
                    if (job?.state != "DOWNLOADED" || job.activePackId != choice.packId) {
                        Button(onClick = { onInstall(choice.sourceId) },
                            enabled = state.packs.none { it.state == "LOADING" },
                            modifier = Modifier.testTag("install-${choice.sourceId}")) {
                            Text(if (job?.state == "ERROR") "Retry installation" else "Install bundled pack")
                        }
                    }
                }
            }
        }
        item { InfoNote("Find courses in Learn and games in Players & GM games.", "Find guided courses in Learn or Explore → All; raw routes remain in Explore → Sourced. Replay or practice either color; Build / edit my repertoire saves your move/reply choices. The editor shows observed moves from installed broadcast scores, with sample/source limits. Separate offline engine alternatives are available in lessons. The installed game library is a finite sample; player identities and course explanations are not independently reviewed.", color = MutedCream) }
    }
}
