package com.openinglab.app.ui

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openinglab.app.content.BundledContent
import com.openinglab.app.content.ContentPackChoice
import com.openinglab.app.content.OpeningPresentation
import com.openinglab.app.content.OpeningPresentationCache
import com.openinglab.shared.analysis.*
import com.openinglab.shared.storage.*
import com.openinglab.shared.chess.BoardPosition
import com.openinglab.shared.chess.OpeningIdentifier
import com.openinglab.shared.chess.OpeningMatch
import com.openinglab.shared.chess.Pgn
import com.openinglab.shared.chess.PositionStatus
import com.openinglab.shared.chess.san
import com.openinglab.shared.data.OfflineFirstOpeningRepository
import com.openinglab.shared.data.OpeningRepository
import com.openinglab.shared.data.SourcedOpeningCatalog
import com.openinglab.shared.data.TeachingCatalog
import com.openinglab.shared.content.SourceKind
import com.openinglab.shared.content.ObservedGameSample
import com.openinglab.shared.content.ObservedReplyIndex
import com.openinglab.shared.chess.OpeningMatchKind
import com.openinglab.shared.lesson.LessonGraph
import com.openinglab.shared.lesson.LessonReplay
import com.openinglab.shared.lesson.LessonBranch
import com.openinglab.shared.lesson.AttemptKind
import com.openinglab.shared.lesson.ReplaySnapshot
import com.openinglab.shared.lesson.ReplayBranchVisit
import com.openinglab.shared.lesson.GroundedContinuation
import com.openinglab.shared.lesson.assess
import com.openinglab.shared.repertoire.*
import com.openinglab.shared.review.*
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import java.util.UUID
import com.openinglab.app.content.CourseFeedback
import com.openinglab.app.content.CourseFeedbackStore
import com.openinglab.shared.course.DeepCourseCatalog
import com.openinglab.shared.course.DeepCourseChapterView

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
    val repertoirePolicy: RepertoirePolicy? = null,
    val repertoireSetSession: RepertoireSetSession? = null,
    val recallHelp: Set<RecallHelp> = emptySet(), val studyExposedAt: Long? = null,
    val reviewScopeId: String? = null, val reviewTargetId: String? = null,
    val reviewAnswered: Boolean = false, val reviewSaved: Boolean = false,
    val reviewAttemptId: String? = null,
    /** Deep-course practice follows the chosen line without pausing at every opponent branch. */
    val courseAutoplay: Boolean = false,
) {
    val variation: Variation get() = opening.variations.first { it.id == replay.pathId }
    val playerSide: PieceColor get() = replay.playerSide
    val position: BoardPosition get() = replay.position
    val ply: Int get() = replay.ply
    val isComplete: Boolean get() = replay.atEnd || reviewAnswered
    val progress: Float get() = if (replay.moves.isEmpty()) 0f else ply.toFloat() / replay.moves.size
    val moveNumber: Int get() = position.fullmoveNumber
}

data class RepertoireEditorUiState(
    val book: RepertoireBook,
    val policy: RepertoirePolicy,
    val pathId: String,
    val ply: Int,
    val coverage: RepertoireCoverage,
    val unrecordedLegalMoves: Int,
    val saving: Boolean = false,
    val error: String? = null,
) {
    val path get() = book.graph.paths.getValue(pathId)
    val position get() = path.positions[ply]
    val options get() = book.optionsAt(position.positionKey)
}

sealed interface ObservedRepliesUiState {
    data object Missing : ObservedRepliesUiState
    data object Loading : ObservedRepliesUiState
    data class Ready(val index: ObservedReplyIndex) : ObservedRepliesUiState
    data object Error : ObservedRepliesUiState
}

sealed interface RepertoireOverviewUiState {
    data object Loading : RepertoireOverviewUiState
    data class Ready(val groups: List<RepertoireOverview>) : RepertoireOverviewUiState
    data object Error : RepertoireOverviewUiState
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
    val sourcedOpenings: List<Opening> = emptyList(),
    val teachingOpenings: List<Opening> = emptyList(),
    val catalogLoading: Boolean = false,
    val catalogError: String? = null,
    val lessonLoading: Boolean = false,
    val lessonError: String? = null,
    val repertoirePolicies: List<RepertoirePolicy> = emptyList(),
    val repertoireEditor: RepertoireEditorUiState? = null,
    val repertoireLoading: Boolean = false,
    val repertoireError: String? = null,
    val repertoireOverview: RepertoireOverviewUiState = RepertoireOverviewUiState.Loading,
    val repertoireSets: List<RepertoireSet> = emptyList(),
    val checkedSet: RepertoireSetPlan? = null,
    val setLoading: Boolean = false,
    val setError: String? = null,
    val observedReplies: ObservedRepliesUiState = ObservedRepliesUiState.Missing,
    val engineAnalysis: EngineAnalysisUiState = EngineAnalysisUiState.Idle,
    val deepCourses: List<DeepCourseChapterView> = emptyList(),
    val deepCourseLoading: Boolean = false,
    val deepCourseError: String? = null,
    val courseFeedback: List<CourseFeedback> = emptyList(),
    val feedbackMessage: String? = null,
)

class AppViewModel @JvmOverloads constructor(
    private val savedStateHandle: SavedStateHandle,
    private val openingRepository: OpeningRepository = OfflineFirstOpeningRepository(),
    private val learningStore: LearningStore? = null,
    private val packReader: ((ContentPackChoice) -> PackBundle)? = null,
    private val analysisEngine: ChessAnalysisEngine? = null,
    private val autoInstallBundledOpenings: Boolean = false,
    private val presentationCache: OpeningPresentationCache? = null,
    private val deepCourseSource: (() -> String)? = null,
    private val courseFeedbackStore: CourseFeedbackStore? = null,
) : ViewModel() {
    // Parsed and validated once, off Main in init; a restore that needs it earlier waits for the same lazy value.
    private val deepCourseResult: Result<DeepCourseCatalog?> by lazy {
        runCatching { deepCourseSource?.let { DeepCourseCatalog(listOf(DeepCourseCatalog.parse(it()))) } }
    }
    private fun isDeepCourse(id: String) = id.startsWith(DeepCourseCatalog.ID_PREFIX)
    /** Suspend lookup used by restores: a deep-course ID is parsed off Main if it is not loaded yet. */
    private suspend fun openingFor(id: String): Opening {
        if (isDeepCourse(id)) withContext(Dispatchers.Default) { deepCourseResult }
        return getOpening(id)
    }
    fun deepChapter(openingId: String): DeepCourseChapterView? = if (isDeepCourse(openingId)) deepCourseResult.getOrNull()?.chapter(openingId) else null

    val openings: List<Opening> = openingRepository.getOpenings()
    private val openingIdentifier = OpeningIdentifier(openings)
    private val lessonGraphs by lazy { openings.associate { it.id to LessonGraph.fromOpening(it) }.toMutableMap() }
    private val graphVersions = mutableMapOf<String, Pair<Int, String>>()
    private var sourceCatalog: SourcedOpeningCatalog? = null
    private var teachingCatalog: TeachingCatalog? = null
    private var autoInstallAttempted = false
    private var catalogLoaded = false
    private var sourceCatalogKey: String? = null
    private val catalogReady = CompletableDeferred<Unit>()
    private var lessonJob: Job? = null
    private var identifierJob: Job? = null
    private var identifierRevision = 0
    private var opponentJob: Job? = null
    private var playbackJob: Job? = null
    private var trainerRevision = 0
    private val _uiState = MutableStateFlow(
        AppUiState(trainer = if (savedStateHandle.get<Boolean>("active_game") == true) null else restoreTrainer(),
            identifier = IdentifierUiState(match = openingIdentifier.identify(emptyList())))
    )
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()
    private val bookmarkWrites = Channel<Pair<Int, LessonBookmark>>(Channel.CONFLATED)
    private var bookmarkRevision = 0
    private var persistedBookmarkRevision = -1
    private var packJob: Job? = null
    private var repertoireJob: Job? = null
    private var repertoireRequest = 0
    private var repertoireOverviewJob: Job? = null
    private var repertoireOverviewGeneration = 0
    private var setJob: Job? = null
    private var observedRepliesJob: Job? = null
    private var observedRepliesKey: List<Pair<String, String>>? = null
    private var observedRepliesGeneration = 0
    private var engineJob: Job? = null
    private var engineGeneration = 0

    val gameLibrary = GameLibraryController(learningStore, viewModelScope, savedStateHandle,
        onStart = { cancelTrainerJobs(); bookmarkRevision++; savedStateHandle["active_game"] = true },
        onBookmark = { bookmark ->
            if (learningStore != null) {
                bookmarkRevision++
                _uiState.update { it.copy(persistenceStatus = "Saving lesson…") }
                bookmarkWrites.trySend(bookmarkRevision to bookmark)
            }
        }, engine = analysisEngine)

    val recall = RecallController(learningStore, viewModelScope, ::prepareRecallEnrollment)

    private suspend fun prepareRecallEnrollment(trainer: TrainerUiState): RecallEnrollment {
        val set = trainer.repertoireSetSession?.plan?.set
        if (set != null) {
            require(trainer.repertoireSetSession.plan.ready)
            val entries = mutableListOf<RecallEntry>()
            for (ref in set.members) {
                val policy = requireNotNull(learningStore?.repertoirePolicy(ref.id, ref.revision))
                val graph = prepareGraph(getOpening(policy.lessonId))
                entries += withContext(Dispatchers.Default) {
                    val context = currentCoroutineContext()
                    val book = RepertoireBook(graph)
                    RecallPlanner.policy(book, policy) { context.ensureActive() }.entries
                }
                require(entries.size <= RecallEnrollment.MAX_CARDS) { "Review set exceeds local limits; no members omitted" }
            }
            val group = "set:${set.id}"
            return RecallEnrollment(RecallScope(scopeIdentity(group, entries, set.revision), group,
                "${set.name} · ${set.side.name.lowercase()} · set revision ${set.revision}", set.id, set.revision), entries)
        }
        val graph = prepareGraph(trainer.opening)
        return withContext(Dispatchers.Default) {
            val context = currentCoroutineContext()
            val book = RepertoireBook(graph)
            trainer.repertoirePolicy?.let { RecallPlanner.policy(book, it) { context.ensureActive() } }
                ?: RecallPlanner.route(book, trainer.playerSide, trainer.replay.pathId)
        }
    }

    fun startRecallReview(scopeId: String) {
        cancelTrainerJobs(); bookmarkRevision++
        val revision = trainerRevision
        _uiState.update { it.copy(lessonLoading = true, lessonError = null) }
        recall.next(scopeId) { card ->
            if (revision == trainerRevision) {
                lessonJob = viewModelScope.launch {
                    try {
                        requireNotNull(card) { "No due positions" }
                        catalogReady.await()
                        val target = card.target
                        val opening = openingFor(target.lessonId)
                        val base = prepareGraph(opening)
                        val book = withContext(Dispatchers.Default) { RepertoireBook(base) }
                        require(book.contentVersion == target.contentVersion)
                        val policy = card.context.policy?.let { requireNotNull(learningStore?.repertoirePolicy(it.id, it.revision)) }
                        val graph = if (policy == null) base else preparePolicyGraph(base, policy)
                        val path = card.context.pathId ?: target.pathId
                        val replay = graph.start(target.side, path).jump(target.ply)
                        require(replay.moves.take(target.ply).map { it.move.uci } == target.prefix && replay.nextMove?.move?.uci == target.expectedUci)
                        if (revision != trainerRevision) return@launch
                        _uiState.update { it.copy(lessonLoading = false) }
                        setTrainer(TrainerUiState(opening, replay, repertoirePolicy = policy,
                            reviewScopeId = scopeId, reviewTargetId = target.id,
                            feedback = "Recall this chosen move · ${target.side.name.lowercase()}",
                            explanation = "One exact-history decision. Hints, engine help and recent Study exposure count as assistance."))
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        if (revision == trainerRevision) _uiState.update { it.copy(trainer = null, lessonLoading = false,
                            lessonError = if (card == null) "No positions are due in this chosen scope. Return to Review for the next schedule."
                            else "This exact review content or policy revision is unavailable. Its history remains retained; no replacement was substituted.") }
                    }
                }
            }
        }
    }

    fun nextRecallReview() { _uiState.value.trainer?.reviewScopeId?.let(::startRecallReview) }

    init {
        if (deepCourseSource != null) {
            _uiState.update { it.copy(deepCourseLoading = true) }
            viewModelScope.launch {
                // After the opening catalog, so the bundled course never delays startup lessons.
                catalogReady.await()
                val result = withContext(Dispatchers.Default) { deepCourseResult }
                val feedback = withContext(Dispatchers.IO) { runCatching { courseFeedbackStore?.all().orEmpty() }.getOrDefault(emptyList()) }
                _uiState.update { state -> state.copy(deepCourseLoading = false, courseFeedback = feedback,
                    deepCourses = result.getOrNull()?.chapters.orEmpty(),
                    deepCourseError = result.exceptionOrNull()?.let { "The bundled deep course failed its checks and is not shown. Other lessons are unaffected." }) }
            }
        }
        val store = learningStore
        if (store == null) {
            catalogReady.complete(Unit)
            _uiState.update { it.copy(persistenceStatus = "Session-only preview") }
        }
        else {
            viewModelScope.launch {
                recall.state.collect { review ->
                    if (persistedBookmarkRevision == bookmarkRevision) _uiState.update { it.copy(persistenceStatus =
                        if (review.pendingWrites > 0) "Saving recall history…" else if (review.error != null && store.supportsRecall)
                            "Bookmark saved; recall needs attention. See Review for details." else "Saved for offline resume") }
                }
            }
            viewModelScope.launch {
                try { store.repertoireSets.collect { sets -> _uiState.update { it.copy(repertoireSets = sets) } } }
                catch (error: Exception) {
                    if (error is CancellationException) throw error
                    _uiState.update { it.copy(setError = "Saved multi-opening repertoires could not be loaded; data is retained.") }
                }
            }
            viewModelScope.launch {
                try { store.repertoirePolicies.collect { policies -> _uiState.update { it.copy(repertoirePolicies = policies) } } }
                catch (error: Exception) {
                    if (error is CancellationException) throw error
                    _uiState.update { it.copy(repertoireError = "Saved repertoires could not be loaded; data has been retained.") }
                }
            }
            viewModelScope.launch {
                try {
                    store.recoverInterruptedInstalls()
                    store.availability.collect { packs ->
                        val installed = store.activePacks()
                        if (autoInstallBundledOpenings && packReader != null && !autoInstallAttempted &&
                            installed.none { it.manifest.source.kind == SourceKind.OPENING_TAXONOMY }) {
                            autoInstallAttempted = true
                            _uiState.update { it.copy(catalogLoading = true) }
                            // Keep collecting installation state instead of blocking this collector
                            // with installation and then publishing its stale pre-install snapshot.
                            installPack("lichess-openings")
                        }
                        _uiState.update { it.copy(packs = packs, installedPacks = installed) }
                        gameLibrary.sourcesChanged()
                        refreshObservedReplies(store, installed)
                        refreshSourceCatalog(store, installed)
                    }
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    observedRepliesJob?.cancel()
                    observedRepliesKey = null
                    observedRepliesGeneration++
                    _uiState.update { it.copy(packError = "Offline database unavailable. Existing data has not been deleted.", observedReplies = ObservedRepliesUiState.Error) }
                    catalogReady.complete(Unit)
                }
            }
            viewModelScope.launch {
                for ((revision, bookmark) in bookmarkWrites) {
                    try {
                        store.saveBookmark(bookmark)
                        if (bookmark.gameReference == null) store.selectRepertoire(RepertoireSelection(bookmark.lessonId, bookmark.replay.playerSide.name,
                            bookmark.replay.branches.lastOrNull()?.targetPathId ?: bookmark.replay.rootPathId))
                        persistedBookmarkRevision = revision
                        if (revision == bookmarkRevision) _uiState.update { it.copy(persistenceStatus =
                            if (recall.state.value.pendingWrites > 0) "Saving recall history…" else if (recall.state.value.error != null && store.supportsRecall)
                                "Bookmark saved; recall needs attention. See Review for details." else "Saved for offline resume") }
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        if (revision == bookmarkRevision) _uiState.update { it.copy(persistenceStatus = "Could not save progress; keep the app open and retry a lesson action.") }
                    }
                }
            }
            viewModelScope.launch {
                val revision = bookmarkRevision
                try {
                    val bookmark = store.latestBookmark()
                    val gameBookmark = gameLibrary.savedBookmark() ?: bookmark?.takeIf {
                        it.gameReference != null && savedStateHandle.get<Boolean>("active_game") != false
                    }
                    if (gameBookmark != null && revision == bookmarkRevision) {
                        gameLibrary.restore(gameBookmark)
                        if (revision == bookmarkRevision) _uiState.update { it.copy(persistenceStatus =
                            if (gameLibrary.state.value.study != null) "Saved for offline resume" else "Saved original game is unavailable; its bookmark is retained.") }
                        return@launch
                    }
                    val saved = savedStateHandle.get<Bundle>("trainer_session")
                    val restoreId = saved?.getString("opening") ?: bookmark?.lessonId
                    // Seed/no-bookmark restoration must not wait for thousands of coached routes.
                    if (restoreId?.let { it.startsWith("source:") || it.startsWith("course:") } == true)
                        catalogReady.await()
                    if (revision == bookmarkRevision && _uiState.value.trainer == null) {
                        val savedOpening = saved?.getString("opening")?.let { runCatching { getOpening(it) }.getOrNull() }
                        if (savedOpening != null) prepareGraph(savedOpening)
                        val policy = saved?.getString("repertoire")?.let { store.repertoirePolicy(it, saved.getInt("repertoireRevision")) }
                        val policyGraph = if (policy != null && savedOpening != null) preparePolicyGraph(prepareGraph(savedOpening), policy) else null
                        val setSession = saved?.getString("setId")?.let { id ->
                            val set = requireNotNull(store.repertoireSet(id, saved.getInt("setRevision")))
                            RepertoireSetSession(buildSetPlan(set), saved.getInt("setIndex"))
                        }
                        val restored = verifyRestoredReview(restoreTrainer(saved, policy, policyGraph, setSession) ?: bookmark?.let { restoreBookmark(it) })
                        if (revision != bookmarkRevision) return@launch
                        _uiState.update { it.copy(trainer = restored, persistenceStatus = when {
                            bookmark == null -> "No saved lesson yet"
                            restored == null -> "Saved lesson content changed or is unavailable; progress has been retained."
                            else -> "Saved for offline resume"
                        }) }
                    } else if (revision == bookmarkRevision) {
                        _uiState.update { it.copy(persistenceStatus = "Restored session; saving for offline resume…") }
                        val checked = verifyRestoredReview(_uiState.value.trainer)
                        if (revision == bookmarkRevision) {
                            if (checked != null) setTrainer(checked)
                            else _uiState.update { it.copy(trainer = null, persistenceStatus = "Exact saved review context is unavailable; history retained.") }
                        }
                    }
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    _uiState.update { it.copy(persistenceStatus = "Saved progress could not be loaded.") }
                }
            }
        }
    }

    private suspend fun refreshSourceCatalog(store: LearningStore, installed: List<InstalledPack>) {
        val pack = installed.firstOrNull { it.manifest.source.kind == SourceKind.OPENING_TAXONOMY }
        if (catalogLoaded && sourceCatalogKey == pack?.manifestSha256) {
            if (pack == null) _uiState.update { it.copy(catalogLoading = it.packs.any { job -> job.state == "LOADING" }) }
            return
        }
        _uiState.update { it.copy(catalogLoading = true, catalogError = null) }
        try {
            val presentation = withContext(Dispatchers.Default) {
                val context = currentCoroutineContext()
                if (pack == null) null else {
                    val load: suspend () -> OpeningPresentation = {
                        val sourced = SourcedOpeningCatalog(pack.manifest, store.openings(pack.manifest.packId))
                        OpeningPresentation(sourced, TeachingCatalog(sourced) { context.ensureActive() })
                    }
                    presentationCache?.get("ashva-teaching/1:${pack.manifestSha256}", load) ?: load()
                }
            }
            val catalog = presentation?.source
            val teaching = presentation?.teaching
            sourceCatalog = catalog
            teachingCatalog = teaching
            sourceCatalogKey = pack?.manifestSha256
            catalogLoaded = true
            _uiState.update { it.copy(sourcedOpenings = catalog?.openings.orEmpty(), teachingOpenings = teaching?.openings.orEmpty(),
                catalogLoading = pack == null && it.packs.any { job -> job.state == "LOADING" }) }
            refreshIdentifierMatch()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            _uiState.update { it.copy(catalogLoading = false, catalogError = "Sourced catalog could not be loaded; existing lessons and data are retained.") }
        } finally { catalogReady.complete(Unit) }
    }

    private fun refreshObservedReplies(store: LearningStore, installed: List<InstalledPack>, force: Boolean = false) {
        val packs = installed.filter { it.manifest.source.kind == SourceKind.BROADCAST_GAMES }.sortedBy { it.manifest.packId }
        val key = packs.map { it.manifest.packId to it.manifestSha256 }
        if (!force && key == observedRepliesKey && _uiState.value.observedReplies != ObservedRepliesUiState.Error) return
        observedRepliesGeneration++
        observedRepliesKey = key
        observedRepliesJob?.cancel()
        _uiState.update { it.copy(observedReplies = if (packs.isEmpty()) ObservedRepliesUiState.Missing else ObservedRepliesUiState.Loading) }
        if (packs.isEmpty()) return
        observedRepliesJob = viewModelScope.launch {
            try {
                val index = withContext(Dispatchers.Default) {
                    val context = currentCoroutineContext()
                    val samples = packs.map { ObservedGameSample(it, store.games(it.manifest.packId)) }
                    ObservedReplyIndex.build(samples) { context.ensureActive() }
                }
                if (key == observedRepliesKey) _uiState.update { it.copy(observedReplies = ObservedRepliesUiState.Ready(index)) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (key == observedRepliesKey) _uiState.update { it.copy(observedReplies = ObservedRepliesUiState.Error) }
            }
        }
    }

    fun retryObservedReplies() {
        val store = learningStore ?: return
        val generation = observedRepliesGeneration
        viewModelScope.launch {
            try {
                // A retry must recheck active versions, not resurrect a cached/withdrawn pack list.
                val installed = store.activePacks()
                if (generation == observedRepliesGeneration) refreshObservedReplies(store, installed, force = true)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (generation == observedRepliesGeneration) {
                    observedRepliesGeneration++
                    observedRepliesJob?.cancel()
                    observedRepliesKey = null
                    _uiState.update { it.copy(observedReplies = ObservedRepliesUiState.Error) }
                }
            }
        }
    }

    private suspend fun prepareGraph(opening: Opening): LessonGraph {
        lessonGraphs[opening.id]?.let { return it }
        val prepared = withContext(Dispatchers.Default) {
            val graph = LessonGraph.fromOpening(opening)
            val description = contentDescription(graph)
            Triple(graph, description.hashCode(), contentSha256(description.encodeToByteArray()))
        }
        // Bound heavy sourced replay caches; active replay objects retain their own immutable graph.
        lessonGraphs.keys.filter { (it.startsWith("source:") || it.startsWith("course:")) && it != _uiState.value.trainer?.opening?.id }
            .take((lessonGraphs.keys.count { it.startsWith("source:") || it.startsWith("course:") } - 7).coerceAtLeast(0))
            .forEach { lessonGraphs.remove(it); graphVersions.remove(it) }
        lessonGraphs[opening.id] = prepared.first
        graphVersions[opening.id] = prepared.second to prepared.third
        return prepared.first
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

    fun getOpening(id: String): Opening = (if (isDeepCourse(id)) deepCourseResult.getOrNull()?.getOpening(id) else null) ?: teachingCatalog?.getOpening(id) ?: sourceCatalog?.getOpening(id) ?: openingRepository.getOpening(id)

    fun learningOpenings(): List<Opening> = _uiState.value.teachingOpenings.ifEmpty { openings }

    /** Picks a course line with probability equal to how often club games reach it (its measured weight). */
    fun practiceWeightedDeepLine(openingId: String, side: PieceColor, random: kotlin.random.Random = kotlin.random.Random.Default): String? {
        val chapter = deepChapter(openingId) ?: return null
        val weighted = chapter.lineWeights.filterValues { it > 0 }
        val choice = if (weighted.isEmpty()) chapter.opening.variations.random(random).id else {
            var roll = random.nextDouble() * weighted.values.sum()
            weighted.entries.firstOrNull { (_, w) -> roll -= w; roll <= 0 }?.key ?: weighted.keys.last()
        }
        startTrainer(openingId, side, choice)
        return choice
    }

    fun flagExplanation(kind: String) {
        val store = courseFeedbackStore ?: return
        val trainer = _uiState.value.trainer ?: return
        if (!isDeepCourse(trainer.opening.id)) return
        val move = trainer.replay.lastMove ?: return
        val feedback = CourseFeedback(trainer.opening.id, trainer.replay.pathId, trainer.ply, move.san, kind,
            move.annotation.explanation, move.annotation.label, System.currentTimeMillis())
        viewModelScope.launch {
            try {
                val all = withContext(Dispatchers.IO) { store.add(feedback) }
                _uiState.update { it.copy(courseFeedback = all, feedbackMessage = "Flag saved on this device: ${move.san} · $kind") }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.update { it.copy(feedbackMessage = "The flag could not be saved.") }
            }
        }
    }

    fun exportCourseFeedback(): String = courseFeedbackStore?.export() ?: "[]"


    fun primaryOpeningId(id: String): String {
        val old = runCatching { getOpening(id) }.getOrNull() ?: return id
        val name = old.name.lowercase().replace('ó', 'o')
        return _uiState.value.teachingOpenings.firstOrNull { it.name.lowercase().replace('ó', 'o') == name }?.id ?: id
    }

    fun loadRepertoireOverview() {
        repertoireOverviewJob?.cancel()
        val generation = ++repertoireOverviewGeneration
        val snapshot = _uiState.value
        val policies = snapshot.repertoirePolicies.toList()
        val catalogKey = sourceCatalogKey
        if (snapshot.repertoireError != null) {
            _uiState.update { it.copy(repertoireOverview = RepertoireOverviewUiState.Error) }
            return
        }
        _uiState.update { it.copy(repertoireOverview = RepertoireOverviewUiState.Loading) }
        repertoireOverviewJob = viewModelScope.launch {
            try {
                catalogReady.await()
                val groups = mutableListOf<RepertoireOverview>()
                for (side in PieceColor.entries) {
                    val builder = RepertoireOverviewBuilder(side)
                    for (policy in policies.filter { it.side == side }.sortedBy { it.id }) {
                        currentCoroutineContext().ensureActive()
                        val opening = if (policy.lessonId.startsWith("source:") && (snapshot.catalogLoading || snapshot.catalogError != null)) null
                            else runCatching { getOpening(policy.lessonId) }.getOrNull()
                        if (opening == null) {
                            builder.unavailable(policy, RepertoireMemberStatus.UNAVAILABLE)
                            continue
                        }
                        val graph = prepareGraph(opening)
                        withContext(Dispatchers.Default) {
                            val context = currentCoroutineContext()
                            val book = RepertoireBook(graph)
                            try { book.validate(policy) }
                            catch (_: IllegalArgumentException) {
                                builder.unavailable(policy, RepertoireMemberStatus.CHANGED)
                                return@withContext
                            }
                            builder.add(book, policy) { context.ensureActive() }
                        }
                    }
                    groups += withContext(Dispatchers.Default) {
                        val context = currentCoroutineContext()
                        builder.build { context.ensureActive() }
                    }
                }
                val current = _uiState.value
                if (generation == repertoireOverviewGeneration && current.repertoirePolicies == policies &&
                    catalogKey == sourceCatalogKey && current.catalogLoading == snapshot.catalogLoading && current.catalogError == snapshot.catalogError) _uiState.update {
                    it.copy(repertoireOverview = RepertoireOverviewUiState.Ready(groups))
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (generation == repertoireOverviewGeneration) _uiState.update { it.copy(repertoireOverview = RepertoireOverviewUiState.Error) }
            }
        }
    }

    fun cancelRepertoireOverview() {
        repertoireOverviewGeneration++
        repertoireOverviewJob?.cancel()
    }

    fun saveRepertoireSet(name: String, side: PieceColor, memberIds: List<String>, existing: RepertoireSet? = null) {
        if (setJob?.isActive == true) return
        val snapshot = _uiState.value.repertoirePolicies.associateBy { it.id }
        _uiState.update { it.copy(setLoading = true, setError = null) }
        setJob = viewModelScope.launch {
            try {
                val set = RepertoireSet(existing?.id ?: "repertoire-set:${UUID.randomUUID()}", name.trim(), side,
                    memberIds.map { id -> val p = requireNotNull(snapshot[id]); require(p.side == side); RepertoirePolicyRef(p.id, p.revision) },
                    existing?.revision?.plus(1) ?: 0).also { it.validate() }
                learningStore?.saveRepertoireSet(set)
                if (learningStore == null) _uiState.update { it.copy(repertoireSets = it.repertoireSets.filterNot { old -> old.id == set.id } + set) }
                _uiState.update { it.copy(setLoading = false, checkedSet = null) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.update { it.copy(setLoading = false, setError = "Set not saved. Choose a name and 1–64 distinct saved families of one color; reopen after a concurrent edit. Old revisions remain intact.") }
            }
        }
    }

    private suspend fun buildSetPlan(set: RepertoireSet): RepertoireSetPlan {
        val planner = RepertoireSetPlanner(set)
        val policies = set.members.map { ref ->
            requireNotNull(learningStore?.repertoirePolicy(ref.id, ref.revision) ?:
                _uiState.value.repertoirePolicies.firstOrNull { it.id == ref.id && it.revision == ref.revision }) { "Exact family revision is unavailable" }
        }
        if (policies.any { it.lessonId.startsWith("source:") || it.lessonId.startsWith("course:") }) catalogReady.await()
        for (policy in policies) {
            currentCoroutineContext().ensureActive()
            val opening = runCatching { getOpening(policy.lessonId) }.getOrNull()
            if (opening == null) { planner.unavailable(policy, RepertoireMemberStatus.UNAVAILABLE); continue }
            val graph = prepareGraph(opening)
            withContext(Dispatchers.Default) {
                val context = currentCoroutineContext()
                val book = RepertoireBook(graph)
                try { book.validate(policy) }
                catch (_: IllegalArgumentException) {
                    planner.unavailable(policy, RepertoireMemberStatus.CHANGED)
                    return@withContext
                }
                planner.add(book, policy) { context.ensureActive() }
            }
        }
        return withContext(Dispatchers.Default) {
            val context = currentCoroutineContext(); planner.build { context.ensureActive() }
        }
    }

    fun checkRepertoireSet(set: RepertoireSet) {
        if (setJob?.isActive == true) return
        _uiState.update { it.copy(setLoading = true, checkedSet = null, setError = null) }
        setJob = viewModelScope.launch {
            try {
                val plan = buildSetPlan(set)
                _uiState.update { it.copy(setLoading = false, checkedSet = plan) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.update { it.copy(setLoading = false, setError = "This set could not be checked. Exact saved members are retained; no family or route was silently omitted.") }
            }
        }
    }

    fun practiceRepertoireSet(plan: RepertoireSetPlan) {
        if (!plan.ready) return
        practiceSetItem(RepertoireSetSession(plan, 0))
    }

    fun moveSetPractice(delta: Int) {
        val session = _uiState.value.trainer?.repertoireSetSession ?: return
        val index = session.index + delta
        if (index !in session.plan.items.indices) return
        practiceSetItem(session.copy(index = index))
    }

    private fun practiceSetItem(session: RepertoireSetSession) {
        val policy = session.plan.overview.members.single { it.policy.id == session.item.policy.id }.policy
        practicePolicy(policy, session.item.pathId, session)
    }

    fun openRepertoire(openingId: String, side: PieceColor, pathId: String?, ply: Int = 0, forceCursor: Boolean = false) {
        pauseTrainer()
        val pending = repertoireJob
        val request = ++repertoireRequest
        _uiState.update { it.copy(repertoireEditor = null, repertoireLoading = true, repertoireError = null) }
        repertoireJob = viewModelScope.launch {
            try {
                pending?.join() // Never cancel an in-flight save when navigating to another policy.
                if (request != repertoireRequest) return@launch
                catalogReady.await()
                val graph = prepareGraph(getOpening(openingId))
                val book = withContext(Dispatchers.Default) { RepertoireBook(graph) }
                if (request != repertoireRequest) return@launch
                val stored = learningStore?.repertoirePolicy(book.policyId(side)) ?: _uiState.value.repertoirePolicies.firstOrNull { it.id == book.policyId(side) }
                val policy = stored ?: withContext(Dispatchers.Default) { book.seed(side, pathId?.takeIf { it in graph.paths } ?: graph.originalPathId) }
                withContext(Dispatchers.Default) { book.validate(policy) }
                if (stored == null) {
                    learningStore?.saveRepertoirePolicy(policy)
                    if (learningStore == null) _uiState.update { it.copy(repertoirePolicies = it.repertoirePolicies + policy) }
                }
                val saved = savedStateHandle.get<Bundle>("repertoire_cursor")?.takeIf { !forceCursor && it.getString("id") == policy.id }
                val cursorPath = saved?.getString("path")?.takeIf { it in graph.paths } ?: pathId?.takeIf { it in graph.paths } ?: graph.originalPathId
                val cursor = (saved?.getInt("ply") ?: ply).coerceIn(0, graph.paths.getValue(cursorPath).moves.size)
                val editor = withContext(Dispatchers.Default) { editorState(book, policy, cursorPath, cursor) }
                if (request != repertoireRequest) return@launch
                _uiState.update { it.copy(repertoireEditor = editor, repertoireLoading = false) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (request == repertoireRequest) _uiState.update { it.copy(repertoireLoading = false, repertoireError = "Repertoire unavailable or changed. Existing choices and progress were retained; reopen after checking the offline library.") }
            }
        }
    }

    private fun editorState(book: RepertoireBook, policy: RepertoirePolicy, pathId: String, ply: Int): RepertoireEditorUiState {
        val board = book.graph.paths.getValue(pathId).positions[ply]
        val recorded = book.optionsAt(board.positionKey).map { it.uci }.toSet()
        return RepertoireEditorUiState(book, policy, pathId, ply, book.coverage(policy), board.legalMoves().count { it.uci !in recorded })
    }

    fun moveRepertoireCursor(pathId: String, ply: Int) {
        val editor = _uiState.value.repertoireEditor ?: return
        if (repertoireJob?.isActive == true || editor.saving || pathId !in editor.book.graph.paths) return
        repertoireJob = viewModelScope.launch {
            val next = withContext(Dispatchers.Default) { editorState(editor.book, editor.policy, pathId,
                ply.coerceIn(0, editor.book.graph.paths.getValue(pathId).moves.size)) }
            savedStateHandle["repertoire_cursor"] = Bundle().apply { putString("id", editor.policy.id); putString("path", next.pathId); putInt("ply", next.ply) }
            _uiState.update { it.copy(repertoireEditor = next) }
        }
    }

    fun chooseRepertoireMove(uci: String, included: Boolean) = changeRepertoire { editor ->
        val key = editor.position.positionKey
        if (editor.position.sideToMove == editor.policy.side) editor.book.prefer(editor.policy, key, uci)
        else editor.book.include(editor.policy, key, uci, included)
    }

    fun includeAllRepertoireReplies() = changeRepertoire { it.book.includeAll(it.policy, it.position.positionKey) }
    fun adoptRepertoireRoute() = changeRepertoire { it.book.adoptRoute(it.policy, it.pathId) }

    private fun changeRepertoire(change: (RepertoireEditorUiState) -> RepertoirePolicy) {
        val editor = _uiState.value.repertoireEditor ?: return
        if (repertoireJob?.isActive == true || editor.saving) return
        _uiState.update { it.copy(repertoireEditor = editor.copy(saving = true, error = null)) }
        repertoireJob = viewModelScope.launch {
            try {
                val policy = withContext(Dispatchers.Default) { change(editor) }
                learningStore?.saveRepertoirePolicy(policy)
                val next = withContext(Dispatchers.Default) { editorState(editor.book, policy, editor.pathId, editor.ply) }
                if (learningStore == null) _uiState.update { it.copy(repertoirePolicies = it.repertoirePolicies.filterNot { p -> p.id == policy.id } + policy) }
                _uiState.update { it.copy(repertoireEditor = next) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.update { it.copy(repertoireEditor = editor.copy(error = "Choices were not saved. Reopen the editor to reload the latest revision before retrying.")) }
            }
        }
    }

    private suspend fun preparePolicyGraph(graph: LessonGraph, policy: RepertoirePolicy): LessonGraph {
        val prepared = withContext(Dispatchers.Default) {
            val practice = RepertoireBook(graph).practiceGraph(policy)
            val description = contentDescription(practice)
            Triple(practice, description.hashCode(), contentSha256(description.encodeToByteArray()))
        }
        // Only the active policy fingerprint is needed; the replay holds the immutable graph.
        graphVersions.keys.filter { ":policy:" in it }.forEach { graphVersions.remove(it) }
        graphVersions[prepared.first.id] = prepared.second to prepared.third
        return prepared.first
    }

    fun practiceRepertoire(policy: RepertoirePolicy, pathId: String? = null) {
        practicePolicy(policy, pathId, null)
    }

    private fun practicePolicy(policy: RepertoirePolicy, pathId: String?, session: RepertoireSetSession?) {
        cancelTrainerJobs()
        bookmarkRevision++
        val revision = trainerRevision
        // Keep the current queue screen mounted while preparing its next family. Removing it
        // would fire TrainerScreen.onDispose and cancel this new load via pauseTrainer.
        _uiState.update { it.copy(trainer = if (session == null) null else it.trainer?.copy(isPlaying = false, isOpponentThinking = false),
            lessonLoading = true, lessonError = null) }
        lessonJob = viewModelScope.launch {
            try {
                catalogReady.await()
                val opening = getOpening(policy.lessonId)
                val graph = preparePolicyGraph(prepareGraph(opening), policy)
                if (revision != trainerRevision) return@launch
                val selected = pathId?.takeIf { it in graph.paths } ?: graph.paths.values.maxBy { it.moves.size }.id
                _uiState.update { it.copy(lessonLoading = false) }
                setTrainer(TrainerUiState(opening, graph.start(policy.side, selected), repertoirePolicy = policy, repertoireSetSession = session,
                    feedback = "Your repertoire · ${policy.side.name.lowercase()}", explanation = "Only recorded routes fitting revision ${policy.revision} are included."))
                advanceOpponentIfNeeded()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.update { it.copy(lessonLoading = false, lessonError = "No available source route fits these choices. Edit the repertoire and fill its uncovered branches. Saved choices were retained.") }
            }
        }
    }

    fun filteredOpenings(): List<Opening> {
        val state = _uiState.value
        val results = if (state.selectedDifficulty == "Starter") openingRepository.searchOpenings(state.searchQuery)
            else if (state.selectedDifficulty == "Sourced") sourceCatalog?.search(state.searchQuery).orEmpty()
            else teachingCatalog?.search(state.searchQuery) ?: openingRepository.searchOpenings(state.searchQuery)
        return results.filter {
            state.selectedDifficulty == "All" ||
                state.selectedDifficulty == "Starter" ||
                (state.selectedDifficulty == "Sourced" && it.provenance != null) ||
                it.difficulty.name.equals(state.selectedDifficulty, ignoreCase = true)
        }
    }

    fun startTrainer(openingId: String, side: PieceColor, variationId: String? = null, study: Boolean = false) {
        cancelTrainerJobs()
        val opening = getOpening(openingId)
        if (gameLibrary.state.value.active) gameLibrary.leaveForOpening()
        val variation = opening.variations.firstOrNull { it.id == variationId } ?: opening.mainLine
        val deep = isDeepCourse(openingId)
        if (opening.provenance == null && !deep) {
            setTrainer(TrainerUiState(opening = opening, replay = lessonGraphs.getValue(openingId).start(side, variation.id),
                feedback = "Your move · ${side.name.lowercase()}"))
            if (study) studyTrainer() else advanceOpponentIfNeeded()
        } else {
            bookmarkRevision++ // A pending cold restore must not replace the newly requested lesson.
            val revision = trainerRevision
            _uiState.update { it.copy(trainer = null, lessonLoading = true, lessonError = null) }
            lessonJob = viewModelScope.launch {
                try {
                    val graph = prepareGraph(opening)
                    if (revision != trainerRevision) return@launch
                    _uiState.update { it.copy(lessonLoading = false) }
                    setTrainer(TrainerUiState(opening, graph.start(side, variation.id),
                        feedback = "Practice the ${if (deep) "deep course line" else if (opening.teaching != null) "opening course" else "source route"} · ${side.name.lowercase()}",
                        explanation = variation.description, courseAutoplay = deep))
                    if (study) studyTrainer() else advanceOpponentIfNeeded()
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    _uiState.update { it.copy(lessonLoading = false, lessonError = "This sourced lesson could not be prepared. No progress was deleted.") }
                }
            }
        }
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
        val attemptId = recall.record(trainer, move, attempt.kind) { id ->
            viewModelScope.launch {
                // Even an immediately completing test/local store must observe the answered state
                // and its event ID installed below, not race the return from record().
                kotlinx.coroutines.yield()
                val current = _uiState.value.trainer
                if (current?.reviewTargetId == trainer.reviewTargetId && current?.reviewScopeId == trainer.reviewScopeId && current?.reviewAnswered == true && current.reviewAttemptId == id)
                    setTrainer(current.copy(reviewSaved = true, feedback = "Review answer saved. Choose Next due position."))
            }
        }
        if (attempt.kind == AttemptKind.EXPECTED) {
            setTrainer(afterMove(trainer, trainer.replay.next()).copy(
                assistedMoves = trainer.assistedMoves + if (trainer.currentAssisted || trainer.recallHelp.isNotEmpty() || trainer.studyExposedAt?.let { System.currentTimeMillis() - it < RecallScheduler.RETRY } == true) 1 else 0,
                currentAssisted = false, recallHelp = emptySet(), reviewAnswered = trainer.reviewTargetId != null,
                reviewAttemptId = if (trainer.reviewTargetId == null) null else attemptId,
                feedback = if (trainer.reviewTargetId != null) "Saving review answer…" else afterMove(trainer, trainer.replay.next()).feedback))
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
                mistakes = trainer.mistakes + 1, currentAssisted = true, recallHelp = trainer.recallHelp + RecallHelp.AUTOMATIC_HINT,
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
                recallHelp = trainer.recallHelp + RecallHelp.HINT,
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
        setTrainer(atCursor(trainer.copy(mode = LessonMode.STUDY, hasStudied = true, studyExposedAt = System.currentTimeMillis()), trainer.replay))
    }

    fun practiceTrainer() { _uiState.value.trainer?.let(::beginPractice) }

    private fun beginPractice(trainer: TrainerUiState) {
        if (trainer.reviewTargetId != null) {
            if (trainer.reviewAnswered) { if (trainer.reviewSaved || recall.state.value.pendingWrites == 0) trainer.reviewScopeId?.let(::startRecallReview); return }
            cancelTrainerJobs()
            val revision = trainerRevision
            lessonJob = viewModelScope.launch {
                val card = learningStore?.recallCard(requireNotNull(trainer.reviewScopeId), trainer.reviewTargetId)
                if (revision == trainerRevision && card != null) setTrainer(atCursor(trainer.copy(mode = LessonMode.PRACTICE), trainer.replay.jump(card.target.ply)))
            }
            return
        }
        cancelTrainerJobs()
        setTrainer(atCursor(trainer.copy(mode = LessonMode.PRACTICE, mistakes = 0, assistedMoves = 0,
            currentAssisted = false, recallHelp = emptySet()), trainer.replay.first()))
        advanceOpponentIfNeeded()
    }

    fun flipTrainerSide() {
        val trainer = _uiState.value.trainer ?: return
        if (trainer.repertoirePolicy != null || trainer.reviewTargetId != null) return // Each color has its own policy/review.
        cancelTrainerJobs()
        val flipped = trainer.copy(replay = trainer.replay.withSide(trainer.playerSide.opposite),
            assistedMoves = 0, mistakes = 0, currentAssisted = false, recallHelp = emptySet())
        setTrainer(atCursor(flipped, flipped.replay))
        advanceOpponentIfNeeded()
    }

    fun jumpTrainer(ply: Int) {
        val trainer = _uiState.value.trainer ?: return
        cancelTrainerJobs()
        setTrainer(atCursor(trainer.copy(mode = LessonMode.STUDY, hasStudied = true, studyExposedAt = System.currentTimeMillis()), trainer.replay.jump(ply)))
    }

    fun stayOnTrainerLine() {
        val trainer = _uiState.value.trainer ?: return
        setTrainer(trainer.copy(branchOffers = emptyList(), attemptedMove = null))
        advanceOpponentIfNeeded()
    }

    fun switchTrainerBranch(pathId: String, targetPly: Int) {
        val trainer = _uiState.value.trainer ?: return
        if (trainer.reviewTargetId != null) return // Leave a single-card review to explore other variations.
        val branch = trainer.branchOffers.singleOrNull { it.pathId == pathId && it.targetPly == targetPly } ?: return
        cancelTrainerJobs()
        var replay = trainer.replay.diverge(branch)
        val applyAttempt = trainer.mode == LessonMode.PRACTICE && trainer.attemptedMove == branch.nextMove.move
        if (applyAttempt) {
            recall.record(trainer.copy(replay = replay, currentAssisted = true, recallHelp = trainer.recallHelp + RecallHelp.BRANCH), branch.nextMove.move, AttemptKind.EXPECTED)
            replay = replay.next()
        }
        val updated = if (applyAttempt) afterMove(trainer, replay).copy(
            assistedMoves = trainer.assistedMoves + 1, currentAssisted = false, recallHelp = emptySet()) else
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

    fun analyzeTrainer() {
        pauseTrainer() // Analysis is explicit; no pondering, autoplay or simultaneous lesson reply.
        val trainer = _uiState.value.trainer ?: return
        val engine = analysisEngine
        if (engine == null) {
            _uiState.update { it.copy(engineAnalysis = EngineAnalysisUiState.Error("Offline engine is unavailable in this install.")) }
            return
        }
        val position = AnalysisPosition(trainer.replay.graph.initialPosition.toFen(),
            trainer.replay.moves.take(trainer.ply).map { it.move.uci })
        val original = trainer.attemptedMove?.takeIf { trainer.position.isLegal(it) }?.uci ?: trainer.replay.nextMove?.move?.uci
        val generation = engineGeneration
        _uiState.update { it.copy(engineAnalysis = EngineAnalysisUiState.Loading) }
        engineJob = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.Default) { engine.analyze(position, original) }
                require(result.position == position) { "Analysis root mismatch" }
                val continuations = withContext(Dispatchers.Default) {
                    (result.alternatives?.lines.orEmpty() + result.original?.lines.orEmpty()).map { line ->
                        val context = currentCoroutineContext()
                        GroundedContinuation.build(position.board(), line.uci, line.san) { context.ensureActive() }
                    }
                }
                if (generation == engineGeneration) {
                    val current = _uiState.value.trainer
                    if (continuations.isNotEmpty() && current?.mode == LessonMode.PRACTICE && !current.isComplete && current.position.sideToMove == current.playerSide &&
                        current.opening.id == trainer.opening.id && current.replay.moves.take(current.ply).map { it.move.uci } == position.moves)
                        setTrainer(current.copy(currentAssisted = true, recallHelp = current.recallHelp + RecallHelp.ENGINE))
                    _uiState.update { it.copy(engineAnalysis = EngineAnalysisUiState.Ready(result,
                        continuations.map { it.positions }, previewExplanations = continuations.map { it.explanations })) }
                }
            } catch (cancelled: CancellationException) {
                if (!currentCoroutineContext().isActive) throw cancelled
                if (generation == engineGeneration) _uiState.update { it.copy(engineAnalysis = EngineAnalysisUiState.Error("Analysis timed out. Retry with this position; no partial result was promoted to a recommendation.")) }
            }
            catch (_: Exception) {
                if (generation == engineGeneration) _uiState.update { it.copy(engineAnalysis = EngineAnalysisUiState.Error(
                    "Analysis could not complete. Retry; unsupported devices or invalid engine output are never shown as recommendations.")) }
            }
        }
    }

    fun stopEngineAnalysis() {
        engineGeneration++
        engineJob?.cancel(); engineJob = null
        _uiState.update { it.copy(engineAnalysis = EngineAnalysisUiState.Idle) }
    }

    fun exploreEngineLine(index: Int) {
        val ready = _uiState.value.engineAnalysis as? EngineAnalysisUiState.Ready ?: return
        if (index !in ready.lines.indices) return
        _uiState.update { it.copy(engineAnalysis = ready.copy(previewIndex = index, previewPly = 1)) }
    }

    fun jumpEnginePreview(ply: Int) {
        val ready = _uiState.value.engineAnalysis as? EngineAnalysisUiState.Ready ?: return
        val index = ready.previewIndex ?: return
        if (ply !in ready.previewPositions[index].indices) return
        _uiState.update { it.copy(engineAnalysis = ready.copy(previewPly = ply)) }
    }

    fun returnFromEnginePreview() {
        val ready = _uiState.value.engineAnalysis as? EngineAnalysisUiState.Ready ?: return
        _uiState.update { it.copy(engineAnalysis = ready.copy(previewIndex = null, previewPly = 0)) }
    }

    private fun cancelTrainerJobs() {
        stopEngineAnalysis()
        trainerRevision++
        opponentJob?.cancel(); opponentJob = null
        playbackJob?.cancel(); playbackJob = null
        lessonJob?.cancel(); lessonJob = null
        _uiState.update { it.copy(lessonLoading = false) }
    }

    private fun atCursor(trainer: TrainerUiState, replay: LessonReplay): TrainerUiState = trainer.copy(
        replay = replay, selectedSquare = null, legalTargets = emptySet(), hintSquares = emptySet(),
        pendingPromotion = null, attemptedMove = null, isPlaying = false, isOpponentThinking = false,
        branchOffers = if (trainer.reviewTargetId == null && !(trainer.courseAutoplay && trainer.mode == LessonMode.PRACTICE)) replay.branches() else emptyList(),
        feedbackKind = FeedbackKind.NONE,
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
        if (gameLibrary.state.value.active) gameLibrary.leaveForOpening()
        val previous = _uiState.value.trainer
        if (previous == null || previous.opening.id != trainer.opening.id || previous.replay.pathId != trainer.replay.pathId ||
            previous.ply != trainer.ply || previous.attemptedMove != trainer.attemptedMove) stopEngineAnalysis()
        _uiState.update { it.copy(trainer = trainer) }
        recall.trainerChanged(trainer, previous)
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
            putStringArrayList("recallHelp", ArrayList(trainer.recallHelp.map { it.name }))
            trainer.studyExposedAt?.let { putLong("studyExposedAt", it) }
            putString("reviewScopeId", trainer.reviewScopeId); putString("reviewTargetId", trainer.reviewTargetId)
            putBoolean("reviewAnswered", trainer.reviewAnswered); putBoolean("reviewSaved", trainer.reviewSaved)
            putString("reviewAttemptId", trainer.reviewAttemptId)
            putBoolean("hint", trainer.hintSquares.isNotEmpty())
            putBoolean("offer", trainer.branchOffers.isNotEmpty())
            putString("attempt", trainer.attemptedMove?.uci)
            putString("promotion", trainer.pendingPromotion?.uci)
            putLong("speed", trainer.playbackDelayMillis)
            putString("repertoire", trainer.repertoirePolicy?.id)
            trainer.repertoirePolicy?.let { putInt("repertoireRevision", it.revision) }
            trainer.repertoireSetSession?.let {
                putString("setId", it.plan.set.id); putInt("setRevision", it.plan.set.revision); putInt("setIndex", it.index)
            }
        }
        if (learningStore != null) {
            bookmarkRevision++
            _uiState.update { it.copy(persistenceStatus = "Saving lesson…") }
            bookmarkWrites.trySend(bookmarkRevision to LessonBookmark(trainer.opening.id, durableContentVersion(trainer.replay.graph), snapshot,
                trainer.mode.name, trainer.mistakes, trainer.assistedMoves, trainer.currentAssisted, trainer.hasStudied,
                trainer.hintSquares.isNotEmpty(), trainer.branchOffers.isNotEmpty(), trainer.attemptedMove?.uci,
                trainer.pendingPromotion?.uci, trainer.playbackDelayMillis, trainer.repertoirePolicy?.id, trainer.repertoirePolicy?.revision,
                trainer.repertoireSetSession?.plan?.set?.id, trainer.repertoireSetSession?.plan?.set?.revision, trainer.repertoireSetSession?.index,
                recallHelp = trainer.recallHelp, studyExposedAt = trainer.studyExposedAt, reviewScopeId = trainer.reviewScopeId,
                reviewTargetId = trainer.reviewTargetId, reviewAnswered = trainer.reviewAnswered, reviewAttemptId = trainer.reviewAttemptId))
        }
    }

    private fun contentDescription(graph: LessonGraph) = graph.paths.values.joinToString("\n") {
        "${it.id}|${it.description}|${it.whiteIdea}|${it.blackIdea}|" + it.moves.joinToString(";") { move ->
            "${move.move.uci}|${move.san}|${move.annotation}"
        }
    }
    private fun contentFingerprint(graph: LessonGraph) = graphVersions[graph.id]?.first ?: contentDescription(graph).hashCode()
    private fun durableContentVersion(graph: LessonGraph) = graphVersions[graph.id]?.second ?: contentSha256(contentDescription(graph).encodeToByteArray())

    private fun restoreTrainer(saved: Bundle? = savedStateHandle.get<Bundle>("trainer_session"), policy: RepertoirePolicy? = null, policyGraph: LessonGraph? = null,
        setSession: RepertoireSetSession? = null): TrainerUiState? = runCatching {
        if (saved == null) return null
        require(saved.getInt("version") == 1)
        val opening = getOpening(requireNotNull(saved.getString("opening")))
        require(saved.getString("repertoire") == policy?.id)
        if (policy != null) require(saved.getInt("repertoireRevision") == policy.revision)
        require(saved.getString("setId") == setSession?.plan?.set?.id)
        if (setSession != null) {
            require(saved.getInt("setRevision") == setSession.plan.set.revision && saved.getInt("setIndex") == setSession.index)
            require(setSession.item.lessonId == opening.id && setSession.item.policy == RepertoirePolicyRef(requireNotNull(policy).id, policy.revision))
            require(saved.getString("root") == setSession.item.pathId)
        }
        val graph = policyGraph ?: lessonGraphs.getValue(opening.id)
        require(saved.getInt("content") == contentFingerprint(graph))
        val from = requireNotNull(saved.getIntArray("from"))
        val to = requireNotNull(saved.getIntArray("to"))
        val paths = requireNotNull(saved.getStringArrayList("paths"))
        require(from.size == to.size && from.size == paths.size && paths.size <= LessonReplay.MAX_BRANCH_DEPTH)
        val replay = LessonReplay.restore(graph, ReplaySnapshot(requireNotNull(saved.getString("root")),
            PieceColor.valueOf(requireNotNull(saved.getString("side"))), saved.getInt("ply"),
            paths.mapIndexed { i, path -> ReplayBranchVisit(from[i], path, to[i]) }))
        val attempt = saved.getString("attempt")?.let(ChessMove::fromUci)
        require(policy == null || replay.playerSide == policy.side)
        val restored = atCursor(TrainerUiState(opening, replay, repertoirePolicy = policy, repertoireSetSession = setSession,
            mode = LessonMode.valueOf(requireNotNull(saved.getString("mode"))),
            mistakes = saved.getInt("mistakes"), assistedMoves = saved.getInt("assisted"),
            currentAssisted = saved.getBoolean("currentAssisted"), hasStudied = saved.getBoolean("studied"),
            recallHelp = saved.getStringArrayList("recallHelp").orEmpty().map { RecallHelp.valueOf(it) }.toSet(),
            studyExposedAt = if (saved.containsKey("studyExposedAt")) saved.getLong("studyExposedAt") else null,
            reviewScopeId = saved.getString("reviewScopeId"), reviewTargetId = saved.getString("reviewTargetId"),
            reviewAnswered = saved.getBoolean("reviewAnswered"), reviewSaved = saved.getBoolean("reviewSaved"),
            reviewAttemptId = saved.getString("reviewAttemptId"),
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

    private suspend fun restoreBookmark(bookmark: LessonBookmark): TrainerUiState? = try {
        val base = prepareGraph(openingFor(bookmark.lessonId))
        val policy = bookmark.repertoireId?.let { requireNotNull(learningStore?.repertoirePolicy(it, requireNotNull(bookmark.repertoireRevision))) }
        val graph = if (policy == null) base else preparePolicyGraph(base, policy)
        val setSession = bookmark.repertoireSetId?.let { id ->
            val set = requireNotNull(learningStore?.repertoireSet(id, bookmark.repertoireSetRevision))
            RepertoireSetSession(buildSetPlan(set), requireNotNull(bookmark.repertoireSetIndex))
        }
        require(bookmark.contentVersion == durableContentVersion(graph))
        bookmark.reviewTargetId?.let { requireNotNull(learningStore?.recallCard(requireNotNull(bookmark.reviewScopeId), it)) }
        val snapshot = bookmark.replay
        restoreTrainer(Bundle().apply {
            putInt("version", 1); putString("opening", bookmark.lessonId); putInt("content", contentFingerprint(graph))
            putString("root", snapshot.rootPathId); putString("side", snapshot.playerSide.name); putInt("ply", snapshot.ply)
            putIntArray("from", snapshot.branches.map { it.fromPly }.toIntArray())
            putIntArray("to", snapshot.branches.map { it.targetPly }.toIntArray())
            putStringArrayList("paths", ArrayList(snapshot.branches.map { it.targetPathId }))
            putString("mode", bookmark.mode); putInt("mistakes", bookmark.mistakes); putInt("assisted", bookmark.assisted)
            putBoolean("currentAssisted", bookmark.currentAssisted); putBoolean("studied", bookmark.studied)
            putStringArrayList("recallHelp", ArrayList(bookmark.recallHelp.map { it.name }))
            bookmark.studyExposedAt?.let { putLong("studyExposedAt", it) }
            putString("reviewScopeId", bookmark.reviewScopeId); putString("reviewTargetId", bookmark.reviewTargetId)
            putBoolean("reviewAnswered", bookmark.reviewAnswered)
            putString("reviewAttemptId", bookmark.reviewAttemptId)
            // Actual persisted card state, not a previously displayed Saved label.
            putBoolean("reviewSaved", bookmark.reviewAnswered && bookmark.reviewAttemptId?.let {
                learningStore?.hasRecallEvent(it, requireNotNull(bookmark.reviewTargetId))
            } == true)
            putBoolean("hint", bookmark.hint); putBoolean("offer", bookmark.offerBranches)
            putString("attempt", bookmark.attemptUci); putString("promotion", bookmark.promotionUci); putLong("speed", bookmark.speedMillis)
            putString("repertoire", policy?.id); policy?.let { putInt("repertoireRevision", it.revision) }
            setSession?.let { putString("setId", it.plan.set.id); putInt("setRevision", it.plan.set.revision); putInt("setIndex", it.index) }
        }, policy, if (policy == null) null else graph, setSession)
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        null
    }

    private suspend fun verifyRestoredReview(trainer: TrainerUiState?): TrainerUiState? {
        if (trainer?.reviewTargetId == null) return trainer
        return try {
            val card = requireNotNull(learningStore?.recallCard(requireNotNull(trainer.reviewScopeId), trainer.reviewTargetId))
            val base = prepareGraph(trainer.opening)
            val book = withContext(Dispatchers.Default) { RepertoireBook(base) }
            require(card.target.contentVersion == book.contentVersion && trainer.playerSide == card.target.side)
            require(trainer.replay.moves.take(card.target.ply).map { it.move.uci } == card.target.prefix && trainer.replay.moves.getOrNull(card.target.ply)?.move?.uci == card.target.expectedUci)
            require(card.context.policy == trainer.repertoirePolicy?.let { RepertoirePolicyRef(it.id, it.revision) })
            val saved = trainer.reviewAnswered && trainer.reviewAttemptId?.let { learningStore.hasRecallEvent(it, card.target.id) } == true
            trainer.copy(reviewSaved = saved, feedback = if (trainer.reviewAnswered && !saved)
                "Answer write was interrupted; return to Review or restart to retry. It was not counted." else trainer.feedback)
        } catch (e: Exception) { if (e is CancellationException) throw e; null }
    }

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
        refreshIdentifierMatch()
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
        refreshIdentifierMatch()
    }

    fun loadIdentifierExample(openingId: String) {
        cancelIdentifierWork()
        val opening = getOpening(openingId)
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
            // Include a catalog that finished loading while this import was running.
            refreshIdentifierMatch()
        }
    }

    private fun identifierState(initial: BoardPosition, moves: List<String>): IdentifierUiState {
        var board = initial
        val notation = moves.map { uci ->
            val move = ChessMove.fromUci(uci)
            board.san(move).also { board = board.apply(move) }
        }
        return IdentifierUiState(initialPosition = initial, position = board, moves = moves,
            sanMoves = notation, positionStatus = board.status(), match = identifyWithSource(initial, moves))
    }

    private fun identifyWithSource(initial: BoardPosition, moves: List<String>, catalog: SourcedOpeningCatalog? = sourceCatalog): OpeningMatch {
        val seed = openingIdentifier.identify(initial, moves)
        if (seed.kind == OpeningMatchKind.INVALID || catalog == null) return seed
        val positions = mutableListOf(initial)
        moves.forEach { positions += positions.last().apply(ChessMove.fromUci(it)) }
        val source = catalog.identify(positions) ?: return seed
        return if ((source.matchedPly ?: -1) >= (seed.matchedPly ?: -1) || !seed.isNamedPosition) source else seed
    }

    private fun refreshIdentifierMatch() {
        // A catalog notification must not cancel an import and strand its loading state.
        if (_uiState.value.identifier.isLoading) return
        cancelIdentifierWork()
        val revision = identifierRevision
        val state = _uiState.value.identifier
        val catalog = sourceCatalog
        identifierJob = viewModelScope.launch {
            val match = withContext(Dispatchers.Default) { identifyWithSource(state.initialPosition, state.moves, catalog) }
            if (revision == identifierRevision) _uiState.update { it.copy(identifier = it.identifier.copy(match = match)) }
        }
    }

    private fun cancelIdentifierWork() {
        identifierRevision++
        identifierJob?.cancel()
        identifierJob = null
    }
}
