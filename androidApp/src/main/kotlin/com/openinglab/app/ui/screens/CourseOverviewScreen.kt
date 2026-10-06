// SPDX-License-Identifier: Apache-2.0
package com.openinglab.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.rotate
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.openinglab.app.ui.components.ExpandableText
import com.openinglab.app.ui.theme.*
import com.openinglab.shared.course.*

@Composable
fun CourseOverviewScreen(chapters: List<DeepCourseChapterSummary>, onBack: () -> Unit, onChapter: (String) -> Unit, modifier: Modifier = Modifier) {
    val pack = chapters.firstOrNull()?.course ?: return
    LazyColumn(modifier.testTag("deep-course-overview"), contentPadding = PaddingValues(18.dp, 0.dp, 18.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { CourseHeader(pack.title, onBack)
            Text(courseSubtitle(pack), color = MutedCream, style = MaterialTheme.typography.bodySmall)
            ExpandableText(pack.summary, color = MutedCream, style = MaterialTheme.typography.bodySmall) }
        items(chapters.sortedBy { it.chapter.kind == "GAME" }, key = { it.openingId }) { chapter ->
            Surface(onClick = { onChapter(chapter.openingId) }, modifier = Modifier.fillMaxWidth().testTag("deep-course-${chapter.chapter.id}"),
                color = DeepMoss, shape = RoundedCornerShape(14.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(chapter.chapter.displayTitle(pack), color = Cream, style = MaterialTheme.typography.titleMedium)
                        Text(if (chapter.chapter.kind == "GAME") "GM game · ${chapter.chapter.game?.result.orEmpty()}" else
                            "${chapter.chapter.variations.size} variations · ${chapter.lineCount} lines", color = MutedCream, style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.Rounded.ChevronRight, "Open chapter", tint = MutedCream)
                }
            }
        }
    }
}

internal fun courseSubtitle(pack: DeepCoursePack): String = "Complete course · ${pack.chapters.count { it.kind == "REPERTOIRE" }} chapters · " +
    when (pack.side) { "BOTH" -> "both colours"; "WHITE" -> "White"; else -> "Black" }

@Composable
internal fun CourseHeader(title: String, onBack: () -> Unit, tag: String = "course-title") {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Cream) }
        Text(title, color = Cream, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).testTag(tag))
    }
}

@Composable
internal fun CourseCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = DeepMoss, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Divider)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

internal fun LazyListScope.variationRows(tree: CourseVariationTree, parent: Int?, expanded: List<Int>, onExpand: (Int) -> Unit,
                                       onOpen: (Int) -> Unit, depth: Int = 0) {
    tree.children(parent).forEach { index ->
        item(key = "variation-$index") { CourseVariationRow(tree, index, onOpen = { onOpen(index) }, depth = depth,
            expanded = index in expanded, onExpand = { onExpand(index) }) }
        if (index in expanded) variationRows(tree, index, expanded, onExpand, onOpen, depth + 1)
    }
}

@Composable
internal fun CourseVariationRow(tree: CourseVariationTree, index: Int, onOpen: () -> Unit, depth: Int = 0,
                                expanded: Boolean = false, onExpand: () -> Unit = {}) {
    val v = tree.variations[index]
    val rotation by animateFloatAsState(if (expanded) 90f else 0f, label = "Variation chevron")
    Surface(Modifier.fillMaxWidth().padding(start = (minOf(depth, 5) * 12).dp), color = DeepMoss, shape = RoundedCornerShape(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clickable(onClick = onOpen).padding(12.dp).testTag("deep-variation-$index"),
                verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(tree.shortName(index), color = Cream, style = MaterialTheme.typography.titleSmall)
                Text(numberedCourseMoves(v.path, maxOf(0, v.path.size - 2)), color = MutedCream, style = MaterialTheme.typography.bodySmall)
                ResultBar(v, Modifier.fillMaxWidth())
                Text("${v.games} master games", color = MutedCream, style = MaterialTheme.typography.labelSmall)
            }
            if (tree.children(index).isNotEmpty()) IconButton(onExpand, Modifier.testTag("deep-expand-$index")) {
                Icon(Icons.Rounded.ChevronRight,
                    if (expanded) "Collapse sub-variations" else "Expand sub-variations", Modifier.rotate(rotation), tint = Leaf)
            } else IconButton(onOpen) { Icon(Icons.Rounded.ChevronRight, "Open variation", tint = MutedCream) }
        }
    }
}

internal fun numberedCourseMoves(path: List<String>, from: Int = 0): String = path.drop(from).mapIndexed { offset, san ->
    val ply = from + offset
    if (ply % 2 == 0) "${ply / 2 + 1}.$san" else if (offset == 0) "${ply / 2 + 1}...$san" else san
}.joinToString(" ")

@Composable
internal fun ResultBar(v: CourseVariation, modifier: Modifier = Modifier, labels: Boolean = false) {
    val total = maxOf(1, v.whiteWins + v.draws + v.blackWins)
    Column(modifier.semantics { contentDescription = "White ${100 * v.whiteWins / total}%, draw ${100 * v.draws / total}%, Black ${100 * v.blackWins / total}%" }) {
        Row(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp))) {
            listOf(v.whiteWins to Cream, v.draws to MutedCream, v.blackWins to Ink).forEach { (count, color) ->
                if (count > 0) Surface(Modifier.weight(count.toFloat()).fillMaxHeight(), color = color,
                    border = if (color == Ink) BorderStroke(1.dp, MutedCream) else null) {}
            }
        }
        if (labels) Text("White ${100 * v.whiteWins / total}% · Draw ${100 * v.draws / total}% · Black ${100 * v.blackWins / total}%",
            color = MutedCream, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
internal fun ResultLegend() {
    Row(Modifier.padding(top = 6.dp).testTag("deep-result-legend"), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf("White" to Cream, "Draw" to MutedCream, "Black" to Ink).forEach { (label, color) ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(10.dp), color = color, shape = RoundedCornerShape(2.dp),
                    border = if (color == Ink) BorderStroke(1.dp, MutedCream) else null) {}
                Text(label, color = MutedCream, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun CourseUnavailable(onBack: () -> Unit, loading: Boolean, error: String?) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(error ?: if (loading) "Loading the course…" else "This course is unavailable. Saved learning data is retained.", color = Cream)
        if (loading) CircularProgressIndicator()
        TextButton(onBack) { Text("Back", color = Leaf) }
    }
}
