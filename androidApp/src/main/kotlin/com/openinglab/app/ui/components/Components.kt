package com.openinglab.app.ui.components

import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openinglab.app.ui.MainTab
import com.openinglab.app.ui.theme.Cream
import com.openinglab.app.ui.theme.DeepMoss
import com.openinglab.app.ui.theme.Divider
import com.openinglab.app.ui.theme.Ink
import com.openinglab.app.ui.theme.Leaf
import com.openinglab.app.ui.theme.Moss
import com.openinglab.app.ui.theme.MutedCream
import com.openinglab.shared.model.Difficulty
import com.openinglab.shared.model.Opening
import com.openinglab.shared.model.OpeningSide

@Composable
fun OpeningLabMark(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Leaf),
            contentAlignment = Alignment.Center,
        ) {
            Text("♞", color = Ink, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.width(10.dp))
        Text("ASHVA", color = Cream, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = Leaf) {
    Text(text.uppercase(), color = color, style = MaterialTheme.typography.labelMedium, modifier = modifier)
}

@Composable
fun SectionHeader(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Cream, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(action, color = Leaf, style = MaterialTheme.typography.labelLarge, modifier = Modifier.clickable(onClick = onAction).padding(8.dp))
        }
    }
}

@Composable
fun PrimaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = Icons.AutoMirrored.Rounded.ArrowForward,
    color: Color = Leaf,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = if (color.luminance() < .32f) Leaf else Ink,
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
        if (icon != null) {
            Spacer(Modifier.width(8.dp))
            Icon(icon, null, Modifier.size(18.dp))
        }
    }
}

@Composable
fun OpeningCard(
    opening: Opening,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    developerMode: Boolean = false,
) {
    val accent = Color(opening.accentHex)
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick),
        color = DeepMoss,
        shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Divider),
    ) {
        Column(Modifier.padding(if (compact) 16.dp else 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(if (compact) 38.dp else 46.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(accent.copy(alpha = .17f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (opening.side == OpeningSide.BLACK) "♟" else "♙", color = accent, style = MaterialTheme.typography.headlineMedium)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Eyebrow("${opening.eco} · ${opening.family}", color = accent)
                    Text(
                        opening.name,
                        color = Cream,
                        style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = MutedCream, modifier = Modifier.size(18.dp))
            }
            if (developerMode) opening.provenance?.let { source ->
                val course = opening.teaching
                Text(if (course != null) "${course.sourceRoutes} named routes + ${course.authoredRoutes} study lines · ${course.minPlies}–${course.maxPlies} half-moves" else
                    "${opening.variations.size} source routes · ${source.minPlies}–${source.maxPlies} half-moves · ${source.license}",
                    color = MutedCream, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
            if (!compact) {
                Spacer(Modifier.height(18.dp))
                if (developerMode || (opening.teaching == null && opening.provenance == null)) Text(opening.identity, color = MutedCream, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                if (developerMode && opening.teaching != null) Text("White & Black · study, practice and branch replay", color = accent, style = MaterialTheme.typography.labelMedium)
                else if (developerMode) Text("Finite routes · choose a scope in Review for actual recall progress", color = accent, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
fun DifficultyPill(difficulty: Difficulty) {
    val label = difficulty.name.lowercase().replaceFirstChar { it.uppercase() }
    Box(
        Modifier
            .clip(CircleShape)
            .background(Moss)
            .border(1.dp, Divider, CircleShape)
            .padding(horizontal = 11.dp, vertical = 6.dp)
    ) {
        Text(label, color = MutedCream, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun AppBottomBar(selected: MainTab, onSelect: (MainTab) -> Unit) {
    val items = listOf(
        Triple(MainTab.LEARN, "Learn", Icons.Rounded.AutoStories),
        Triple(MainTab.EXPLORE, "Explore", Icons.Rounded.Explore),
        Triple(MainTab.TACTICS, "Tactics", Icons.Rounded.Bolt),
        Triple(MainTab.REVIEW, "Review", Icons.Rounded.Quiz),
        Triple(MainTab.PROFILE, "Profile", Icons.Rounded.Person),
    )
    NavigationBar(
        containerColor = DeepMoss,
        tonalElevation = 0.dp,
        modifier = Modifier.border(1.dp, Divider),
    ) {
        items.forEach { (tab, label, icon) ->
            NavigationBarItem(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                icon = { Icon(icon, label, Modifier.size(22.dp)) },
                label = { Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = if (selected == tab) FontWeight.Bold else FontWeight.Normal) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Ink,
                    selectedTextColor = Leaf,
                    indicatorColor = Leaf,
                    unselectedIconColor = MutedCream,
                    unselectedTextColor = MutedCream,
                ),
            )
        }
    }
}
