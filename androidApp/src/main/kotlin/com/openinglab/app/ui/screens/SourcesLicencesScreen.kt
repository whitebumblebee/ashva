// SPDX-License-Identifier: Apache-2.0
package com.openinglab.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.openinglab.app.ui.AppUiState
import com.openinglab.app.ui.components.ExpandableText
import com.openinglab.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SourcesLicencesScreen(state: AppUiState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val assets = LocalContext.current.assets
    val notices by produceState<List<Pair<String, String>>>(emptyList()) {
        value = withContext(Dispatchers.IO) {
            listOf("Third-party notices" to "content/legal/THIRD_PARTY_NOTICES.md", "Ashva notices" to "content/legal/NOTICE",
                "Stockfish authors" to "engine/AUTHORS", "Stockfish licence" to "engine/COPYING.txt",
                "LLVM notices" to "engine/LLVM-NOTICE.txt", "NDK notices" to "engine/NDK-NOTICE.txt").mapNotNull { (title, path) ->
                runCatching { title to assets.open(path).bufferedReader().use { it.readText() } }.getOrNull()
            }
        }
    }
    LazyColumn(modifier.testTag("sources-licences"), contentPadding = PaddingValues(18.dp, 0.dp, 18.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { CourseHeader("Sources & licences", onBack) }
        state.deepCourseSummaries.distinctBy { it.course.id }.forEach { chapter -> item(key = chapter.course.id) {
            CourseCard {
                Text(chapter.course.title, color = Cream, style = MaterialTheme.typography.titleMedium)
                ExpandableText("Adapted Lichess broadcast data · CC BY-SA 4.0. Club statistics use CC0 data. Broadcast contributors retain credit; legally replayed moves, derived statistics, Stockfish analysis and course explanations are adaptations.", color = MutedCream, style = MaterialTheme.typography.bodySmall)
                SourceLink("CC BY-SA 4.0", "https://creativecommons.org/licenses/by-sa/4.0/")
                chapter.course.provenance.sources.forEach { source ->
                    Text("${source.title} · ${source.license}", color = Cream, style = MaterialTheme.typography.titleSmall)
                    ExpandableText(source.note, color = MutedCream, style = MaterialTheme.typography.bodySmall)
                    SourceLink(source.title, source.url)
                    if (source.license.startsWith("CC0")) SourceLink("CC0 1.0", "https://creativecommons.org/publicdomain/zero/1.0/")
                }
                chapter.course.chapters.mapNotNull { it.game }.forEach { game -> SourceLink("${game.white} – ${game.black} · ${game.event}", game.site) }
            }
        } }
        state.installedPacks.forEach { pack -> item(key = pack.manifest.packId) {
            val source = pack.manifest.source
            CourseCard {
                Text(source.title, color = Cream, style = MaterialTheme.typography.titleMedium)
                Text(source.license, color = Leaf, style = MaterialTheme.typography.labelSmall)
                ExpandableText(source.attribution + "\n" + source.modifications, color = MutedCream, style = MaterialTheme.typography.bodySmall)
                SourceLink("Source", source.url)
                SourceLink("Licence", source.licenseUrl)
                ExpandableText(pack.notices, color = MutedCream, style = MaterialTheme.typography.bodySmall)
            }
        } }
        item { CourseCard {
            Text("Opening name list", color = Cream, style = MaterialTheme.typography.titleMedium)
            ExpandableText("Lichess chess-openings contributors · CC0-1.0. Names and routes legally replayed; canonical notation and normalized positions added.", color = MutedCream, style = MaterialTheme.typography.bodySmall)
            SourceLink("Lichess chess-openings", "https://github.com/lichess-org/chess-openings/tree/c67912be581f0793dbaa776be5ccf111e01f88d9")
            SourceLink("CC0 1.0", "https://creativecommons.org/publicdomain/zero/1.0/")
        } }
        item { CourseCard {
            Text("Stockfish", color = Cream, style = MaterialTheme.typography.titleMedium)
            ExpandableText("Stockfish developers · GPL-3.0-or-later. Separate offline UCI executable. Corresponding source, build recipe, authors, licence and neural network are bundled in the app's engine assets. LLVM runtime: Apache-2.0 with LLVM exception.", color = MutedCream, style = MaterialTheme.typography.bodySmall)
            SourceLink("Stockfish source and licence", "https://github.com/official-stockfish/Stockfish")
            SourceLink("GPL 3.0", "https://www.gnu.org/licenses/gpl-3.0.html")
            SourceLink("LLVM licence", "https://llvm.org/LICENSE.txt")
        } }
        notices.forEach { (title, text) -> item(key = title) { CourseCard {
            Text(title, color = Cream, style = MaterialTheme.typography.titleMedium)
            ExpandableText(text, color = MutedCream, style = MaterialTheme.typography.bodySmall)
        } } }
    }
}

@Composable
private fun SourceLink(title: String, url: String) {
    val handler = LocalUriHandler.current
    if (url.startsWith("https://") || url.startsWith("http://")) TextButton({ handler.openUri(url) }) {
        Text("$title ↗\n$url", color = Leaf, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun ContentFeedbackScreen(state: AppUiState, onBack: () -> Unit, onShare: () -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier.testTag("content-feedback"), contentPadding = PaddingValues(18.dp, 0.dp, 18.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { CourseHeader("Content feedback (${state.courseFeedback.size})", onBack)
            if (state.developerMode) TextButton(onShare, enabled = state.courseFeedback.isNotEmpty(), modifier = Modifier.testTag("deep-share-feedback")) { Text("Share", color = Leaf) } }
        if (state.developerMode) {
            item { Text("${state.courseFeedback.size} explanation flags saved on this device", color = MutedCream, modifier = Modifier.testTag("deep-feedback-count")) }
            state.courseFeedback.forEachIndexed { i, flag -> item(key = i) { CourseCard {
                Text("${flag.san} · ${flag.kind}", color = Gold, style = MaterialTheme.typography.titleSmall)
                ExpandableText(flag.text, style = MaterialTheme.typography.bodySmall)
                if (flag.note.isNotBlank()) ExpandableText(flag.note, color = Cream, style = MaterialTheme.typography.bodySmall)
                Text(flag.label, color = MutedCream, style = MaterialTheme.typography.labelSmall)
            } } }
        }
    }
}
