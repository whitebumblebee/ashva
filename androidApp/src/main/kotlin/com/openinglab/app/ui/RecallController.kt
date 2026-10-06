// SPDX-License-Identifier: Apache-2.0
package com.openinglab.app.ui

import com.openinglab.shared.lesson.AttemptKind
import com.openinglab.shared.model.ChessMove
import com.openinglab.shared.review.*
import com.openinglab.shared.storage.LearningAttempt
import com.openinglab.shared.storage.LearningStore
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.util.UUID

data class RecallUiState(val loading: Boolean = true, val scopes: List<RecallScopeSummary> = emptyList(),
    val totals: LearningTotals = LearningTotals(), val error: String? = null, val now: Long = System.currentTimeMillis(), val pendingWrites: Int = 0, val retryableWrites: Int = 0)

/** A bounded serial writer preserves actual event order independently of navigation/engine jobs.
 * SQL aggregate flows don't materialize the learner's unbounded attempt JSON history. */
class RecallController(private val store: LearningStore?, private val scope: CoroutineScope,
    private val prepare: suspend (TrainerUiState) -> RecallEnrollment,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutable = MutableStateFlow(RecallUiState(now = clock()))
    val state: StateFlow<RecallUiState> = mutable.asStateFlow()
    private val writes = Channel<suspend () -> Unit>(64)
    private val failedWrites = ArrayDeque<suspend () -> Unit>()
    private val enrollments = linkedMapOf<String, RecallEnrollment>()
    private val enrolled = mutableSetOf<String>()
    private var lastKey: String? = null
    init {
        if (store?.supportsRecall != true) mutable.update { it.copy(loading = false, error = "Durable recall storage is unavailable in this session.") }
        else {
            scope.launch {
                for (write in writes) try { write() }
                catch (e: Exception) {
                    if (e is CancellationException) throw e
                    if (failedWrites.size < 64) failedWrites.addLast(write)
                    mutable.update { it.copy(retryableWrites = failedWrites.size, error = "Recall could not be saved or prepared. Existing history is retained. Retry pending saves before leaving the app.") }
                }
                finally { mutable.update { it.copy(pendingWrites = it.pendingWrites - 1) } }
            }
            scope.launch {
                try { store.learningTotals.collect { totals -> mutable.update { it.copy(totals = totals) } } }
                catch (e: Exception) { if (e is CancellationException) throw e; loadError() }
            }
            scope.launch {
                while (isActive) {
                    val at = clock().coerceIn(0, RecallScheduler.MAX_TIME)
                    mutable.update { it.copy(now = at) }
                    try {
                        withTimeoutOrNull(60_000) { store.recallScopes(at).collect { scopes ->
                            mutable.update { it.copy(loading = false, scopes = scopes) }
                        } }
                    } catch (e: Exception) { if (e is CancellationException) throw e; loadError(); delay(60_000) }
                }
            }
        }
    }
    private fun loadError() { mutable.update { it.copy(loading = false, error = "Review history could not be loaded. Nothing was erased.") } }
    /** Retains the original closures (event IDs/timestamps) for idempotent retries, never new grades. */
    fun retryFailedWrites() {
        if (failedWrites.isEmpty() || state.value.pendingWrites > 0) return
        val retry = failedWrites.toList()
        failedWrites.clear()
        mutable.update { it.copy(error = null, retryableWrites = 0) }
        retry.forEach(::enqueue)
    }
    private fun key(t: TrainerUiState) = t.reviewScopeId?.let { "review:$it" } ?: t.repertoireSetSession?.let {
        "set:${it.plan.set.id}:${it.plan.set.revision}"
    } ?: t.repertoirePolicy?.let { "policy:${it.id}:${it.revision}" } ?: "route:${t.opening.id}:${t.playerSide}:${t.replay.pathId}"
    private fun enqueue(block: suspend () -> Unit) {
        if (state.value.pendingWrites + failedWrites.size >= 64) {
            mutable.update { it.copy(error = "Recall writer is busy. This action was not saved; retry pending saves, then retry the move without clearing app data.") }
            return
        }
        mutable.update { it.copy(pendingWrites = it.pendingWrites + 1) }
        if (writes.trySend(block).isFailure) mutable.update { it.copy(pendingWrites = it.pendingWrites - 1, error = "Recall writer is busy. This action was not saved; wait and retry, without clearing app data.") }
    }
    private suspend fun enrollment(t: TrainerUiState, activate: Boolean = false): RecallEnrollment {
        val k = key(t)
        val result = enrollments[k] ?: prepare(t).also {
            withContext(Dispatchers.Default) { val context = currentCoroutineContext(); it.validate { context.ensureActive() } }
            enrollments[k] = it
            if (enrollments.size > 8) enrollments.remove(enrollments.keys.first())
        }
        if (activate || result.scope.id !in enrolled) {
            requireNotNull(store).enrollRecall(result)
            enrolled += result.scope.id
        }
        return result
    }
    fun trainerChanged(t: TrainerUiState, previous: TrainerUiState?) {
        if (store?.supportsRecall != true) return
        if (t.reviewTargetId != null) {
            if (t.mode == LessonMode.STUDY && previous?.mode != LessonMode.STUDY) {
                val id = UUID.randomUUID().toString(); val at = clock()
                enqueue { store.recordStudyView(id, requireNotNull(t.reviewScopeId), at) }
            }
            return
        }
        val k = key(t)
        val studying = t.mode == LessonMode.STUDY && (previous?.mode != LessonMode.STUDY || previous.opening.id != t.opening.id || previous.replay.pathId != t.replay.pathId)
        if (k != lastKey || studying) {
            lastKey = k
            val id = UUID.randomUUID().toString(); val at = clock()
            enqueue { val e = enrollment(t, activate = true); if (studying) store.recordStudyView(id, e.scope.id, at) }
        }
    }
    fun recordStudyActivity(event: com.openinglab.shared.practice.StudyActivity) {
        if (store == null) return
        if (store.supportsRecall) enqueue { store.recordStudyActivity(event) }
        else scope.launch { try { store.recordStudyActivity(event) }
            catch (e: Exception) { if (e is CancellationException) throw e; loadError() } }
    }
    fun record(t: TrainerUiState, move: ChessMove, kind: AttemptKind, onSaved: (String) -> Unit = {}): String {
        val help = t.recallHelp + (if (t.currentAssisted && t.recallHelp.isEmpty()) setOf(RecallHelp.HINT) else emptySet()) +
            (if (t.studyExposedAt?.let { clock() - it < RecallScheduler.RETRY } == true) setOf(RecallHelp.STUDY) else emptySet())
        val assisted = t.currentAssisted || help.isNotEmpty() || kind != AttemptKind.EXPECTED
        val attempt = LearningAttempt(UUID.randomUUID().toString(), t.opening.id, t.replay.pathId, t.ply,
            t.playerSide.name, move.uci, kind.name, assisted, clock())
        if (store == null) return attempt.id
        if (!store.supportsRecall) { scope.launch { try { store.recordAttempt(attempt); onSaved(attempt.id) }
            catch (e: Exception) { if (e is CancellationException) throw e; loadError() } }; return attempt.id }
        enqueue {
            val (scopeId, target) = if (t.reviewScopeId != null && t.reviewTargetId != null) {
                t.reviewScopeId to requireNotNull(store.recallCard(t.reviewScopeId, t.reviewTargetId)).target
            } else {
                val e = enrollment(t)
                val prefix = t.replay.moves.take(t.ply).map { it.move.uci }
                e.scope.id to e.entries.singleOrNull { it.target.prefix == prefix && it.target.expectedUci == t.replay.nextMove?.move?.uci && it.target.side == t.playerSide }?.target
            }
            if (target == null) {
                // A transposition branch can have history not present in this recorded scope.
                store.recordAttempt(attempt)
                mutable.update { it.copy(error = "Exploratory move saved as ungraded history: this exact branch history is outside the chosen review scope.") }
            } else {
                store.recordRecall(RecallEvent(attempt, target, if (kind != AttemptKind.EXPECTED) RecallGrade.NOT_RECALLED
                    else if (assisted) RecallGrade.ASSISTED else RecallGrade.UNAIDED, help, scopeId))
            }
            onSaved(attempt.id)
        }
        return attempt.id
    }
    /** Enqueued after grades: Next never reselects a card whose previous write is still pending. */
    fun next(scopeId: String, loaded: (RecallCard?) -> Unit) {
        if (store?.supportsRecall != true) return
        enqueue { loaded(store.recallCards(scopeId, clock(), 1).firstOrNull()) }
    }
}
