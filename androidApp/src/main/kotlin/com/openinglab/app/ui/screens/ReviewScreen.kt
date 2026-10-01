package com.openinglab.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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

@Composable
fun ReviewScreen(onStartReview: () -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Eyebrow("Review preview · sample data")
            Spacer(Modifier.height(6.dp))
            Text("Make every move\nstick.", color = Cream, style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(10.dp))
            Text("The scheduler is not implemented yet. Counts, dates and recall below are examples. Begin review opens a starter practice lesson, not a personalized queue.", color = MutedCream, style = MaterialTheme.typography.bodyLarge)
        }
        item {
            Surface(color = Leaf, shape = RoundedCornerShape(26.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(22.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(52.dp).background(Ink.copy(alpha = .12f), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Psychology, null, tint = Ink)
                        }
                        Column(Modifier.padding(start = 14.dp).weight(1f)) {
                            Text("Today's review", color = Ink, style = MaterialTheme.typography.headlineMedium)
                            Text("5 positions · about 4 minutes", color = Ink.copy(alpha = .65f), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Spacer(Modifier.height(22.dp))
                    PrimaryAction("Begin review", onStartReview, modifier = Modifier.fillMaxWidth(), color = Ink)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ReviewStat("7", "day streak", Gold, Icons.Rounded.LocalFireDepartment, Modifier.weight(1f))
                ReviewStat("82%", "recall rate", Leaf, Icons.Rounded.Check, Modifier.weight(1f))
            }
        }
        item {
            Text("Coming up", color = Cream, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(12.dp))
            listOf("Ruy López · Morphy Defence" to "Today", "Queen's Gambit · QGD" to "Tomorrow", "London · early ...c5" to "In 3 days").forEach { (name, time) ->
                Row(Modifier.fillMaxWidth().border(1.dp, Divider, RoundedCornerShape(16.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).background(if (time == "Today") Gold else Leaf, CircleShape))
                    Text(name, color = Cream, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 12.dp).weight(1f))
                    Text(time, color = MutedCream, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(9.dp))
            }
        }
    }
}

@Composable
private fun ReviewStat(value: String, label: String, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    Surface(modifier, color = DeepMoss, shape = RoundedCornerShape(20.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Divider)) {
        Column(Modifier.padding(17.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(12.dp))
            Text(value, color = Cream, style = MaterialTheme.typography.headlineMedium)
            Text(label, color = MutedCream, style = MaterialTheme.typography.bodySmall)
        }
    }
}
