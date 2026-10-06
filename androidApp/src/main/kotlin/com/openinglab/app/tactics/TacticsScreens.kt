// SPDX-License-Identifier: Apache-2.0
package com.openinglab.app.tactics

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openinglab.app.ui.components.ChessBoard
import com.openinglab.app.ui.components.Eyebrow
import com.openinglab.app.ui.components.PrimaryAction
import com.openinglab.app.ui.components.ExpandableText
import com.openinglab.shared.chess.san
import com.openinglab.app.ui.theme.*
import com.openinglab.shared.model.PieceColor
import com.openinglab.shared.model.PieceType
import com.openinglab.shared.tactics.*
import java.util.Locale

internal fun duration(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    return if (seconds >= 3600) "%d:%02d:%02d".format(Locale.ROOT, seconds / 3600, seconds / 60 % 60, seconds % 60)
        else "%d:%02d".format(Locale.ROOT, seconds / 60, seconds % 60)
}
private fun percent(value: Double) = "${(value * 100).toInt()}%"

@Composable
fun TacticsTab(controller: TacticsController, modifier: Modifier = Modifier, developerMode: Boolean = false) {
    val state by controller.state.collectAsStateWithLifecycle()
    BackHandler(state.page != TacticsPage.CATALOG) { controller.back() }
    when {
        state.loading -> Column(modifier.padding(24.dp)) { Eyebrow("Tactics"); Text("Checking the puzzle pack…", color = Cream); CircularProgressIndicator() }
        state.pack == null -> Column(modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Eyebrow("Tactics"); Text("Tactics pack failed its checks", color = Cream, modifier = Modifier.testTag("tactics-pack-error"))
            PrimaryAction("Retry", controller::reload)
        }
        state.page == TacticsPage.CATALOG -> TacticsCatalog(state, controller, modifier, developerMode)
        state.page == TacticsPage.SET -> TacticsSetScreen(state, controller, modifier)
        state.page == TacticsPage.PUZZLE -> TacticsPuzzleScreen(state, controller, modifier)
        else -> TacticsCompleteScreen(state, controller, modifier)
    }
}

@Composable
private fun Notice(state: TacticsUiState, controller: TacticsController) {
    state.error?.let { Text(it, color = Coral); TextButton(controller::retrySave) { Text("Retry save") } }
    state.message?.let { Text(it, color = Leaf) }
    if (state.saving) Text("Saving…", color = MutedCream)
}

@Composable
private fun TacticsCatalog(state: TacticsUiState, controller: TacticsController, modifier: Modifier, developerMode: Boolean) {
    var create by rememberSaveable { mutableStateOf(false) }
    LazyColumn(modifier, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Eyebrow("Pattern recognition"); Text("Tactics", style = MaterialTheme.typography.headlineLarge, color = Cream) }
        item { Notice(state, controller) }
        state.mostRecentSet()?.let { set ->
            val cycle = state.cycles(set.id).last()
            val progress = state.attempts(set.id, cycle.cycle).size
            val target = Woodpecker.targetMs(set.puzzleIds.size, state.cycles(set.id).firstOrNull { it.cycle == cycle.cycle - 1 })
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Moss)
                    .clickable { controller.openSet(set.id) }.padding(18.dp).testTag("tactics-continue"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Eyebrow("Continue", color = Gold); Text(set.name, color = Cream, style = MaterialTheme.typography.titleLarge)
                    Text("Cycle ${cycle.cycle} · $progress/${set.puzzleIds.size} · ${duration(cycle.activeMs)} / ${target?.let { "target ${duration(it)}" } ?: "untimed target"}", color = MutedCream)
                }
            }
        }
        listOf("WOODPECKER" to "Woodpecker sets", "THEME" to "Themes", "OPENING" to "From your openings", "CUSTOM" to "My sets").forEach { (category, title) ->
            item { Spacer(Modifier.height(8.dp)); Eyebrow(title) }
            items(state.sets.filter { it.category == category }, key = { it.id }) { set ->
                val ratings = set.puzzleIds.mapNotNull { state.pack?.byId?.get(it)?.rating }
                val cycles = state.cycles(set.id).filter { it.completedAt != null }
                val best = cycles.maxOfOrNull { c -> Woodpecker.stats(c, state.attempts(set.id, c.cycle), set.puzzleIds.size, null).accuracy }
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(DeepMoss)
                    .clickable { controller.openSet(set.id) }.padding(16.dp).testTag("tactics-set-${set.id}"), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(set.name, color = Cream, style = MaterialTheme.typography.titleMedium)
                    Text("${set.puzzleIds.size} puzzles · ${ratings.minOrNull() ?: "?"}–${ratings.maxOrNull() ?: "?"}", color = MutedCream)
                    Text("${cycles.size} cycles done · best accuracy ${best?.let(::percent) ?: "—"}", color = Leaf, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { PrimaryAction("Create a set", { create = true }, Modifier.fillMaxWidth().testTag("tactics-create")) }
        if (developerMode) item { Text("Lichess puzzle database · CC0", color = MutedCream, style = MaterialTheme.typography.bodySmall) }
    }
    if (create) CreateSetDialog(state, onDismiss = { create = false }, onCreate = { controller.createSet(it); create = false })
}

@Composable
private fun TacticsSetScreen(state: TacticsUiState, controller: TacticsController, modifier: Modifier) {
    val set = state.selectedSet ?: return
    val cycles = state.cycles(set.id)
    val latest = cycles.lastOrNull()
    val next = latest?.let { if (it.completedAt == null) it.cycle else it.cycle + 1 } ?: 1
    val resume = latest?.completedAt == null && latest != null
    var reset by rememberSaveable(set.id) { mutableStateOf(false) }
    Column(modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButton(controller::back) { Text("‹ Tactics") }
        Eyebrow("Woodpecker method"); Text(set.name, color = Cream, style = MaterialTheme.typography.headlineMedium)
        ExpandableText("Solve the same set in repeated cycles to recognise its patterns. Aim for half your previous time each cycle; seven cycles are recommended.", color = MutedCream)
        Notice(state, controller)
        if (!state.saving) PrimaryAction(if (resume) "Resume cycle $next (${state.attempts(set.id, next).size}/${set.puzzleIds.size})" else "Start cycle $next",
            controller::startCycle, Modifier.fillMaxWidth().testTag("tactics-start"))
        Eyebrow("Cycle history")
        Row(Modifier.fillMaxWidth()) {
            listOf("Cycle", "Time", "Accuracy", "vs target").forEach { Text(it, color = Gold, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium) }
        }
        if (cycles.isEmpty()) Text("Your first cycle establishes a baseline.", color = MutedCream)
        cycles.forEach { cycle ->
            val attempts = state.attempts(set.id, cycle.cycle)
            val stats = Woodpecker.stats(cycle, attempts, set.puzzleIds.size, cycles.firstOrNull { it.cycle == cycle.cycle - 1 })
            Row(Modifier.fillMaxWidth().testTag("tactics-cycle-${cycle.cycle}")) {
                listOf("${cycle.cycle}${if (cycle.completedAt == null) " · active" else ""}", duration(cycle.activeMs), percent(stats.accuracy),
                    stats.targetMs?.let { if (cycle.completedAt == null) "${duration(it)} target" else
                        "${if (cycle.activeMs <= it) "−" else "+"}${duration(kotlin.math.abs(cycle.activeMs - it))}" } ?: "Untimed")
                    .forEach { Text(it, color = Cream, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall) }
            }
            if (attempts.any { !it.correct } && !state.saving) TextButton({ controller.retryMistakes(cycle.cycle) }, Modifier.testTag("tactics-mistakes-${cycle.cycle}")) {
                Text("Retry mistakes from cycle ${cycle.cycle} (${attempts.count { !it.correct }})")
            }
        }
        TextButton({ reset = true }, enabled = !state.saving) { Text("Reset set history", color = Coral) }
    }
    if (reset) AlertDialog(onDismissRequest = { reset = false }, title = { Text("Reset ${set.name}?") },
        text = { Text("This removes this set’s cycles and attempts. The puzzle set stays available.") },
        confirmButton = { TextButton({ reset = false; controller.resetHistory() }) { Text("Reset history") } },
        dismissButton = { TextButton({ reset = false }) { Text("Cancel") } })
}

@Composable
private fun TacticsPuzzleScreen(state: TacticsUiState, controller: TacticsController, modifier: Modifier) {
    val p = state.puzzle ?: return
    val expectedSan = remember(p.position, p.expected, p.phase) {
        if (p.phase == PuzzlePhase.WRONG) p.expected?.let { p.position.san(it) } else null
    }
    DisposableEffect(controller, p.puzzle.id) { controller.setVisible(true); onDispose { controller.setVisible(false) } }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { controller.setVisible(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { controller.setVisible(false) }
    var menu by remember { mutableStateOf(false) }
    val uri = LocalUriHandler.current
    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(controller::back, Modifier.testTag("tactics-exit")) { Text("‹ Set") }
            Text("${p.ordinal + 1} / ${p.count}", color = Cream, modifier = Modifier.weight(1f).testTag("tactics-progress"))
            Box {
                IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, "Puzzle options", tint = Cream) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Auto-next ${if (state.autoNext) "✓" else ""}") }, onClick = { controller.toggleAutoNext(); menu = false })
                }
            }
        }
        Eyebrow(if (p.practice) "Mistakes practice · cycle ${p.cycle}" else "Cycle ${p.cycle}")
        Text("${duration(state.activeMs)} · ${state.targetMs?.let { "target ${duration(it)}" } ?: "untimed target"}", color = MutedCream, modifier = Modifier.testTag("tactics-timer"))
        Text(if (p.solver == PieceColor.WHITE) "White to move" else "Black to move", color = Cream, style = MaterialTheme.typography.titleLarge)
        val flash = when (p.phase) { PuzzlePhase.CORRECT, PuzzlePhase.SOLVED -> Leaf; PuzzlePhase.WRONG -> Coral; else -> Divider }
        Box(Modifier.widthIn(max = 460.dp).fillMaxWidth().border(3.dp, flash, RoundedCornerShape(8.dp)).padding(3.dp)) {
            ChessBoard(position = p.position, perspective = p.solver, selectedSquare = p.selected, legalTargets = p.targets,
                hintSquares = p.hints, onSquareTap = controller::tap, inputEnabled = p.phase == PuzzlePhase.READY && !state.saving)
        }
        Text(when (p.phase) {
            PuzzlePhase.SETUP -> "Watch the setup move"
            PuzzlePhase.READY -> "Find the best move"
            PuzzlePhase.CORRECT -> "Correct"
            PuzzlePhase.SOLVED -> "Solved"
            PuzzlePhase.WRONG -> expectedSan?.let { "Miss · the move was $it" } ?: "Miss"
            PuzzlePhase.SOLUTION -> "Solution playback"
            PuzzlePhase.SOLUTION_DONE -> "Solution complete"
        }, color = flash.takeUnless { it == Divider } ?: Cream, modifier = Modifier.testTag("tactics-feedback"))
        Notice(state, controller)
        if (!state.saving && state.error == null) {
            if (p.phase == PuzzlePhase.WRONG) TextButton(controller::showSolution, Modifier.testTag("tactics-solution")) { Text("Show solution") }
            if (p.phase in setOf(PuzzlePhase.WRONG, PuzzlePhase.SOLVED, PuzzlePhase.SOLUTION_DONE))
                PrimaryAction("Next", controller::next, Modifier.fillMaxWidth().testTag("tactics-next"))
        }
        TextButton({ uri.openUri("https://lichess.org/training/${p.puzzle.id}") }) { Text("Lichess puzzle ↗") }
        if (p.practice) Text("Practice leaves cycle statistics unchanged.", color = MutedCream, style = MaterialTheme.typography.bodySmall)
    }
    p.promotion?.let {
        AlertDialog(onDismissRequest = controller::cancelPromotion, title = { Text("Promote to") }, text = {
            Row { listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT).forEach { type ->
                TextButton({ controller.promote(type) }) { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }) }
            } }
        }, confirmButton = {}, dismissButton = { TextButton(controller::cancelPromotion) { Text("Cancel") } })
    }
}

@Composable
private fun TacticsCompleteScreen(state: TacticsUiState, controller: TacticsController, modifier: Modifier) {
    val set = state.selectedSet ?: return
    val cycles = state.cycles(set.id)
    val cycle = cycles.lastOrNull { it.completedAt != null } ?: return
    val stats = Woodpecker.stats(cycle, state.attempts(set.id, cycle.cycle), set.puzzleIds.size, cycles.firstOrNull { it.cycle == cycle.cycle - 1 })
    Column(modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Eyebrow("Cycle ${cycle.cycle} complete")
        Text(set.name, style = MaterialTheme.typography.headlineLarge, color = Cream)
        Text("Time ${duration(stats.activeMs)}\nAccuracy ${percent(stats.accuracy)}\nAverage ${"%.1f".format(Locale.ROOT, stats.averageSeconds)} sec / puzzle", color = Cream, style = MaterialTheme.typography.titleLarge)
        stats.improvement?.let { Text("${percent(kotlin.math.abs(it))} ${if (it >= 0) "faster" else "slower"} than the previous cycle", color = Leaf) }
        stats.targetMs?.let { Text("Cycle target ${duration(it)} · ${if (stats.activeMs <= it) "reached" else "keep practising"}", color = MutedCream) }
        Text("Next target ${duration(Woodpecker.targetMs(set.puzzleIds.size, cycle)!!)}", color = Gold)
        Text("Rest a day before the next cycle.", color = MutedCream)
        PrimaryAction("Back to set", controller::back, Modifier.fillMaxWidth().testTag("tactics-complete"))
    }
}

@Composable
private fun CreateSetDialog(state: TacticsUiState, onDismiss: () -> Unit, onCreate: (CustomSetSpec) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var range by remember { mutableStateOf(800f..2600f) }
    var themes by remember { mutableStateOf(emptySet<String>()) }
    var size by rememberSaveable { mutableIntStateOf(50) }
    val tags = listOf("fork", "pin", "skewer", "discoveredAttack", "discoveredCheck", "mateIn2", "mateIn3", "backRankMate", "exposedKing",
        "kingsideAttack", "sacrifice", "attraction", "deflection", "endgame", "advancedPawn", "promotion", "defensiveMove", "quietMove", "intermezzo")
    val filter = (range.start.toInt()..range.endInclusive.toInt()) to themes
    val matches by produceState<Pair<Pair<IntRange, Set<String>>, Int>?>(null, filter, state.pack) {
        value = withContext(Dispatchers.Default) {
            filter to state.pack!!.puzzles.count { p -> p.rating in range.start.toInt()..range.endInclusive.toInt() && (themes.isEmpty() || p.themes.any { it in themes }) }
        }
    }
    val count = matches?.takeIf { it.first == filter }?.second
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Create a set") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(name, { name = it.take(80) }, label = { Text("Set name") }, singleLine = true, modifier = Modifier.testTag("tactics-set-name"))
            Text("Rating ${range.start.toInt()}–${range.endInclusive.toInt()}", color = Cream)
            RangeSlider(value = range, onValueChange = { range = it }, valueRange = 800f..2600f, steps = 35)
            Text("Themes · any selected, or all when empty", color = MutedCream)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tags.forEach { tag -> FilterChip(tag in themes, { themes = if (tag in themes) themes - tag else themes + tag }, label = { Text(tag.replace(Regex("([a-z])([A-Z])"), "$1 $2").replaceFirstChar { it.uppercase() }) }) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(25, 50, 100, 200).forEach { n -> FilterChip(size == n, { size = n }, label = { Text("$n") }) }
            }
            Text(count?.let { "$it puzzles match" } ?: "Checking puzzles…", color = if ((count ?: 0) >= size) Leaf else Coral)
        }
    }, confirmButton = { TextButton({ onCreate(CustomSetSpec(name.trim(), range.start.toInt(), range.endInclusive.toInt(), themes, size, System.currentTimeMillis())) },
        enabled = name.isNotBlank() && (count ?: 0) >= size) { Text("Create") } }, dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}
