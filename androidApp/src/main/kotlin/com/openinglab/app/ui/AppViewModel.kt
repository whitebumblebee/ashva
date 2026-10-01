package com.openinglab.app.ui

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openinglab.app.content.BundledContent
import com.openinglab.app.content.ContentPackChoice
import com.openinglab.shared.storage.*
import com.openinglab.shared.chess.BoardPosition
import com.openinglab.shared.chess.OpeningIdentifier
import com.openinglab.shared.chess.OpeningMatch
import com.openinglab.shared.chess.Pgn
import com.openinglab.shared.chess.PositionStatus
import com.openinglab.shared.chess.san
import com.openinglab.shared.data.OfflineFirstOpeningRepository
import com.openinglab.shared.data.OpeningRepository
import com.openinglab.shared.lesson.LessonGraph
import com.openinglab.shared.lesson.LessonReplay
import com.openinglab.shared.lesson.LessonBranch
import com.openinglab.shared.lesson.AttemptKind
import com.openinglab.shared.lesson.ReplaySnapshot
import com.openinglab.shared.lesson.ReplayBranchVisit
import com.openinglab.shared.lesson.assess
import com.openinglab.shared.model.ChessMove
import com.openinglab.shared.model.Opening
import com.openinglab.shared.model.PieceColor
import com.openinglab.shared.model.PieceType
import com.openinglab.shared.model.Variation
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CancellationException
import java.util.UUID

enum class MainTab { LEARN, EXPLORE, REVIEW, PROFILE }
enum class FeedbackKind { NONE, SUCCESS, ERROR, COMPLETE }
enum class LessonMode { STUDY, PRACTICE }

data class TrainerUiState(
    val opening: Opening,
    val replay: LessonReplay,
    val selectedSquare: String? = null,
    val legalTargets: Set<String> = emptySet(),
    val hintSquares: Set<String> = emptySet(),
    val feedbackKind: FeedbackKind = FeedbackKind.NONE,
    val feedback: String = "Find the best move",
    val explanation: String = "Play the opening one idea at a time.",
    val mistakes: Int = 0,
    val isOpponentThinking: Boolean = false,
    val mode: LessonMode = LessonMode.PRACTICE,
    val isPlaying: Boolean = false,
    val playbackDelayMillis: Long = 1200,
    val branchOffers: List<LessonBranch> = emptyList(),
    val attemptedMove: ChessMove? = null,
    val pendingPromotion: ChessMove? = null,
    val assistedMoves: Int = 0,
    val currentAssisted: Boolean = false,
    val hasStudied: Boolean = false,
) {
    val variation: Variation get() = opening.variations.first { it.id == replay.pathId }
    val playerSide: PieceColor get() = replay.playerSide
    val position: BoardPosition get() = replay.position
    val ply: Int get() = replay.ply
    val isComplete: Boolean get() = replay.atEnd
    val progress: Float get() = if (replay.moves.isEmpty()) 0f else ply.toFloat() / replay.moves.size
    val moveNumber: Int get() = position.fullmoveNumber
}

data class IdentifierUiState(
    val initialPosition: BoardPosition = BoardPosition.starting(),
    val position: BoardPosition = BoardPosition.starting(),
    val moves: List<String> = emptyList(),
    val sanMoves: List<String> = emptyList(),
    val selectedSquare: String? = null,
    val legalTargets: Set<String> = emptySet(),
    val match: OpeningMatch,
    val pendingPromotion: ChessMove? = null,
    val error: String? = null,
    val isLoading: Boolean = false,
    val positionStatus: PositionStatus = PositionStatus.ONGOING,
)

data class AppUiState(
    val selectedTab: MainTab = MainTab.LEARN,
    val searchQuery: String = "",
    val selectedDifficulty: String = "All",
    val trainer: TrainerUiState? = null,
    val identifier: IdentifierUiState,
    val persistenceStatus: String = "Loading saved lesson…",
    val packs: List<PackAvailability> = emptyList(),
    val installedPacks: List<InstalledPack> = emptyList(),
    val packError: String? = null,
)

class AppViewModel @JvmOverloads constructor(
    private val savedStateHandle: SavedStateHandle,
    private val openingRepository: OpeningRepository = OfflineFirstOpeningRepository(),
    private val learningStore: LearningStore? = null,
    private val packReader: ((ContentPackChoice) -> PackBundle)? = null,
) : ViewModel() {
    val openings: List<Opening> = openingRepository.getOpenings()
    private val openingIdentifier = OpeningIdentifier(openings)
    private val lessonGraphs by lazy { openings.associate { it.id to LessonGraph.fromOpening(it) } }
    private var identifierJob: Job? = null
    private var identifierRevision = 0
    private var opponentJob: Job? = null
    private var playbackJob: Job? = null
    private var trainerRevision = 0
    private val _uiState = MutableStateFlow(
        AppUiState(trainer = restoreTrainer(), identifier = IdentifierUiState(match = openingIdentifier.identify(emptyList())))
    )
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()
    private val bookmarkWrites = Channel<LessonBookmark>(Channel.CONFLATED)
    private var bookmarkRevision = 0
    private var packJob: Job? = null

    init {
        val store = learningStore
        if (store == null) _uiState.update { it.copy(persistenceStatus = "Session-only preview") }
        else {
            viewModelScope.launch {
                try {
                    store.availability.collect { packs ->
                        val installed = store.activePacks()
                        _uiState.update { it.copy(packs = packs, installedPacks = installed) }
                    }
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    _uiState.update { it.copy(packError = "Offline database unavailable. Existing data has not been deleted.") }
                }
            }
            viewModelScope.launch {
                for (bookmark in bookmarkWrites) {
                    val revision = bookmarkRevision
                    try {
                        store.saveBookmark(bookmark)
                        store.selectRepertoire(RepertoireSelection(bookmark.lessonId, bookmark.replay.playerSide.name,
                            bookmark.replay.branches.lastOrNull()?.targetPathId ?: bookmark.replay.rootPathId))
                        if (revision == bookmarkRevision) _uiState.update { it.copy(persistenceStatus = "Saved for offline resume") }
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        _uiState.update { it.copy(persistenceStatus = "Could not save progress; keep the app open and retry a lesson action.") }
                    }
                }
            }
            viewModelScope.launch {
                val revision = bookmarkRevision
                try {
                    store.recoverInterruptedInstalls()
                    val bookmark = store.latestBookmark()
                    if (revision == bookmarkRevision && _uiState.value.trainer == null) {
                        val restored = bookmark?.let(::restoreBookmark)
                        _uiState.update { it.copy(trainer = restored, persistenceStatus = when {
                            bookmark == null -> "No saved lesson yet"
                            restored == null -> "Saved lesson content changed or is unavailable; progress has been retained."
                            else -> "Saved for offline resume"
                        }) }
                    } else if (revision == bookmarkRevision) {
                        _uiState.update { it.copy(persistenceStatus = "Restored session; saving for offline resume…") }
                        _uiState.value.trainer?.let(::setTrainer)
                    }
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    _uiState.update { it.copy(persistenceStatus = "Saved progress could not be loaded.") }
                }
            }
        }
    }

    fun installPack(sourceId: String) {
        val store = learningStore ?: return
        val reader = packReader ?: return
        if (packJob?.isActive == true) return
        val choice = BundledContent.choices.first { it.sourceId == sourceId }
        _uiState.update { it.copy(packError = null) }
        packJob = viewModelScope.launch {
            try {
                val bundle = withContext(Dispatchers.IO) { reader(choice) }
                store.install(bundle)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.update { it.copy(packError = "Installation failed. ${error.message.orEmpty().take(240)}") }
            }
        }
    }

    fun selectTab(tab: MainTab) = _uiState.update { it.copy(selectedTab = tab) }
    fun updateSearch(query: String) = _uiState.update { it.copy(searchQuery = query) }
    fun selectDifficulty(filter: String) = _uiState.update { it.copy(selectedDifficulty = filter) }

    fun getOpening(id: String): Opening = openingRepository.getOpening(id)

    fun filteredOpenings(): List<Opening> {
        val state = _uiState.value
        return openingRepository.searchOpenings(state.searchQuery).filter {
            state.selectedDifficulty == "All" || it.difficulty.name.equals(state.selectedDifficulty, ignoreCase = true)
        }
    }

    fun startTrainer(openingId: String, side: PieceColor, variationId: String? = null) {
        cancelTrainerJobs()
        val opening = openingRepository.getOpening(openingId)
        val variation = opening.variations.firstOrNull { it.id == variationId } ?: opening.mainLine
        setTrainer(TrainerUiState(opening = opening, replay = lessonGraphs.getValue(openingId).start(side, variation.id),
            feedback = "Your move · ${side.name.lowercase()}"))
        advanceOpponentIfNeeded()
    }

    fun trainerTap(square: String) {
        val trainer = _uiState.value.trainer ?: return
        if (trainer.mode != LessonMode.PRACTICE || trainer.isComplete || trainer.isOpponentThinking ||
            trainer.pendingPromotion != null || trainer.position.sideToMove != trainer.playerSide) return

        if (trainer.selectedSquare == null) {
            val piece = trainer.position.pieceAt(square)
            if (piece?.color == trainer.playerSide) {
                setTrainer(trainer.copy(
                        selectedSquare = square,
                        legalTargets = trainer.position.legalTargets(square),
                    ))
            }
            return
        }

        if (square == trainer.selectedSquare) {
            clearTrainerSelection()
            return
        }

        if (trainer.position.pieceAt(square)?.color == trainer.playerSide) {
            setTrainer(trainer.copy(selectedSquare = square, legalTargets = trainer.position.legalTargets(square)))
            return
        }
        val move = ChessMove.fromUci(trainer.selectedSquare + square)
        if (trainer.position.pieceAt(move.from)?.type == PieceType.PAWN && move.to[1] in "18" &&
            trainer.position.legalMoves().any { it.from == move.from && it.to == move.to }) {
            setTrainer(trainer.copy(pendingPromotion = move, selectedSquare = null, legalTargets = emptySet()))
        } else attemptTrainerMove(move)
    }

    fun chooseTrainerPromotion(type: PieceType) {
        val move = _uiState.value.trainer?.pendingPromotion ?: return
        if (type in listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT))
            attemptTrainerMove(move.copy(promotion = type))
    }

    fun cancelTrainerPromotion() {
        _uiState.value.trainer?.let { setTrainer(it.copy(pendingPromotion = null)) }
    }

    private fun attemptTrainerMove(move: ChessMove) {
        val trainer = _uiState.value.trainer ?: return
        val attempt = trainer.replay.assess(move)
        learningStore?.let { store -> viewModelScope.launch {
            try {
                store.recordAttempt(LearningAttempt(UUID.randomUUID().toString(), trainer.opening.id,
                    trainer.replay.pathId, trainer.ply, trainer.playerSide.name, move.uci, attempt.kind.name,
                    trainer.currentAssisted || attempt.kind != AttemptKind.EXPECTED, System.currentTimeMillis()))
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.update { it.copy(persistenceStatus = "Could not save this attempt.") }
            }
        } }
        if (attempt.kind == AttemptKind.EXPECTED) {
            setTrainer(afterMove(trainer, trainer.replay.next()).copy(
                assistedMoves = trainer.assistedMoves + if (trainer.currentAssisted) 1 else 0,
                currentAssisted = false))
            advanceOpponentIfNeeded()
        } else {
            setTrainer(trainer.copy(selectedSquare = null, legalTargets = emptySet(), pendingPromotion = null,
                hintSquares = setOf(attempt.expected.move.from, attempt.expected.move.to),
                feedbackKind = FeedbackKind.ERROR,
                feedback = when (attempt.kind) {
                    AttemptKind.ILLEGAL -> "That move is not legal here. Try ${attempt.expected.san}."
                    AttemptKind.AVAILABLE_BRANCH -> "That move belongs to another variation. Stay with ${attempt.expected.san}, or switch below."
                    else -> "Legal move, but outside this lesson. Play ${attempt.expected.san}."
                },
                explanation = attempt.expected.annotation.explanation,
                mistakes = trainer.mistakes + 1, currentAssisted = true,
                branchOffers = attempt.branches, attemptedMove = move))
        }
    }

    fun showHint() {
        val trainer = _uiState.value.trainer ?: return
        val step = trainer.replay.nextMove ?: return
        if (trainer.mode != LessonMode.PRACTICE || trainer.isOpponentThinking) return
        setTrainer(trainer.copy(
                selectedSquare = null,
                legalTargets = emptySet(),
                hintSquares = setOf(step.move.from, step.move.to),
                feedback = "Play ${step.san} · ${step.move.from} → ${step.move.to}",
                explanation = step.annotation.explanation,
                currentAssisted = true,
            ))
    }

    fun restartTrainer() {
        val trainer = _uiState.value.trainer ?: return
        beginPractice(trainer)
    }

    private fun clearTrainerSelection() {
        _uiState.value.trainer?.let { setTrainer(it.copy(selectedSquare = null, legalTargets = emptySet())) }
    }

    fun studyTrainer() {
        val trainer = _uiState.value.trainer ?: return
        cancelTrainerJobs()
        setTrainer(atCursor(trainer.copy(mode = LessonMode.STUDY, hasStudied = true), trainer.replay))
    }

    fun practiceTrainer() { _uiState.value.trainer?.let(::beginPractice) }

    private fun beginPractice(trainer: TrainerUiState) {
        cancelTrainerJobs()
        setTrainer(atCursor(trainer.copy(mode = LessonMode.PRACTICE, mistakes = 0, assistedMoves = 0,
            currentAssisted = false), trainer.replay.first()))
        advanceOpponentIfNeeded()
    }

    fun flipTrainerSide() {
        val trainer = _uiState.value.trainer ?: return
        cancelTrainerJobs()
        val flipped = trainer.copy(replay = trainer.replay.withSide(trainer.playerSide.opposite),
            assistedMoves = 0, mistakes = 0, currentAssisted = false)
        setTrainer(atCursor(flipped, flipped.replay))
        advanceOpponentIfNeeded()
    }

    fun jumpTrainer(ply: Int) {
        val trainer = _uiState.value.trainer ?: return
        cancelTrainerJobs()
        setTrainer(atCursor(trainer.copy(mode = LessonMode.STUDY, hasStudied = true), trainer.replay.jump(ply)))
    }

    fun stayOnTrainerLine() {
        val trainer = _uiState.value.trainer ?: return
        setTrainer(trainer.copy(branchOffers = emptyList(), attemptedMove = null))
        advanceOpponentIfNeeded()
    }

    fun switchTrainerBranch(pathId: String, targetPly: Int) {
        val trainer = _uiState.value.trainer ?: return
        val branch = trainer.branchOffers.singleOrNull { it.pathId == pathId && it.targetPly == targetPly } ?: return
        cancelTrainerJobs()
        var replay = trainer.replay.diverge(branch)
        val applyAttempt = trainer.mode == LessonMode.PRACTICE && trainer.attemptedMove == branch.nextMove.move
        if (applyAttempt) replay = replay.next()
        val updated = if (applyAttempt) afterMove(trainer, replay).copy(
            assistedMoves = trainer.assistedMoves + 1, currentAssisted = false) else
            atCursor(trainer, replay).copy(branchOffers = emptyList())
        setTrainer(updated.copy(feedback = "${if (applyAttempt) "Played ${branch.nextMove.san} · " else ""}${branch.name}"))
        advanceOpponentIfNeeded()
    }

    fun returnTrainerBranch() {
        val trainer = _uiState.value.trainer ?: return
        cancelTrainerJobs()
        setTrainer(atCursor(trainer, trainer.replay.returnToBranch()))
        advanceOpponentIfNeeded()
    }

    fun changeTrainerSpeed() {
        _uiState.value.trainer?.let {
            setTrainer(it.copy(playbackDelayMillis = when (it.playbackDelayMillis) { 700L -> 1200; 1200L -> 2000; else -> 700 }))
        }
    }

    fun toggleTrainerPlayback() {
        val trainer = _uiState.value.trainer ?: return
        if (trainer.isPlaying) { pauseTrainer(); return }
        if (trainer.mode != LessonMode.STUDY || trainer.isComplete || trainer.branchOffers.isNotEmpty()) return
        cancelTrainerJobs()
        val revision = trainerRevision
        setTrainer(trainer.copy(isPlaying = true))
        playbackJob = viewModelScope.launch {
            while (revision == trainerRevision) {
                delay(_uiState.value.trainer?.playbackDelayMillis ?: 1200)
                val current = _uiState.value.trainer ?: break
                if (!current.isPlaying || current.isComplete || current.branchOffers.isNotEmpty()) break
                val next = atCursor(current, current.replay.next())
                val playing = !next.isComplete && next.branchOffers.isEmpty()
                setTrainer(next.copy(isPlaying = playing))
                if (!playing) break
            }
        }
    }

    fun pauseTrainer() {
        cancelTrainerJobs()
        _uiState.value.trainer?.let { setTrainer(it.copy(isPlaying = false, isOpponentThinking = false)) }
    }

    fun resumeTrainer() = advanceOpponentIfNeeded()

    private fun cancelTrainerJobs() {
        trainerRevision++
        opponentJob?.cancel(); opponentJob = null
        playbackJob?.cancel(); playbackJob = null
    }

    private fun atCursor(trainer: TrainerUiState, replay: LessonReplay): TrainerUiState = trainer.copy(
        replay = replay, selectedSquare = null, legalTargets = emptySet(), hintSquares = emptySet(),
        pendingPromotion = null, attemptedMove = null, isPlaying = false, isOpponentThinking = false,
        branchOffers = replay.branches(), feedbackKind = FeedbackKind.NONE,
        feedback = if (trainer.mode == LessonMode.STUDY) {
            if (replay.atStart) "Starting position" else "${replay.ply} / ${replay.moves.size} half-moves"
        } else if (replay.atEnd) "Practice complete" else "${replay.position.sideToMove.name.lowercase().replaceFirstChar { it.uppercase() }} to move",
        explanation = replay.lastMove?.annotation?.explanation ?: replay.path.description,
    )

    private fun afterMove(trainer: TrainerUiState, replay: LessonReplay): TrainerUiState = atCursor(trainer, replay).copy(
        feedbackKind = if (replay.atEnd) FeedbackKind.COMPLETE else FeedbackKind.SUCCESS,
        feedback = if (replay.atEnd) "Practice complete" else "${replay.lastMove?.san} · ${replay.lastMove?.annotation?.title.orEmpty()}",
    )

    private fun advanceOpponentIfNeeded() {
        val initial = _uiState.value.trainer ?: return
        if (initial.mode != LessonMode.PRACTICE || initial.isComplete || initial.isOpponentThinking ||
            initial.branchOffers.isNotEmpty() || initial.position.sideToMove == initial.playerSide) return
        val revision = trainerRevision
        setTrainer(initial.copy(isOpponentThinking = true, feedback = "Playing the lesson reply…"))
        opponentJob = viewModelScope.launch {
            delay(620)
            val current = _uiState.value.trainer ?: return@launch
            if (revision != trainerRevision || current.mode != LessonMode.PRACTICE || current.isComplete ||
                current.branchOffers.isNotEmpty() || current.position.sideToMove == current.playerSide) return@launch
            setTrainer(afterMove(current, current.replay.next()))
        }
    }

    private fun setTrainer(trainer: TrainerUiState) {
        _uiState.update { it.copy(trainer = trainer) }
        val snapshot = trainer.replay.snapshot()
        savedStateHandle["trainer_session"] = Bundle().apply {
            putInt("version", 1)
            putString("opening", trainer.opening.id)
            putInt("content", contentFingerprint(trainer.replay.graph))
            putString("root", snapshot.rootPathId)
            putString("side", snapshot.playerSide.name)
            putInt("ply", snapshot.ply)
            putIntArray("from", snapshot.branches.map { it.fromPly }.toIntArray())
            putIntArray("to", snapshot.branches.map { it.targetPly }.toIntArray())
            putStringArrayList("paths", ArrayList(snapshot.branches.map { it.targetPathId }))
            putString("mode", trainer.mode.name)
            putInt("mistakes", trainer.mistakes)
            putInt("assisted", trainer.assistedMoves)
            putBoolean("currentAssisted", trainer.currentAssisted)
            putBoolean("studied", trainer.hasStudied)
            putBoolean("hint", trainer.hintSquares.isNotEmpty())
            putBoolean("offer", trainer.branchOffers.isNotEmpty())
            putString("attempt", trainer.attemptedMove?.uci)
            putString("promotion", trainer.pendingPromotion?.uci)
            putLong("speed", trainer.playbackDelayMillis)
        }
        if (learningStore != null) {
            bookmarkRevision++
            _uiState.update { it.copy(persistenceStatus = "Saving lesson…") }
            bookmarkWrites.trySend(LessonBookmark(trainer.opening.id, durableContentVersion(trainer.replay.graph), snapshot,
                trainer.mode.name, trainer.mistakes, trainer.assistedMoves, trainer.currentAssisted, trainer.hasStudied,
                trainer.hintSquares.isNotEmpty(), trainer.branchOffers.isNotEmpty(), trainer.attemptedMove?.uci,
                trainer.pendingPromotion?.uci, trainer.playbackDelayMillis))
        }
    }

    private fun contentDescription(graph: LessonGraph) = graph.paths.values.joinToString("\n") {
        "${it.id}|${it.description}|${it.whiteIdea}|${it.blackIdea}|" + it.moves.joinToString(";") { move ->
            "${move.move.uci}|${move.san}|${move.annotation}"
        }
    }
    private fun contentFingerprint(graph: LessonGraph) = contentDescription(graph).hashCode()
    private fun durableContentVersion(graph: LessonGraph) = contentSha256(contentDescription(graph).encodeToByteArray())

    private fun restoreTrainer(saved: Bundle? = savedStateHandle.get<Bundle>("trainer_session")): TrainerUiState? = runCatching {
        if (saved == null) return null
        require(saved.getInt("version") == 1)
        val opening = getOpening(requireNotNull(saved.getString("opening")))
        val graph = lessonGraphs.getValue(opening.id)
        require(saved.getInt("content") == contentFingerprint(graph))
        val from = requireNotNull(saved.getIntArray("from"))
        val to = requireNotNull(saved.getIntArray("to"))
        val paths = requireNotNull(saved.getStringArrayList("paths"))
        require(from.size == to.size && from.size == paths.size && paths.size <= LessonReplay.MAX_BRANCH_DEPTH)
        val replay = LessonReplay.restore(graph, ReplaySnapshot(requireNotNull(saved.getString("root")),
            PieceColor.valueOf(requireNotNull(saved.getString("side"))), saved.getInt("ply"),
            paths.mapIndexed { i, path -> ReplayBranchVisit(from[i], path, to[i]) }))
        val attempt = saved.getString("attempt")?.let(ChessMove::fromUci)
        val restored = atCursor(TrainerUiState(opening, replay,
            mode = LessonMode.valueOf(requireNotNull(saved.getString("mode"))),
            mistakes = saved.getInt("mistakes"), assistedMoves = saved.getInt("assisted"),
            currentAssisted = saved.getBoolean("currentAssisted"), hasStudied = saved.getBoolean("studied"),
            playbackDelayMillis = saved.getLong("speed").takeIf { it in listOf(700L, 1200L, 2000L) } ?: 1200), replay)
        val next = replay.nextMove
        restored.copy(
            attemptedMove = attempt,
            pendingPromotion = saved.getString("promotion")?.let(ChessMove::fromUci),
            branchOffers = if (!saved.getBoolean("offer")) emptyList() else if (attempt != null && next != null)
                replay.assess(attempt).branches else replay.branches(),
            hintSquares = if (saved.getBoolean("hint") && next != null) setOf(next.move.from, next.move.to) else emptySet(),
            feedback = if (saved.getBoolean("hint") && next != null) "Play ${next.san} · ${next.move.from} → ${next.move.to}" else restored.feedback,
        )
    }.getOrNull()

    private fun restoreBookmark(bookmark: LessonBookmark): TrainerUiState? = runCatching {
        val graph = lessonGraphs.getValue(bookmark.lessonId)
        require(bookmark.contentVersion == durableContentVersion(graph))
        val snapshot = bookmark.replay
        restoreTrainer(Bundle().apply {
            putInt("version", 1); putString("opening", bookmark.lessonId); putInt("content", contentFingerprint(graph))
            putString("root", snapshot.rootPathId); putString("side", snapshot.playerSide.name); putInt("ply", snapshot.ply)
            putIntArray("from", snapshot.branches.map { it.fromPly }.toIntArray())
            putIntArray("to", snapshot.branches.map { it.targetPly }.toIntArray())
            putStringArrayList("paths", ArrayList(snapshot.branches.map { it.targetPathId }))
            putString("mode", bookmark.mode); putInt("mistakes", bookmark.mistakes); putInt("assisted", bookmark.assisted)
            putBoolean("currentAssisted", bookmark.currentAssisted); putBoolean("studied", bookmark.studied)
            putBoolean("hint", bookmark.hint); putBoolean("offer", bookmark.offerBranches)
            putString("attempt", bookmark.attemptUci); putString("promotion", bookmark.promotionUci); putLong("speed", bookmark.speedMillis)
        })
    }.getOrNull()

    fun identifierTap(square: String) {
        val state = _uiState.value.identifier
        if (state.pendingPromotion != null || state.isLoading) return
        if (state.selectedSquare == null) {
            val targets = state.position.legalTargets(square)
            if (targets.isNotEmpty()) {
                _uiState.update { it.copy(identifier = state.copy(selectedSquare = square, legalTargets = targets)) }
            }
            return
        }
        if (square == state.selectedSquare) {
            _uiState.update { it.copy(identifier = state.copy(selectedSquare = null, legalTargets = emptySet())) }
            return
        }
        if (square !in state.legalTargets) {
            val targets = state.position.legalTargets(square)
            _uiState.update { it.copy(identifier = state.copy(selectedSquare = square.takeIf { targets.isNotEmpty() }, legalTargets = targets)) }
            return
        }
        val move = state.selectedSquare + square
        val parsed = ChessMove.fromUci(move)
        if (state.position.pieceAt(parsed.from)?.type == PieceType.PAWN && parsed.to[1] in "18") {
            _uiState.update { it.copy(identifier = state.copy(pendingPromotion = parsed, selectedSquare = null, legalTargets = emptySet())) }
            return
        }
        applyIdentifierMove(parsed)
    }

    fun chooseIdentifierPromotion(type: PieceType) {
        val state = _uiState.value.identifier
        val move = state.pendingPromotion ?: return
        val promoted = move.copy(promotion = type)
        if (state.position.isLegal(promoted)) applyIdentifierMove(promoted)
    }

    fun cancelIdentifierPromotion() = _uiState.update {
        it.copy(identifier = it.identifier.copy(pendingPromotion = null))
    }

    private fun applyIdentifierMove(move: ChessMove) {
        val state = _uiState.value.identifier
        if (!state.position.isLegal(move)) return
        cancelIdentifierWork()
        val moves = state.moves + move.uci
        val next = state.position.apply(move)
        _uiState.update {
            it.copy(identifier = state.copy(
                position = next,
                moves = moves,
                sanMoves = state.sanMoves + state.position.san(move),
                selectedSquare = null,
                legalTargets = emptySet(),
                pendingPromotion = null,
                error = null,
                positionStatus = next.status(),
                match = openingIdentifier.identify(state.initialPosition, moves),
            ))
        }
    }

    fun resetIdentifier() {
        cancelIdentifierWork()
        _uiState.update { it.copy(identifier = IdentifierUiState(match = openingIdentifier.identify(emptyList()))) }
    }

    fun undoIdentifier() {
        cancelIdentifierWork()
        val state = _uiState.value.identifier
        val moves = state.moves.dropLast(1)
        val position = moves.fold(state.initialPosition) { board, uci -> board.apply(ChessMove.fromUci(uci)) }
        _uiState.update {
            it.copy(identifier = IdentifierUiState(initialPosition = state.initialPosition, position = position,
                moves = moves, sanMoves = state.sanMoves.dropLast(1), positionStatus = position.status(),
                match = openingIdentifier.identify(state.initialPosition, moves)))
        }
    }

    fun loadIdentifierExample(openingId: String) {
        cancelIdentifierWork()
        val opening = openingRepository.getOpening(openingId)
        val moves = opening.mainLine.steps.take(6).map { it.uci }
        val state = identifierState(BoardPosition.starting(), moves)
        _uiState.update {
            it.copy(identifier = state)
        }
    }

    /** Parsing/replaying imported games must not block Compose rendering. */
    fun importIdentifier(text: String, isFen: Boolean) {
        cancelIdentifierWork()
        val revision = identifierRevision
        _uiState.update { it.copy(identifier = it.identifier.copy(isLoading = true, error = null, pendingPromotion = null)) }
        identifierJob = viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                runCatching {
                    require(text.length <= 64 * 1024) { "Use at most 65,536 characters for this single-game explorer." }
                    if (isFen) identifierState(BoardPosition.fromFen(text), emptyList()) else {
                        val game = Pgn.parse(text)
                        require(game.line.plies.size <= 600) { "This explorer accepts up to 600 half-moves per game." }
                        identifierState(game.initialPosition, game.line.plies.map { it.move.uci })
                    }
                }
            }
            if (revision != identifierRevision) return@launch
            _uiState.update { current -> current.copy(identifier = result.getOrElse {
                current.identifier.copy(isLoading = false, error = it.message ?: "Unable to import game.")
            }) }
        }
    }

    private fun identifierState(initial: BoardPosition, moves: List<String>): IdentifierUiState {
        var board = initial
        val notation = moves.map { uci ->
            val move = ChessMove.fromUci(uci)
            board.san(move).also { board = board.apply(move) }
        }
        return IdentifierUiState(initialPosition = initial, position = board, moves = moves,
            sanMoves = notation, positionStatus = board.status(), match = openingIdentifier.identify(initial, moves))
    }

    private fun cancelIdentifierWork() {
        identifierRevision++
        identifierJob?.cancel()
        identifierJob = null
    }
}
