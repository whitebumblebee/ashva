// SPDX-License-Identifier: Apache-2.0
package com.openinglab.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.openinglab.app.BuildConfig
import com.openinglab.app.ui.RecallUiState
import com.openinglab.app.ui.components.Eyebrow
import com.openinglab.app.ui.theme.*

@Composable
fun ProfileScreen(state: RecallUiState, modifier: Modifier = Modifier) {
    val totals = state.totals
    LazyColumn(modifier.testTag("learner-profile-screen"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Eyebrow("Local learner · no signed-in account")
            Text("Your learning history", color = Cream, style = MaterialTheme.typography.headlineLarge)
            Text("Real stored events, not a sample rating, estimated recall percentage or invented streak.", color = MutedCream)
        }
        if (state.loading) item { CircularProgressIndicator() }
        state.error?.let { item { Text(it, color = Gold) } }
        item {
            Column(Modifier.testTag("profile-real-totals"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric("${totals.attempts}", "Recorded move attempts")
                Metric("${totals.unaided}", "Unaided answers · includes early drills, not all spaced progress")
                Metric("${totals.assisted}", "Assisted correct answers")
                Metric("${totals.notRecalled}", "Expected move not recalled · not a chess-blunder classification")
                Metric("${totals.studyViews}", "Study views · not recall grades")
                Metric("${totals.legacyUngraded}", "Legacy / exploratory ungraded attempts · retained without mastery inference")
            }
        }
        item {
            Text("Chosen repertoire completion", color = Cream, style = MaterialTheme.typography.titleLarge)
            Text("Use Review to select a route, family policy or named set revision and see its actual decision denominator. Counts from overlapping scopes are not added into a fabricated global percentage.", color = MutedCream)
        }
        item(key = "app-version") {
            Text("Ashva ${BuildConfig.VERSION_NAME} · Android alpha", color = Cream, style = MaterialTheme.typography.titleMedium)
            Text("Bookmarks, attempts, review schedules and installed source packs stay in this app's local database. No analytics, accounts or cloud sync. Automatic backup and device-transfer backup are disabled; uninstalling or clearing app data deletes your study history.", color = MutedCream, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable private fun Metric(value: String, label: String) {
    Surface(color = DeepMoss, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(value, color = Leaf, style = MaterialTheme.typography.titleLarge)
            Text(label, color = Cream, modifier = Modifier.weight(1f))
        }
    }
}
