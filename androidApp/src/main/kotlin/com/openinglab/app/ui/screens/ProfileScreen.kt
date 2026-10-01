package com.openinglab.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.openinglab.app.ui.components.Eyebrow
import com.openinglab.app.ui.theme.Cream
import com.openinglab.app.ui.theme.DeepMoss
import com.openinglab.app.ui.theme.Divider
import com.openinglab.app.ui.theme.Gold
import com.openinglab.app.ui.theme.Ink
import com.openinglab.app.ui.theme.Leaf
import com.openinglab.app.ui.theme.MutedCream
import com.openinglab.app.BuildConfig

@Composable
fun ProfileScreen(modifier: Modifier = Modifier) {
    LazyColumn(modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Eyebrow("Demo profile · not a signed-in account")
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(74.dp).background(Leaf, CircleShape), contentAlignment = Alignment.Center) {
                    Text("♞", color = Ink, style = MaterialTheme.typography.headlineMedium)
                }
                Column(Modifier.padding(start = 16.dp)) {
                    Text("Learner", color = Cream, style = MaterialTheme.typography.headlineLarge)
                    Text("Local study · no account required", color = MutedCream, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item {
            Text("Sample statistics and goals below are design previews, not your rating, recall or study history.",
                color = MutedCream, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ProfileMetric("3", "Openings", Modifier.weight(1f))
                ProfileMetric("47", "Lines", Modifier.weight(1f))
                ProfileMetric("82%", "Recall", Modifier.weight(1f))
            }
        }
        item {
            Surface(color = Gold, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Star, null, tint = Ink, modifier = Modifier.size(28.dp))
                    Column(Modifier.padding(start = 13.dp).weight(1f)) {
                        Text("Weekly goal", color = Ink, style = MaterialTheme.typography.titleMedium)
                        Text("4 of 5 study days complete", color = Ink.copy(alpha = .66f), style = MaterialTheme.typography.bodySmall)
                    }
                    Text("80%", color = Ink, style = MaterialTheme.typography.titleLarge)
                }
            }
        }
        item {
            Text("Preferences preview · not editable yet", color = Cream, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(10.dp))
            ProfileRow(Icons.Rounded.Palette, "Board & pieces", "Forest / Classic")
            Spacer(Modifier.height(8.dp))
            ProfileRow(Icons.Rounded.Settings, "Training preferences", "Adaptive hints")
        }
        item {
            Text("Ashva ${BuildConfig.VERSION_NAME} · Android alpha", color = Cream, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text("Bookmarks, attempts and installed source packs stay in this app's local database. No analytics, accounts or cloud sync. Automatic backup and device-transfer backup are disabled; uninstalling or clearing app data deletes your study history.",
                color = MutedCream, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ProfileMetric(value: String, label: String, modifier: Modifier) {
    Surface(modifier, color = DeepMoss, shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Divider)) {
        Column(Modifier.padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = Cream, style = MaterialTheme.typography.titleLarge)
            Text(label, color = MutedCream, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ProfileRow(icon: ImageVector, title: String, value: String) {
    Surface(color = DeepMoss, shape = RoundedCornerShape(17.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Divider), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Leaf)
            Column(Modifier.padding(horizontal = 13.dp).weight(1f)) {
                Text(title, color = Cream, style = MaterialTheme.typography.titleMedium)
                Text(value, color = MutedCream, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = MutedCream)
        }
    }
}
