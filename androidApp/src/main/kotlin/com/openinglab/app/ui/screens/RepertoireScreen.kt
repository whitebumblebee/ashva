// SPDX-License-Identifier: Apache-2.0
package com.openinglab.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.openinglab.app.ui.RepertoireEditorUiState
import com.openinglab.app.ui.ObservedRepliesUiState
import com.openinglab.app.ui.RepertoireOverviewUiState
import com.openinglab.app.ui.components.ChessBoard
import com.openinglab.app.ui.components.InfoNote
import com.openinglab.app.ui.components.ExpandableText
import com.openinglab.app.ui.theme.*
import com.openinglab.shared.repertoire.*
import com.openinglab.shared.model.PieceColor

@Composable
fun RepertoireScreen(
    state: RepertoireEditorUiState,
    onBack: () -> Unit,
    onCursor: (String, Int) -> Unit,
    onChoose: (String, Boolean) -> Unit,
    onIncludeAll: () -> Unit,
    onAdoptRoute: () -> Unit,
    onPractice: (String?) -> Unit,
    observations: ObservedRepliesUiState,
    onSources: () -> Unit,
    onRetryObservations: () -> Unit,
    modifier: Modifier = Modifier,
    developerMode: Boolean = false,
) {
    var showRoutes by rememberSaveable { mutableStateOf(false) }
    var confirmAdopt by rememberSaveable { mutableStateOf(false) }
    var visibleGaps by rememberSaveable(state.policy.revision) { mutableIntStateOf(12) }
    val ownTurn = state.position.sideToMove == state.policy.side
    val key = state.position.positionKey
    val index = (observations as? ObservedRepliesUiState.Ready)?.index
    val observed = index?.at(key)
    val recorded = state.options.map { it.uci }.toSet()
    LazyColumn(modifier.testTag("repertoire-scroll"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            TextButton(onBack) { Text("← Back", color = Leaf) }
            Text("My repertoire", style = MaterialTheme.typography.headlineSmall, color = Cream)
            Text(state.policy.name, style = MaterialTheme.typography.titleMedium, color = Gold)
            Text("Revision ${state.policy.revision} · ${if (state.saving) "Saving…" else "Choices saved"}", color = MutedCream,
                modifier = Modifier.testTag("repertoire-revision"))
            state.error?.let { Text(it, color = Gold, modifier = Modifier.testTag("repertoire-save-error")) }
            InfoNote("Choose your moves and the replies to prepare.", "One opening family, one color. Seeded from the selected route. Choose your moves and the opponent replies you want to prepare for. Other legal moves are not bad moves.", color = MutedCream)
        }
        item {
            Text("${state.coverage.eligiblePathIds.size} recorded routes fit your choices · ${state.coverage.gaps.size} unanswered branches", color = Cream,
                modifier = Modifier.testTag("repertoire-coverage"))
            InfoNote("${state.coverage.excludedOpponentReplies} excluded replies · ${state.coverage.sourceBoundaries} route endpoints", "${state.coverage.excludedOpponentReplies} known opponent replies excluded · ${state.coverage.sourceBoundaries} source endpoints reached. This is snapshot coverage, not all possible theory or mastery.", color = MutedCream)
            Button({ onPractice(null) }, enabled = !state.saving && state.error == null && state.coverage.eligiblePathIds.isNotEmpty(), modifier = Modifier.testTag("practice-repertoire")) {
                Text("Practice my repertoire")
            }
        }
        item {
            Text(state.path.name, color = Gold, modifier = Modifier.testTag("repertoire-route"))
            TextButton({ showRoutes = true }, enabled = !state.saving) { Text("Browse recorded routes") }
            TextButton({ confirmAdopt = true }, enabled = !state.saving && state.error == null, modifier = Modifier.testTag("repertoire-adopt-route")) { Text("Add this route to my choices") }
            Text("Position ${state.ply}/${state.path.moves.size} · ${state.position.sideToMove.name.lowercase()} to move", color = Cream,
                modifier = Modifier.testTag("repertoire-position"))
            ChessBoard(state.position, perspective = state.policy.side, inputEnabled = false)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton({ onCursor(state.pathId, 0) }, enabled = !state.saving, modifier = Modifier.testTag("repertoire-first")) { Text("First") }
                TextButton({ onCursor(state.pathId, state.ply - 1) }, enabled = !state.saving && state.ply > 0, modifier = Modifier.testTag("repertoire-previous")) { Text("Previous") }
                TextButton({ onCursor(state.pathId, state.ply + 1) }, enabled = !state.saving && state.ply < state.path.moves.size, modifier = Modifier.testTag("repertoire-next")) { Text("Next") }
                TextButton({ onCursor(state.pathId, state.path.moves.size) }, enabled = !state.saving, modifier = Modifier.testTag("repertoire-last")) { Text("Last") }
            }
            Text(if (ownTurn) "Your move: choose one preferred continuation" else "Opponent replies: include the ones you want to cover", color = Cream, style = MaterialTheme.typography.titleMedium)
            if (!ownTurn && state.options.isNotEmpty()) TextButton(onIncludeAll, enabled = !state.saving && state.error == null,
                modifier = Modifier.testTag("repertoire-include-all")) { Text("Include all recorded replies here") }
            InfoNote("${state.unrecordedLegalMoves} legal moves outside these routes", "${state.unrecordedLegalMoves} legal moves at this position are absent from this family snapshot. They are not recorded teaching routes. Open a lesson to explore separate offline engine alternatives; analysis does not add them to this repertoire.", color = MutedCream,
                modifier = Modifier.testTag("repertoire-off-book"))
            if (state.options.isEmpty()) InfoNote("No further moves recorded in this route.", "Source ends here. No continuation is recorded; this is not the end of the game or opening theory.", color = Gold)
        }
        item(key = "observed-replies") {
            ObservedRepliesCard(state, observations, onSources, onRetryObservations, developerMode)
        }
        items(state.options, key = { "option:${it.uci}" }) { option ->
            val selected = if (ownTurn) state.policy.preferredMoves[key] == option.uci else option.uci in state.policy.opponentReplies[key].orEmpty()
            val enabled = !state.saving && state.error == null
            val choose = { onChoose(option.uci, !selected) }
            Row(Modifier.fillMaxWidth().testTag("repertoire-option-${option.uci}").then(
                if (ownTurn) Modifier.selectable(selected, enabled, Role.RadioButton) { if (!selected) choose() }
                else Modifier.toggleable(selected, enabled, Role.Checkbox) { choose() }
            ).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (ownTurn) RadioButton(selected, null, enabled = enabled) else Checkbox(selected, null, enabled = enabled)
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text("${option.san} · ${if (selected) if (ownTurn) "Preferred" else "Included" else "Not selected"}", color = Cream)
                    Text(when {
                        observed == null -> "Game observations unavailable"
                        observed.scoresWithReply == 0 -> "No score with a reply here; frequency unavailable"
                        else -> "Observed: ${observed.count(option.uci)} / ${observed.scoresWithReply} scores with a reply"
                    }, color = Gold, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("observed-option-${option.uci}"))
                    Text(option.routeNames.take(3).joinToString(" · ") + if (option.routeNames.size > 3) " · +${option.routeNames.size - 3} recorded names" else "", color = MutedCream, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (observed != null) items(observed.replies.filter { it.uci !in recorded }, key = { "observed-outside:${it.uci}" }) { reply ->
            Column(Modifier.testTag("observed-outside-${reply.uci}").padding(vertical = 8.dp)) {
                Text("${reply.san} · Observed: ${reply.scores} / ${observed.scoresWithReply} scores with a reply", color = Gold)
                InfoNote("Observed reply outside this opening’s routes.", "Outside this family snapshot. Recorded in the game sample, but no selectable teaching route here yet.", color = MutedCream)
            }
        }
        item { Text("Unanswered branches", color = Cream, style = MaterialTheme.typography.titleMedium) }
        if (state.coverage.gaps.isEmpty()) item { InfoNote("All included branches have choices.", "No choice gaps along the admitted recorded prefixes. Source endpoints and excluded replies still limit this repertoire.", color = MutedCream) }
        items(state.coverage.gaps.take(visibleGaps), key = { "gap:${it.pathId}:${it.ply}:${it.kind}:${it.moveUci}" }) { gap ->
            val path = state.book.graph.paths.getValue(gap.pathId)
            OutlinedButton({ onCursor(gap.pathId, gap.ply) }, enabled = !state.saving,
                modifier = Modifier.fillMaxWidth().testTag("repertoire-gap-${gap.ply}")) {
                Column {
                    Text(when (gap.kind) {
                        RepertoireGapKind.CHOOSE_LEARNER_MOVE -> "Choose your response at half-move ${gap.ply}"
                        RepertoireGapKind.INCLUDE_OPPONENT_REPLY -> "Include an opponent reply at half-move ${gap.ply}"
                        RepertoireGapKind.SOURCE_CONTINUATION -> "Chosen move has no source continuation in this move order (${gap.ply})"
                    })
                    Text(path.name, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (visibleGaps < state.coverage.gaps.size) item {
            TextButton({ visibleGaps += 12 }) { Text("Show more unanswered branches (${state.coverage.gaps.size - visibleGaps} remaining)") }
        }
    }
    if (showRoutes) RoutePicker(state, onDismiss = { showRoutes = false }, onPick = { path ->
        showRoutes = false; onCursor(path, 0)
    })
    if (confirmAdopt) AlertDialog(onDismissRequest = { confirmAdopt = false }, title = { Text("Use this route's choices?") },
        text = { Text("This includes its opponent replies and uses its moves for your side, replacing conflicting preferred moves at shared positions. Other reply selections are retained. Earlier revisions and bookmarks remain saved. Source limits still apply.") },
        confirmButton = { TextButton({ confirmAdopt = false; onAdoptRoute() }, modifier = Modifier.testTag("confirm-adopt-route")) { Text("Use route choices") } },
        dismissButton = { TextButton({ confirmAdopt = false }) { Text("Cancel") } })
}

@Composable
private fun ObservedRepliesCard(state: RepertoireEditorUiState, observations: ObservedRepliesUiState,
    onSources: () -> Unit, onRetry: () -> Unit, developerMode: Boolean) {
    var details by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.testTag("observed-replies-card"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Moves observed in the game sample", color = Cream, style = MaterialTheme.typography.titleMedium)
        when (observations) {
            ObservedRepliesUiState.Missing -> InfoNote("Install a game pack to see observed replies.", "No installed game sample. Install April 2020 broadcast scores in Offline library & sources. Missing data is not zero observations.", color = MutedCream,
                modifier = Modifier.testTag("observed-missing"))
            ObservedRepliesUiState.Loading -> {
                Text("Loading and legally checking the installed score sample…", color = MutedCream, modifier = Modifier.testTag("observed-loading"))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            ObservedRepliesUiState.Error -> {
                InfoNote("Observations unavailable; your choices are saved.", "Game observations could not be loaded. No counts are shown; source packs and choices are retained.", color = Gold, modifier = Modifier.testTag("observed-error"))
                TextButton(onRetry, modifier = Modifier.testTag("observed-retry")) { Text("Retry observations") }
            }
            is ObservedRepliesUiState.Ready -> {
                val index = observations.index
                val key = state.position.positionKey
                val observed = index.at(key)
                val choices = index.choices(state.policy, key, state.options.map { it.uci }.toSet())
                ExpandableText("${observed.scoresSeen} / ${index.totalScores} distinct score records reach this position · ${observed.scoresWithReply} have a recorded reply · ${observed.scoresWithoutReply} end here on first visit", color = Cream,
                    modifier = Modifier.testTag("observed-denominator"))
                if (observed.scoresSeen == 0) InfoNote("No installed game reaches this position.", "No score in this sample reaches the position. This says nothing about whether a move is playable.", color = Gold, modifier = Modifier.testTag("observed-zero"))
                InfoNote("${choices.selected} selected · ${choices.notSelected} unselected · ${choices.outsideSnapshot} outside this opening", "${choices.selected} replies match your choices · ${choices.notSelected} use unselected snapshot moves · ${choices.outsideSnapshot} are outside this family snapshot. Counts apply only here, not to the whole repertoire.", color = MutedCream,
                    modifier = Modifier.testTag("observed-choice-counts"))
                InfoNote("Reply shares count each score’s first visit.", "One score ID counts once per normalized position, using its first visit. Transpositions combine; later repeated visits do not add votes. Shares use only scores with a reply. Different IDs are not assumed to be different real games.", color = MutedCream, style = MaterialTheme.typography.bodySmall)
                InfoNote("Observed replies reflect this installed sample.", "A small archived sample, not popular-master coverage, move quality, win odds or verified GM identities. No new route or engine alternative is generated.", color = MutedCream, style = MaterialTheme.typography.bodySmall)
                if (developerMode) index.sources.forEach { source ->
                    Text("${source.manifest.source.title} · ${source.manifest.coverage.acceptedRecords} scores · ${source.manifest.source.revision} · ${source.manifest.source.license}", color = Gold)
                }
                if (developerMode) TextButton({ details = !details }, modifier = Modifier.testTag("observed-source-details")) { Text(if (details) "Hide source details" else "Source details & attribution") }
                if (developerMode && details) index.sources.forEach { source ->
                    val manifest = source.manifest
                    Text("${manifest.source.attribution}\n${manifest.source.url}\nLicense: ${manifest.source.licenseUrl}\n${manifest.source.modifications}\nPack: ${manifest.packId}\nManifest SHA-256: ${source.manifestSha256}\nRetrieved: ${manifest.retrievedAt}", color = MutedCream, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("observed-provenance-${manifest.packId}"))
                    manifest.dependencies.forEach { Text("Dependency: ${it.packId} · ${it.manifestSha256}", color = MutedCream, style = MaterialTheme.typography.bodySmall) }
                    manifest.limitations.forEach { Text(it, color = MutedCream, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        TextButton(onSources, modifier = Modifier.testTag("observed-open-sources")) { Text("Offline library & sources") }
    }
}

@Composable
private fun RoutePicker(state: RepertoireEditorUiState, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Recorded source routes") }, text = {
        Column {
            OutlinedTextField(query, { query = it }, label = { Text("Find a variation") }, modifier = Modifier.testTag("repertoire-route-search"))
            LazyColumn(Modifier.heightIn(max = 340.dp)) {
                items(state.book.graph.paths.values.filter { it.name.contains(query, true) }, key = { it.id }) { path ->
                    TextButton({ onPick(path.id) }, modifier = Modifier.fillMaxWidth().testTag("repertoire-pick-${path.id}")) {
                        Text("${path.name} · ${path.moves.size} half-moves${if (path.id in state.coverage.eligiblePathIds) " · Fits choices" else ""}")
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onDismiss) { Text("Close") } })
}

@Composable
fun MyRepertoiresScreen(policies: List<RepertoirePolicy>, error: String?, onBack: () -> Unit,
    onEdit: (RepertoirePolicy) -> Unit, onPractice: (RepertoirePolicy) -> Unit, modifier: Modifier = Modifier,
    overview: RepertoireOverviewUiState = RepertoireOverviewUiState.Loading,
    onEditConflict: (RepertoirePolicy, RepertoireChoiceOrigin) -> Unit = { policy, _ -> onEdit(policy) },
    onRetry: () -> Unit = {},
    sets: List<RepertoireSet> = emptyList(), checkedSet: RepertoireSetPlan? = null,
    setLoading: Boolean = false, setError: String? = null,
    onSaveSet: (String, PieceColor, List<String>, RepertoireSet?) -> Unit = { _, _, _, _ -> },
    onCheckSet: (RepertoireSet) -> Unit = {}, onPracticeSet: (RepertoireSetPlan) -> Unit = {},
    developerMode: Boolean = false,
) {
    var side by rememberSaveable { mutableStateOf(PieceColor.WHITE) }
    var showSetEditor by remember { mutableStateOf(false) }
    var editingSet by remember { mutableStateOf<RepertoireSet?>(null) }
    val groups = (overview as? RepertoireOverviewUiState.Ready)?.groups.orEmpty()
    val group = groups.firstOrNull { it.side == side }
    val summaries = groups.flatMap { it.members }.associateBy { it.policy.id }
    LazyColumn(modifier.testTag("my-repertoires-scroll"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            TextButton(onBack) { Text("← Back") }
            Text("My repertoires", color = Cream, style = MaterialTheme.typography.headlineSmall)
            InfoNote("Build a repertoire from any opening lesson.", "White and Black choices are separate. Open any lesson and tap Build / edit my repertoire to begin. Old saved revisions and bookmarks are retained.", color = MutedCream)
            error?.let { Text(it, color = Gold) }
            if (policies.isEmpty()) Text("No repertoire choices saved yet.", color = Cream)
        }
        item(key = "sets-heading") {
            Text("Named multi-opening repertoires", color = Gold, style = MaterialTheme.typography.titleLarge)
            InfoNote("Group compatible openings into one practice queue.", "Group compatible family choices for one color into a practice queue. Every member pins its exact saved revision. A family edit does not change a set until you explicitly save its membership again.", color = MutedCream)
            TextButton({ editingSet = null; showSetEditor = true }, enabled = !setLoading && policies.any { it.side == side },
                modifier = Modifier.testTag("create-repertoire-set")) { Text("New ${side.name.lowercase()} repertoire") }
            if (setLoading) Text("Saving or checking exact set members…", color = MutedCream, modifier = Modifier.testTag("set-loading"))
            setError?.let { Text(it, color = Gold, modifier = Modifier.testTag("set-error")) }
        }
        items(sets.filter { it.side == side }, key = { "set:${it.id}" }) { set ->
            val plan = checkedSet?.takeIf { it.set == set }
            Column(Modifier.testTag("set-${set.id}"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(set.name, color = Cream, style = MaterialTheme.typography.titleMedium)
                Text("${set.side.name} · ${set.members.size} exact family revisions · set revision ${set.revision}", color = MutedCream)
                set.members.forEach { ref ->
                    val current = policies.firstOrNull { it.id == ref.id }
                    Text("${current?.name ?: "Retained family"} · pinned revision ${ref.revision}" +
                        if (current != null && current.revision != ref.revision) " · newer revision ${current.revision} exists" else "", color = MutedCream,
                        style = MaterialTheme.typography.bodySmall)
                }
                Row {
                    TextButton({ editingSet = set; showSetEditor = true }, enabled = !setLoading) { Text("Edit membership") }
                    TextButton({ onCheckSet(set) }, enabled = !setLoading, modifier = Modifier.testTag("check-set-${set.id}")) { Text("Check set") }
                }
                if (plan != null) {
                    ExpandableText("${plan.items.size} admitted route entries · ${plan.overview.conflicts.size} preferred-move conflicts · ${plan.overview.unavailableMembers} unavailable/changed members",
                        color = Gold, modifier = Modifier.testTag("set-check-${set.id}"))
                    InfoNote("Practice follows the choices saved in this set.", "Only routes fitting the pinned choices enter the queue. Named routes may overlap; endpoints are not complete theory and queue navigation is not a mastery score.", color = MutedCream)
                    if (!plan.ready) InfoNote("Resolve conflicts or unavailable members to practise.", "Unified practice is unavailable: resolve incompatible preferences, unavailable content or a member with no fitting route. Keep intentionally different choices in separate named sets; nothing is overwritten.", color = Gold)
                    plan.overview.conflicts.take(12).forEach { conflict ->
                        Text("Conflicting preferred moves: ${conflict.choices.joinToString { it.san }}", color = Cream)
                        conflict.choices.flatMap { it.origins }.distinctBy { it.policyId }.forEach { origin ->
                            val member = plan.overview.members.single { it.policy.id == origin.policyId }.policy
                            TextButton({ onEditConflict(member, origin) }) { Text("Edit current ${member.name} here") }
                        }
                    }
                    if (plan.overview.conflicts.size > 12) Text("${plan.overview.conflicts.size - 12} more conflicts; the combined overview below lists the current-family choices.", color = MutedCream)
                    Button({ onPracticeSet(plan) }, enabled = plan.ready && !setLoading, modifier = Modifier.testTag("practice-set-${set.id}")) { Text("Practice this repertoire") }
                }
            }
        }
        item {
            Text("Combined repertoire overview", color = Gold, style = MaterialTheme.typography.titleLarge)
            InfoNote("Shared positions and conflicts across your openings.", "Live view of saved family choices, separately for each color. Shared positions combine; conflicts stay visible. This does not stitch new teaching routes or automatically change choices.", color = MutedCream)
            Row {
                PieceColor.entries.forEach { color ->
                    TextButton({ side = color }, Modifier.testTag("overview-side-$color")) {
                        Text("${color.name.lowercase().replaceFirstChar { it.uppercase() }}${if (side == color) " ✓" else ""}")
                    }
                }
            }
            when (overview) {
                RepertoireOverviewUiState.Loading -> Text("Checking saved families and shared positions…", color = MutedCream, modifier = Modifier.testTag("overview-loading"))
                RepertoireOverviewUiState.Error -> {
                    InfoNote("Overview unavailable; your choices are saved.", "Combined view could not be checked. Saved choices have not changed; no families were silently omitted.", color = Gold, modifier = Modifier.testTag("overview-error"))
                    TextButton(onRetry, Modifier.testTag("overview-retry")) { Text("Retry combined view") }
                }
                is RepertoireOverviewUiState.Ready -> group?.let {
                    ExpandableText("${it.side.name} · ${it.members.size} saved family policies · ${it.preferredPositions} preferred positions · ${it.includedReplies} distinct included replies", color = Cream,
                        modifier = Modifier.testTag("overview-summary"))
                    ExpandableText("${it.reachedPositions} reached source positions · ${it.conflicts.size} preferred-move conflicts · ${it.unavailableMembers} unavailable/changed families", color = Gold,
                        modifier = Modifier.testTag("overview-conflicts"))
                    InfoNote("Coverage counts reachable, available routes.", "Counts cover validated reachable source prefixes only, not all theory or mastery. Missing versions remain listed and are excluded from checked counts. Practice is still per family, not a unified cross-family lesson.", color = MutedCream)
                    if (it.members.isNotEmpty() && it.conflicts.isEmpty() && it.unavailableMembers == 0)
                        InfoNote("No preferred-move conflicts found.", "No preferred-move conflict in the checked scope. Source endpoints, excluded replies and unanswered branches still limit coverage.", color = MutedCream)
                }
            }
        }
        items(group?.conflicts.orEmpty(), key = { "conflict:${it.positionKey}" }) { conflict ->
            Column(Modifier.testTag("overview-conflict")) {
                Text("Different preferred moves at one shared position", color = Gold)
                Text(conflict.positionKey, color = MutedCream, style = MaterialTheme.typography.bodySmall)
                conflict.choices.forEach { choice ->
                    Text("${choice.san} · ${choice.uci}", color = Cream)
                    choice.origins.forEach { origin ->
                        policies.firstOrNull { it.id == origin.policyId }?.let { policy ->
                            TextButton({ onEditConflict(policy, origin) }, Modifier.testTag("overview-edit-${origin.policyId}-${choice.uci}")) {
                                Text("Edit ${policy.name} here (half-move ${origin.ply})")
                            }
                        }
                    }
                }
                InfoNote("Keep separate repertoires or edit compatible choices.", "Keep these as separate family repertoires, or edit compatible choices deliberately. Some families cannot share one preferred move (for example e4 versus d4). Nothing is overwritten; old revisions/bookmarks are retained.", color = MutedCream)
            }
        }
        item { Text("Saved family policies", color = Cream, style = MaterialTheme.typography.titleLarge) }
        items(policies, key = { it.id }) { policy ->
            var showIdentity by rememberSaveable(policy.id) { mutableStateOf(false) }
            Column {
                Text(policy.name, color = Cream, style = MaterialTheme.typography.titleMedium)
                Text("Revision ${policy.revision} · ${policy.preferredMoves.size} preferred positions · ${policy.opponentReplies.values.sumOf { it.size }} included replies", color = MutedCream)
                if (developerMode) TextButton({ showIdentity = !showIdentity }) { Text(if (showIdentity) "Hide exact saved identity" else "Show exact saved identity") }
                if (developerMode && showIdentity) Text("Lesson: ${policy.lessonId}\nMove snapshot SHA-256: ${policy.contentVersion}\nPolicy: ${policy.id} · revision ${policy.revision}", color = MutedCream, style = MaterialTheme.typography.bodySmall)
                summaries[policy.id]?.let { member ->
                    Text(when (member.status) {
                        RepertoireMemberStatus.AVAILABLE -> "${member.fittingRoutes} member routes fit · ${member.unansweredBranches} unanswered branches · ${member.sourceEndpoints} source endpoints · ${member.excludedReplies} excluded replies. Member routes may overlap across families."
                        RepertoireMemberStatus.UNAVAILABLE -> "Exact source family is unavailable. Saved revision/content identity retained; check the offline library."
                        RepertoireMemberStatus.CHANGED -> "Current family content does not validate these saved choices. They were retained, not automatically rebound."
                    }, color = MutedCream, modifier = Modifier.testTag("overview-member-${policy.id}"))
                }
                Row {
                    TextButton({ onEdit(policy) }, modifier = Modifier.testTag("edit-policy-${policy.side}")) { Text("Edit choices") }
                    TextButton({ onPractice(policy) }, enabled = summaries[policy.id]?.let { it.status == RepertoireMemberStatus.AVAILABLE && it.fittingRoutes > 0 } ?: true,
                        modifier = Modifier.testTag("practice-policy-${policy.side}")) { Text("Practice") }
                }
            }
        }
    }
    if (showSetEditor) RepertoireSetEditorDialog(editingSet, editingSet?.side ?: side, policies,
        onDismiss = { showSetEditor = false }, onSave = { name, ids ->
            onSaveSet(name, editingSet?.side ?: side, ids, editingSet); showSetEditor = false
        })
}

@Composable
private fun RepertoireSetEditorDialog(existing: RepertoireSet?, side: PieceColor, policies: List<RepertoirePolicy>,
    onDismiss: () -> Unit, onSave: (String, List<String>) -> Unit) {
    var name by rememberSaveable(existing?.id) { mutableStateOf(existing?.name ?: "") }
    var chosen by rememberSaveable(existing?.id) { mutableStateOf(existing?.members?.map { it.id }.orEmpty()) }
    val available = policies.filter { it.side == side }
    val availableIds = available.map { it.id }.toSet()
    val missing = chosen.filter { it !in availableIds }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (existing == null) "New ${side.name.lowercase()} repertoire" else "Update repertoire membership") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it.take(120) }, label = { Text("Repertoire name") }, modifier = Modifier.testTag("set-name"))
                ExpandableText("Saving pins the latest displayed revision of every checked family. Prior set revisions and bookmarks remain saved. Conflicts are checked before practice.")
                if (missing.isNotEmpty()) ExpandableText("${missing.size} members are unavailable in the current family list. Their old set is retained; cancel or remove them deliberately before saving.")
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(available, key = { it.id }) { policy ->
                        Row(Modifier.fillMaxWidth().testTag("set-member-${policy.id}").toggleable(policy.id in chosen, role = Role.Checkbox) {
                            chosen = if (it) chosen + policy.id else chosen - policy.id
                        }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(policy.id in chosen, null)
                            Text("${policy.name} · revision ${policy.revision}", Modifier.weight(1f))
                        }
                    }
                    items(missing, key = { it }) { id -> TextButton({ chosen = chosen - id }) { Text("Remove unavailable member from new revision") } }
                }
            }
        }, confirmButton = {
            TextButton({ onSave(name, chosen) }, enabled = name.isNotBlank() && chosen.size in 1..64 && missing.isEmpty(),
                modifier = Modifier.testTag("save-repertoire-set")) { Text("Save membership") }
        }, dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}
