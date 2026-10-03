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
import com.openinglab.app.ui.RecallUiState
import com.openinglab.app.ui.components.Eyebrow
import com.openinglab.app.ui.theme.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ReviewScreen(state: RecallUiState, onStartReview: (String) -> Unit, modifier: Modifier = Modifier, onRetryRecall: () -> Unit = {}) {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = state.scopes.firstOrNull { it.scope.id == selectedId } ?: state.scopes.firstOrNull()
    LazyColumn(modifier.testTag("recall-review-screen"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Eyebrow("Your local recall")
            Text("Make your moves stick.", color = Cream, style = MaterialTheme.typography.displayMedium)
            Text("Practice a line or a saved repertoire to add its learner decisions. Review asks one exact-history position at a time. Studying is separate from demonstrating recall.", color = MutedCream)
        }
        if (state.loading) item { CircularProgressIndicator(Modifier.testTag("recall-loading")) }
        state.error?.let { error -> item { Text(error, color = Gold, modifier = Modifier.testTag("recall-error")) } }
        if (state.retryableWrites > 0) item { Button(onRetryRecall, enabled = state.pendingWrites == 0,
            modifier = Modifier.testTag("retry-recall-saves")) { Text("Retry pending saves (${state.retryableWrites})") } }
        if (!state.loading && state.scopes.isEmpty()) item {
            Text("No chosen review scope yet. Open an opening, choose White or Black, and practice; or practice your saved repertoire. Old attempts are retained as ungraded history.", color = Cream, modifier = Modifier.testTag("recall-empty"))
        }
        selected?.let { summary -> item {
            Surface(color = DeepMoss, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(summary.scope.title, color = Leaf, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("recall-selected-scope"))
                    Text("${summary.due} due or new · ${summary.total} chosen decisions", color = Cream, modifier = Modifier.testTag("recall-due-count"))
                    Text("${summary.introduced}/${summary.total} attempted · ${summary.established}/${summary.total} established", color = Cream, modifier = Modifier.testTag("recall-progress"))
                    Text("Established means three due, unaided answers with separated intervals, and not currently overdue. It is not a chess rating, win probability or full-theory mastery. Shared exact prefixes count once within a course; transposed histories stay distinct.", color = MutedCream, style = MaterialTheme.typography.bodySmall)
                    summary.nextDueAt?.let {
                        Text("Next scheduled: ${DateTimeFormatter.ofPattern("d MMM, HH:mm").format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))}", color = MutedCream, modifier = Modifier.testTag("recall-next-date"))
                    }
                    Button({ onStartReview(summary.scope.id) }, enabled = summary.due > 0 && !state.loading, modifier = Modifier.testTag("begin-recall-review")) {
                        Text(if (summary.due > 0) "Review due positions" else "Nothing due in this scope")
                    }
                }
            }
        } }
        item { Text("Chosen scopes · exact retained revisions", color = Cream, style = MaterialTheme.typography.titleMedium) }
        items(state.scopes, key = { it.scope.id }) { summary ->
            OutlinedButton({ selectedId = summary.scope.id }, modifier = Modifier.fillMaxWidth().testTag("recall-scope-${summary.scope.id}")) {
                Column(Modifier.fillMaxWidth()) {
                    Text(summary.scope.title, color = Cream)
                    Text("${summary.due} due/new · ${summary.established}/${summary.total} established", color = MutedCream, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Text("Hints, automatic hints, successful exposed engine analysis and Study exposure within ten minutes count as assistance. Assisted answers and unsuccessful recall retry after ten minutes; early drills cannot extend intervals. No notifications or network uploads.", color = MutedCream, style = MaterialTheme.typography.bodySmall)
        }
    }
}
