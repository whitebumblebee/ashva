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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.openinglab.app.ui.components.Eyebrow
import com.openinglab.app.ui.components.OpeningCard
import com.openinglab.app.ui.theme.Cream
import com.openinglab.app.ui.theme.DeepMoss
import com.openinglab.app.ui.theme.Divider
import com.openinglab.app.ui.theme.Ink
import com.openinglab.app.ui.theme.Leaf
import com.openinglab.app.ui.theme.Moss
import com.openinglab.app.ui.theme.MutedCream
import com.openinglab.shared.model.Opening

@Composable
fun ExploreScreen(
    openings: List<Opening>,
    query: String,
    selectedDifficulty: String,
    onQueryChange: (String) -> Unit,
    onDifficultyChange: (String) -> Unit,
    onOpeningClick: (String) -> Unit,
    onIdentify: () -> Unit,
    modifier: Modifier = Modifier,
    sourcedOpenings: List<Opening> = emptyList(),
    catalogLoading: Boolean = false,
    catalogError: String? = null,
    onOfflineLibrary: () -> Unit = {},
) {
    LazyColumn(
        modifier = modifier.testTag("opening-list"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Eyebrow("Opening explorer")
            Spacer(Modifier.height(6.dp))
            Text("Find your next\ngreat position.", color = Cream, style = MaterialTheme.typography.displayMedium)
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().testTag("opening-search"),
                placeholder = { Text("Search names, ECO codes, or ideas", color = MutedCream) },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = MutedCream) },
                singleLine = true,
                shape = RoundedCornerShape(17.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = DeepMoss,
                    unfocusedContainerColor = DeepMoss,
                    focusedBorderColor = Leaf,
                    unfocusedBorderColor = Divider,
                    focusedTextColor = Cream,
                    unfocusedTextColor = Cream,
                    cursorColor = Leaf,
                ),
            )
        }
        item { IdentifyBanner(onIdentify) }
        item {
            Column(Modifier.fillMaxWidth().background(DeepMoss, RoundedCornerShape(18.dp)).padding(16.dp)) {
                Text(when {
                    catalogLoading -> "Loading installed opening catalog…"
                    catalogError != null -> catalogError
                    sourcedOpenings.isEmpty() -> "More openings available offline"
                    else -> "${sourcedOpenings.size} source families · ${sourcedOpenings.sumOf { it.variations.size }} routes"
                }, color = Cream, style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag("source-catalog-status"))
                Text(if (selectedDifficulty == "Sourced") "Raw name-derived taxonomy. Choose All for Ashva-guided courses; raw source routes remain unchanged." else
                    "Opening courses add Ashva plans and board-derived explanations. Named source routes and authored study continuations are labeled separately; coverage is finite.",
                    color = MutedCream, style = MaterialTheme.typography.bodySmall)
                TextButton(onOfflineLibrary) { Text("Offline library & sources", color = Leaf) }
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("All", "Sourced", "Starter", "Foundation", "Intermediate", "Advanced")) { filter ->
                    val selected = filter == selectedDifficulty
                    Box(
                        Modifier
                            .background(if (selected) Leaf else Moss, CircleShape)
                            .border(1.dp, if (selected) Leaf else Divider, CircleShape)
                            .clickable { onDifficultyChange(filter) }
                            .padding(horizontal = 15.dp, vertical = 9.dp)
                    ) {
                        Text(filter, color = if (selected) Ink else MutedCream, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text("All openings", color = Cream, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                Text("${openings.size} entries", color = MutedCream, style = MaterialTheme.typography.bodySmall)
            }
        }
        items(openings, key = { it.id }) { opening ->
            OpeningCard(opening, onClick = { onOpeningClick(opening.id) }, modifier = Modifier.fillMaxWidth().testTag("opening-${opening.id}"), compact = true)
        }
        if (openings.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No opening found", color = Cream, style = MaterialTheme.typography.titleLarge)
                    Text("Try a player idea like “fianchetto” or an ECO code.", color = MutedCream, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun IdentifyBanner(onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = Leaf,
        shape = RoundedCornerShape(22.dp),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).background(Ink.copy(alpha = .12f), RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.CameraAlt, null, tint = Ink)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Don't know the name?", color = Ink, style = MaterialTheme.typography.titleMedium)
                Text("Enter moves or import PGN/FEN. Match positions against available offline catalogs.", color = Ink.copy(alpha = .7f), style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Rounded.Add, null, tint = Ink)
        }
    }
}
