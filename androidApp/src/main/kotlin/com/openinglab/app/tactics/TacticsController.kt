// SPDX-License-Identifier: Apache-2.0
package com.openinglab.app.tactics

import android.os.SystemClock
import com.openinglab.app.content.StartupDispatchers
import com.openinglab.shared.data.PackValidationCache
import com.openinglab.shared.chess.BoardPosition
import com.openinglab.shared.model.ChessMove
import com.openinglab.shared.model.PieceColor
import com.openinglab.shared.model.PieceType
import com.openinglab.shared.tactics.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Calendar
import java.util.UUID

enum class TacticsPage { CATALOG, SET, PUZZLE, COMPLETE }
enum class PuzzlePhase { SETUP, READY, CORRECT, WRONG, SOLVED, SOLUTION, SOLUTION_DONE }
data class TacticsPuzzleUi(val puzzle: Puzzle, val position: BoardPosition, val solver: PieceColor, val ordinal: Int,
    val count: Int, val cycle: Int, val practice: Boolean, val phase: PuzzlePhase = PuzzlePhase.SETUP,
    val selected: String? = null, val targets: Set<String> = emptySet(), val hints: Set<String> = emptySet(),
    val promotion: ChessMove? = null, val expected: ChessMove? = null)
data class TacticsUiState(val loading: Boolean = true, val pack: TacticsPack? = null,
    val history: TacticsHistory = TacticsHistory(), val page: TacticsPage = TacticsPage.CATALOG,
    val selectedSetId: String? = null, val puzzle: TacticsPuzzleUi? = null, val activeMs: Long = 0,
    val targetMs: Long? = null, val saving: Boolean = false, val autoNext: Boolean = true,
    val error: String? = null, val message: String? = null) {
    val sets: List<PuzzleSet> get() = pack?.sets.orEmpty() + history.customSets.map { it.asSet() }
    val selectedSet: PuzzleSet? get() = sets.firstOrNull { it.id == selectedSetId }
    fun cycles(id: String) = history.cyclesBySet[id].orEmpty()
    fun attempts(id: String, cycle: Int) = history.attemptsByCycle[id to cycle].orEmpty()
    fun mostRecentSet(): PuzzleSet? {
        val recent = history.latestCycle ?: return null
        return sets.firstOrNull { it.id == recent.setId }
    }
}
data class TacticsTodaySummary(val activeSet: String?, val setId: String?, val cycle: Int?, val progress: Int,
    val puzzleCount: Int, val puzzlesSolvedToday: Int)

/** Screen-owned interaction and animation, backed by the same Room database as the other learner data. */
class TacticsController(private val store: TacticsStore?, private val scope: CoroutineScope,
    private val source: (() -> String)?, private val elapsed: () -> Long = SystemClock::elapsedRealtime,
    private val now: () -> Long = System::currentTimeMillis, initialAutoNext: Boolean = true,
    private val startupReady: suspend () -> Unit = {},
    private val backgroundReady: suspend () -> Unit = {},
    private val validationCache: PackValidationCache? = null,
    private val onAutoNext: (Boolean) -> Unit = {},
    private val onPuzzleAttempt: (String, String, String, Long) -> Unit = { _, _, _, _ -> }) {
    private val _state = MutableStateFlow(TacticsUiState(autoNext = initialAutoNext))
    val state = _state.asStateFlow()
    val today = combine(_state.map { it.pack to it.history }.distinctUntilChanged(),
        flow { while (currentCoroutineContext().isActive) { emit(now()); delay(30_000) } }) { (pack, history), at ->
        val snapshot = TacticsUiState(pack = pack, history = history)
        val set = snapshot.mostRecentSet()
        val cycle = set?.let { snapshot.cycles(it.id).lastOrNull() }
        val day = Calendar.getInstance().apply { timeInMillis = at; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        TacticsTodaySummary(set?.name, set?.id, cycle?.cycle,
            if (set != null && cycle != null) snapshot.attempts(set.id, cycle.cycle).size else 0,
            set?.puzzleIds?.size ?: 0, history.attempts.count { it.correct && it.at >= day })
    }.flowOn(StartupDispatchers.worker).stateIn(scope, SharingStarted.Eagerly, TacticsTodaySummary(null, null, null, 0, 0, 0))
    private val writes = Mutex()
    private val validation = Mutex()
    private var fullyValidated = false
    private val checkedSets = mutableSetOf<String>()
    private var retrySessionId = ""
    private val loggedActivity = mutableSetOf<String>()
    private var session: PuzzleSession? = null
    private var ids: List<String> = emptyList()
    private var visible = false
    private var animation: Job? = null
    private var generation = 0
    private var cycleMs = 0L
    private var puzzleMs = 0L
    private var runningSince: Long? = null
    private var checkpointAt = 0L
    private data class Pending(val attempt: TacticsAttempt, val ids: List<String>, val activeMs: Long)
    private var pending: Pending? = null
    private data class ClockSave(val setId: String, val cycle: Int, val activeMs: Long)
    private val pendingClocks = mutableMapOf<Pair<String, Int>, ClockSave>()

    init {
        reload()
        scope.launch {
            while (isActive) {
                delay(250); accrue()
                if (runningSince != null && elapsed() - checkpointAt >= 2000) { checkpointAt = elapsed(); checkpoint() }
            }
        }
    }
    fun reload() {
        if (_state.value.pack != null) return
        _state.update { it.copy(loading = true, error = null) }
        scope.launch {
            try {
                startupReady()
                val (pack, validated) = withContext(StartupDispatchers.worker) {
                    val context = currentCoroutineContext()
                    val pack = TacticsPackValidator.decode(requireNotNull(source).invoke())
                    TacticsPackValidator.validateStructure(pack) { context.ensureActive() }
                    if (validationCache == null) TacticsPackValidator.validate(pack) { context.ensureActive() }
                    pack to (validationCache == null || validationCache.isValidated("pack"))
                }
                val history = store?.load() ?: TacticsHistory()
                withContext(StartupDispatchers.worker) { validateHistory(pack, history) }
                fullyValidated = validated
                _state.update { it.copy(loading = false, pack = pack, history = history) }
                if (!fullyValidated) scope.launch {
                    backgroundReady()
                    try { validation.withLock {
                        if (!fullyValidated) {
                            withContext(StartupDispatchers.background) {
                                val context = currentCoroutineContext()
                                TacticsPackValidator.validateOnce(pack, requireNotNull(validationCache)) { context.ensureActive() }
                            }
                            fullyValidated = true
                        }
                    } } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        leavePuzzle()
                        _state.update { it.copy(pack = null, puzzle = null, error = "Tactics pack failed its checks") }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _state.update { it.copy(loading = false, error = "Tactics pack failed its checks") }
            }
        }
    }
    private fun validateHistory(pack: TacticsPack, history: TacticsHistory) {
        history.latestCycle
        history.customSets.forEach { c ->
            c.spec.validate()
            require(c.puzzleIds.size == c.spec.size && c.puzzleIds.distinct().size == c.puzzleIds.size)
            require(c.puzzleIds.all { it in pack.byId }) { "Saved tactics set is unavailable" }
        }
        val sets = pack.sets + history.customSets.map { it.asSet() }
        val cycles = history.cyclesBySet
        val attemptsByCycle = history.attemptsByCycle
        sets.forEach { set -> cycles[set.id].orEmpty().forEach { cycle ->
            val attempts = attemptsByCycle[set.id to cycle.cycle].orEmpty()
            val next = Woodpecker.nextOrdinal(set.puzzleIds, attempts)
            require(cycle.activeMs >= 0 && cycle.cycle >= 1)
            require((cycle.completedAt != null) == (next == set.puzzleIds.size))
        } }
    }
    private suspend fun validateSet(set: PuzzleSet) = validation.withLock {
        if (fullyValidated || set.id in checkedSets) return@withLock
        val pack = requireNotNull(_state.value.pack)
        withContext(StartupDispatchers.worker) {
            val context = currentCoroutineContext()
            TacticsPackValidator.validateSet(pack, set) { context.ensureActive() }
        }
        checkedSets.add(set.id)
    }
    fun openSet(id: String) {
        if (_state.value.sets.none { it.id == id }) return
        leavePuzzle()
        _state.update { it.copy(page = TacticsPage.SET, selectedSetId = id, message = null, puzzle = null) }
        scope.launch {
            try { _state.value.selectedSet?.let { validateSet(it) } }
            catch (e: Exception) {
                if (e is CancellationException) throw e
                _state.update { it.copy(error = "Tactics set failed its checks") }
            }
        }
    }
    fun back() {
        val page = _state.value.page
        leavePuzzle()
        _state.update { it.copy(page = if (page == TacticsPage.SET) TacticsPage.CATALOG else TacticsPage.SET, puzzle = null) }
    }
    private fun leavePuzzle() {
        accrue(); visible = false; runningSince = null; checkpoint()
        animation?.cancel(); generation++; session = null
    }
    fun startCycle() {
        val set = _state.value.selectedSet ?: return
        if (_state.value.saving || pending != null || store == null) {
            if (store == null) _state.update { it.copy(error = "Tactics history could not be saved") }; return
        }
        _state.update { it.copy(saving = true, error = null, message = null) }
        scope.launch {
            try {
                validateSet(set)
                if (_state.value.selectedSetId != set.id) { _state.update { it.copy(saving = false) }; return@launch }
                val (cycle, history) = writes.withLock { flushClocks(); store.startCycle(set.id, now()) to store.load() }
                withContext(StartupDispatchers.worker) { validateHistory(_state.value.pack!!, history) }
                val attempts = history.attempts.filter { it.setId == set.id && it.cycle == cycle.cycle }
                val ordinal = Woodpecker.nextOrdinal(set.puzzleIds, attempts)
                ids = set.puzzleIds; cycleMs = cycle.activeMs
                puzzleMs = (cycleMs - attempts.sumOf { it.activeMs }).coerceAtLeast(0)
                val previous = history.cycles.firstOrNull { it.setId == set.id && it.cycle == cycle.cycle - 1 }
                _state.update { it.copy(history = history, saving = false, page = TacticsPage.PUZZLE,
                    targetMs = Woodpecker.targetMs(ids.size, previous), activeMs = cycleMs) }
                loadPuzzle(ordinal, cycle.cycle, false)
            } catch (e: Exception) { failed(e) }
        }
    }
    fun retryMistakes(cycle: Int) {
        if (_state.value.saving || pending != null) return
        val set = _state.value.selectedSet ?: return
        val mistakes = _state.value.attempts(set.id, cycle).filterNot { it.correct }.map { it.puzzleId }
        if (mistakes.isEmpty()) return
        _state.update { it.copy(saving = true, error = null) }
        scope.launch {
            try {
                validateSet(set)
                if (_state.value.selectedSetId != set.id) { _state.update { it.copy(saving = false) }; return@launch }
                retrySessionId = UUID.randomUUID().toString()
                leavePuzzle(); ids = mistakes; cycleMs = 0; puzzleMs = 0
                _state.update { it.copy(saving = false, page = TacticsPage.PUZZLE, targetMs = null, activeMs = 0, message = null) }
                loadPuzzle(0, cycle, true)
            } catch (e: Exception) { failed(e) }
        }
    }

    private fun loadPuzzle(ordinal: Int, cycle: Int, practice: Boolean) {
        animation?.cancel(); generation++; runningSince = null
        val puzzle = _state.value.pack!!.byId.getValue(ids[ordinal])
        session = PuzzleSession(puzzle)
        _state.update { it.copy(puzzle = TacticsPuzzleUi(puzzle, session!!.setupPosition, session!!.solverSide, ordinal, ids.size, cycle, practice)) }
        animate()
    }
    fun setVisible(value: Boolean) {
        accrue(); visible = value; runningSince = null
        if (!value) { animation?.cancel(); checkpoint() } else { animate(); syncClock() }
    }
    private fun accrue() {
        val start = runningSince ?: return
        val at = elapsed(); val delta = (at - start).coerceAtLeast(0)
        cycleMs += delta; puzzleMs += delta; runningSince = at
        _state.update { it.copy(activeMs = cycleMs) }
    }
    private fun syncClock() {
        val s = _state.value
        runningSince = if (visible && s.page == TacticsPage.PUZZLE && s.puzzle?.phase == PuzzlePhase.READY && !s.saving) elapsed() else null
    }
    private fun checkpoint() {
        val p = _state.value.puzzle ?: return
        val set = _state.value.selectedSet ?: return
        if (p.practice || store == null) return
        val key = set.id to p.cycle
        pendingClocks[key] = ClockSave(set.id, p.cycle, maxOf(cycleMs, pendingClocks[key]?.activeMs ?: 0))
        scope.launch {
            try {
                val history = writes.withLock { flushClocks(); store.load() }
                _state.update { it.copy(history = history) }
            }
            catch (e: Exception) { if (e is CancellationException) throw e
                _state.update { it.copy(error = "Tactics time could not be saved. Keep the app open and retry.") }
            }
        }
    }
    private suspend fun flushClocks() {
        val store = store ?: return
        pendingClocks.toMap().forEach { (key, clock) ->
            store.checkpoint(clock.setId, clock.cycle, clock.activeMs)
            if (pendingClocks[key] == clock) pendingClocks.remove(key)
        }
    }
    fun tap(square: String) {
        val p = _state.value.puzzle ?: return
        if (p.phase != PuzzlePhase.READY || _state.value.saving || !visible) return
        if (p.selected != null && square in p.targets) {
            val choices = p.position.legalMoves(p.selected).filter { it.to == square }
            if (choices.any { it.promotion != null }) _state.update { it.copy(puzzle = p.copy(promotion = ChessMove(p.selected, square))) }
            else choices.singleOrNull()?.let(::submit)
        } else if (p.position.pieceAt(square)?.color == p.solver) {
            _state.update { it.copy(puzzle = p.copy(selected = square, targets = p.position.legalTargets(square))) }
        } else _state.update { it.copy(puzzle = p.copy(selected = null, targets = emptySet())) }
    }
    fun promote(type: PieceType) { _state.value.puzzle?.promotion?.let { submit(it.copy(promotion = type)) } }
    fun cancelPromotion() { _state.update { it.copy(puzzle = it.puzzle?.copy(promotion = null)) } }
    fun submit(move: ChessMove) {
        val p = _state.value.puzzle ?: return
        if (!visible || p.phase != PuzzlePhase.READY || _state.value.saving) return
        val session = session ?: return
        val setId = _state.value.selectedSetId ?: return
        val at = now()
        val day = java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        val path = if (p.practice) "retry:$retrySessionId:${p.puzzle.id}" else "${p.cycle}:${p.puzzle.id}"
        val eventId = "puzzle:$day:$setId:$path"
        if (loggedActivity.add(eventId)) onPuzzleAttempt(eventId, setId, path, at)
        accrue(); runningSince = null
        val clean = p.copy(selected = null, targets = emptySet(), promotion = null)
        when (val result = session.submit(move)) {
            is PuzzleResult.Wrong -> {
                _state.update { it.copy(puzzle = clean.copy(phase = PuzzlePhase.WRONG, hints = setOf(result.expected.from, result.expected.to), expected = result.expected)) }
                finishAttempt(false)
            }
            is PuzzleResult.Correct -> {
                _state.update { it.copy(puzzle = clean.copy(position = result.afterSolver, phase = PuzzlePhase.CORRECT)) }; animate()
            }
            is PuzzleResult.Solved -> {
                _state.update { it.copy(puzzle = clean.copy(position = result.position, phase = PuzzlePhase.SOLVED)) }; finishAttempt(true)
            }
        }
    }
    private fun finishAttempt(correct: Boolean) {
        val p = _state.value.puzzle ?: return
        if (p.practice) { animate(); return }
        val set = _state.value.selectedSet ?: return
        pending = Pending(TacticsAttempt(set.id, p.cycle, p.puzzle.id, p.ordinal, correct, puzzleMs, now()), ids, cycleMs)
        retrySave()
    }
    fun retrySave() {
        val write = pending ?: run {
            if (store != null && !_state.value.saving) {
                accrue(); runningSince = null
                scope.launch {
                    _state.update { it.copy(saving = true, error = null) }
                    try {
                        val history = writes.withLock { flushClocks(); store.load() }
                        _state.update { it.copy(history = history, saving = false) }; syncClock()
                    } catch (e: Exception) { failed(e) }
                }
            }
            return
        }
        if (_state.value.saving || store == null) return
        _state.update { it.copy(saving = true, error = null) }
        scope.launch {
            try {
                val history = writes.withLock {
                    store.recordAttempt(write.attempt, write.ids, write.activeMs)
                    val key = write.attempt.setId to write.attempt.cycle
                    if ((pendingClocks[key]?.activeMs ?: 0) <= write.activeMs) pendingClocks.remove(key)
                    store.load()
                }
                pending = null
                _state.update { it.copy(history = history, saving = false) }
                animate()
            } catch (e: Exception) { failed(e) }
        }
    }
    private fun failed(e: Exception) {
        if (e is CancellationException) throw e
        _state.update { it.copy(saving = false, error = "Tactics history could not be saved. Retry to keep this attempt.") }
    }
    fun setAutoNext(enabled: Boolean) { _state.update { it.copy(autoNext = enabled) }; onAutoNext(enabled); animate() }
    fun toggleAutoNext() = setAutoNext(!_state.value.autoNext)
    fun continueRecent() {
        val s = _state.value
        if (s.page == TacticsPage.PUZZLE) return
        val set = s.mostRecentSet() ?: s.sets.firstOrNull { it.id.startsWith("woodpecker-") } ?: return
        openSet(set.id)
        if (s.cycles(set.id).lastOrNull()?.completedAt == null) startCycle()
    }
    private fun animate() {
        animation?.cancel()
        if (!visible || _state.value.saving || pending != null) return
        val token = generation
        val phase = _state.value.puzzle?.phase ?: return
        animation = scope.launch {
            when (phase) {
                PuzzlePhase.SETUP, PuzzlePhase.CORRECT -> {
                    delay(if (phase == PuzzlePhase.SETUP) 350 else 300)
                    if (token != generation || !visible) return@launch
                    _state.update { it.copy(puzzle = it.puzzle?.copy(position = session!!.position, phase = PuzzlePhase.READY)) }; syncClock()
                }
                PuzzlePhase.SOLVED -> if (_state.value.autoNext) {
                    delay(600); if (token == generation && visible && _state.value.autoNext) next()
                }
                PuzzlePhase.SOLUTION -> playSolution(token)
                else -> Unit
            }
        }
    }
    fun showSolution() {
        if (_state.value.puzzle?.phase != PuzzlePhase.WRONG || _state.value.saving || pending != null) return
        _state.update { it.copy(puzzle = it.puzzle?.copy(phase = PuzzlePhase.SOLUTION, hints = emptySet())) }; animate()
    }
    private suspend fun playSolution(token: Int) {
        val session = session ?: return
        if (_state.value.puzzle?.position != session.position) {
            delay(350)
            _state.update { it.copy(puzzle = it.puzzle?.copy(position = session.position)) }
        }
        while (!session.solved && visible && token == generation) {
            delay(450)
            val result = session.submit(session.expected!!)
            val after = when (result) {
                is PuzzleResult.Correct -> result.afterSolver
                is PuzzleResult.Solved -> result.position
                is PuzzleResult.Wrong -> error("Checked solution is invalid")
            }
            _state.update { it.copy(puzzle = it.puzzle?.copy(position = after)) }
            if (result is PuzzleResult.Correct) { delay(350); _state.update { it.copy(puzzle = it.puzzle?.copy(position = result.position)) } }
        }
        if (visible && token == generation) _state.update { it.copy(puzzle = it.puzzle?.copy(phase = PuzzlePhase.SOLUTION_DONE)) }
    }
    fun next() {
        val p = _state.value.puzzle ?: return
        if (_state.value.saving || pending != null || p.phase !in setOf(PuzzlePhase.SOLVED, PuzzlePhase.WRONG, PuzzlePhase.SOLUTION_DONE)) return
        puzzleMs = 0; runningSince = null
        if (p.ordinal + 1 == p.count) {
            animation?.cancel(); session = null; generation++
            _state.update { it.copy(page = if (p.practice) TacticsPage.SET else TacticsPage.COMPLETE,
                message = if (p.practice) "Mistakes practice complete" else null, puzzle = null) }
        } else loadPuzzle(p.ordinal + 1, p.cycle, p.practice)
    }
    fun createSet(spec: CustomSetSpec) {
        val pack = _state.value.pack ?: return
        if (_state.value.saving || store == null) return
        _state.update { it.copy(saving = true, error = null, message = null) }
        scope.launch {
            try {
                val selected = withContext(StartupDispatchers.worker) { spec.select(pack) }
                val set = TacticsCustomSet("custom-${UUID.randomUUID()}", spec, selected, now())
                val history = writes.withLock { store.createCustomSet(set); store.load() }
                _state.update { it.copy(saving = false, history = history, message = "Set created", selectedSetId = set.id, page = TacticsPage.SET) }
            } catch (e: Exception) { if (e is CancellationException) throw e
                _state.update { it.copy(saving = false, error = e.message?.takeIf { it.startsWith("Only ") }
                    ?: "Set could not be created. Check the name and puzzle filters.") }
            }
        }
    }
    fun resetHistory() {
        val set = _state.value.selectedSet ?: return
        if (_state.value.saving || pending != null || store == null) return
        leavePuzzle(); _state.update { it.copy(saving = true) }
        pendingClocks.keys.filter { it.first == set.id }.forEach { pendingClocks.remove(it) }
        scope.launch {
            try {
                val history = writes.withLock { store.reset(set.id); store.load() }
                _state.update { it.copy(saving = false, history = history, page = TacticsPage.SET, puzzle = null, error = null) }
            } catch (e: Exception) { failed(e) }
        }
    }
    /** Read-only snapshot for Home/Profile; no implicit enrollment or learner writes. */
    fun todaySummary(): TacticsTodaySummary = today.value
}
