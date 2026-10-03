// SPDX-License-Identifier: Apache-2.0
package com.openinglab.app.ui

import androidx.lifecycle.SavedStateHandle
import com.openinglab.shared.content.SourceKind
import com.openinglab.shared.games.*
import com.openinglab.shared.lesson.*
import com.openinglab.shared.model.PieceColor
import com.openinglab.shared.storage.*
import com.openinglab.shared.analysis.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Screen-level state holder; lazy metadata loading never prepares every game graph on launch. */
class GameLibraryController(
    private val store: LearningStore?, private val scope: CoroutineScope,
    private val saved: SavedStateHandle,
    private val onStart: () -> Unit,
    private val onBookmark: (LessonBookmark) -> Unit,
    private val engine: ChessAnalysisEngine? = null,
) {
    private val _state = MutableStateFlow(GameLearningUiState())
    val state: StateFlow<GameLearningUiState> = _state.asStateFlow()
    private var requested = false
    private var privateGames = emptyList<PrivateGameRecord>()
    private var libraryJob: Job? = null
    private var libraryGeneration = 0
    private var searchJob: Job? = null
    private var searchGeneration = 0
    private var studyJob: Job? = null
    private var studyGeneration = 0
    private var playback: Job? = null
    private var key: String? = null
    private val json = Json { encodeDefaults = true }
    private var teachingJob: Job? = null
    private var teachingGeneration = 0
    private var teachingKey: String? = null
    // Main-confined, bounded and disposable; exact content/ply/version keys, not GM intent.
    private val teachingCache = linkedMapOf<String, OriginalMoveTeaching>()
    private var analysisJob: Job? = null
    private var analysisGeneration = 0

    init {
        if (store != null) {
            scope.launch {
                try { store.followedPlayers.collect { followers ->
                    _state.update { it.copy(followers = followers) }; search(_state.value.filter)
                } } catch (error: Exception) { if (error is CancellationException) throw error
                    _state.update { it.copy(message = "Following could not be loaded; retained choices were not deleted.") }
                }
            }
            scope.launch {
                try { store.privateGames.collect { records -> privateGames = records; if (requested) refresh() } }
                catch (error: Exception) { if (error is CancellationException) throw error
                    _state.update { it.copy(message = "Private games could not be loaded; imports are retained.") }
                }
            }
        }
    }

    fun openLibrary() { requested = true; refresh() }
    fun sourcesChanged() { if (requested) refresh() }
    fun retry() { key = null; openLibrary() }

    private fun refresh() {
        libraryGeneration++; val generation = libraryGeneration
        libraryJob?.cancel()
        libraryJob = scope.launch {
            try {
                val packs = store?.activePacks().orEmpty().filter { it.manifest.source.kind == SourceKind.BROADCAST_GAMES }.sortedBy { it.manifest.packId }
                val records = privateGames
                val nextKey = packs.joinToString { it.manifest.packId + ":" + it.manifestSha256 } + "|" + records.joinToString { it.id }
                if (generation != libraryGeneration) return@launch
                if (nextKey == key && _state.value.library !is GameLibraryUiState.Error) return@launch
                _state.update { it.copy(library = GameLibraryUiState.Loading) }
                val library = withContext(Dispatchers.Default) {
                    val context = currentCoroutineContext()
                    val sources = packs.map { pack ->
                        context.ensureActive()
                        val dependency = pack.manifest.dependencies.single()
                        require(store?.retainedPack(dependency.packId)?.manifestSha256 == dependency.manifestSha256)
                        val names = requireNotNull(store).openings(dependency.packId).associate { it.id to it.name }
                        LibraryPack(pack, store.games(pack.manifest.packId), names)
                    }
                    GameLibrary.build(sources, records) { context.ensureActive() }
                }
                if (generation != libraryGeneration) return@launch
                key = nextKey
                _state.update { it.copy(library = if (library.scores.isEmpty()) GameLibraryUiState.Missing else GameLibraryUiState.Ready(library, library.scores)) }
                search(_state.value.filter)
            } catch (error: Exception) { if (error is CancellationException) throw error
                if (generation == libraryGeneration) _state.update { it.copy(library = GameLibraryUiState.Error("Game library could not be checked. Retry or inspect offline sources; content and saved choices remain retained.")) }
            }
        }
    }

    fun search(filter: GameLibraryFilter) {
        _state.update { it.copy(filter = filter) }
        val ready = _state.value.library as? GameLibraryUiState.Ready ?: return
        searchGeneration++; val generation = searchGeneration
        searchJob?.cancel()
        val followers = _state.value.followers.map { it.id }.toSet()
        _state.update { it.copy(library = ready.copy(searching = true)) }
        searchJob = scope.launch {
            val (matches, players) = withContext(Dispatchers.Default) {
                val context = currentCoroutineContext()
                val scores = ready.library.search(filter, followers) { context.ensureActive() }
                val words = filter.query.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
                scores to ready.library.players.filter { player ->
                    context.ensureActive()
                    words.all { word -> word in player.reference.names.joinToString(" ").lowercase() }
                }
            }
            if (generation == searchGeneration && (_state.value.library as? GameLibraryUiState.Ready)?.library === ready.library)
                _state.update { it.copy(library = ready.copy(scores = matches, searching = false, players = players)) }
        }
    }

    fun follow(id: String, add: Boolean) {
        val player = (_state.value.library as? GameLibraryUiState.Ready)?.library?.players?.firstOrNull { it.reference.id == id }?.reference
            ?: _state.value.followers.firstOrNull { it.id == id } ?: return
        scope.launch {
            try {
                if (store != null) { if (add) store.followPlayer(player) else store.unfollowPlayer(id) }
                else { _state.update { it.copy(followers = it.followers.filterNot { old -> old.id == id } + if (add) listOf(player) else emptyList()) }; search(_state.value.filter) }
            } catch (error: Exception) { if (error is CancellationException) throw error
                _state.update { it.copy(message = "Following could not be saved. Existing games and choices were not deleted.") }
            }
        }
    }

    fun importPgn(text: String) {
        if (_state.value.importing) return
        _state.update { it.copy(importing = true, message = null) }
        scope.launch {
            try {
                val record = if (store != null) store.importPrivateGame(text) else withContext(Dispatchers.Default) { PrivateGameRecord.import(text) }
                if (store == null) privateGames = privateGames.filterNot { it.id == record.id } + record
                _state.update { it.copy(importing = false, message = "Private original game imported locally. Comments/identities are user-supplied, not verified; it is excluded from public source statistics.") }
                requested = true; key = null; refresh()
            } catch (error: Exception) { if (error is CancellationException) throw error
                _state.update { it.copy(importing = false, message = "Import rejected. Supply one legal standard PGN ≤256 KiB /4096 mainline half-moves, not an archive. Existing games were unchanged.") }
            }
        }
    }

    fun start(id: String, side: PieceColor) {
        val score = (_state.value.library as? GameLibraryUiState.Ready)?.library?.score(id) ?: return
        val reference = when (score) {
            is LibraryScore.Private -> GameStudyReference(score.game.id, privateImport = true)
            is LibraryScore.Broadcast -> score.origins.first().let { GameStudyReference(score.game.id, it.manifest.packId, it.manifestSha256) }
        }
        onStart(); pause(); studyGeneration++; val generation = studyGeneration
        studyJob?.cancel()
        _state.update { it.copy(study = null, studyLoading = true, active = true, message = null) }
        studyJob = scope.launch {
            try {
                val session = withContext(Dispatchers.Default) { prepare(reference, score, side) }
                if (generation == studyGeneration) { _state.update { it.copy(studyLoading = false) }; updateStudy(session) }
            } catch (error: Exception) { if (error is CancellationException) throw error
                if (generation == studyGeneration) _state.update { it.copy(studyLoading = false, message = "The exact original score could not be prepared. Saved data was retained.") }
            }
        }
    }

    private fun prepare(ref: GameStudyReference, score: LibraryScore, side: PieceColor): GameStudyUiState {
        ref.validate()
        val graph = when (score) {
            is LibraryScore.Broadcast -> score.game.toLessonGraph()
            is LibraryScore.Private -> LessonGraph.fromPgn(score.game.checkedGame(), ref.lessonId)
        }
        require(graph.paths.getValue(graph.originalPathId).kind == LessonPathKind.ORIGINAL_GAME)
        return GameStudyUiState(ref, score, graph.start(side))
    }

    suspend fun restore(bookmark: LessonBookmark) {
        val ref = requireNotNull(bookmark.gameReference).also { it.validate() }
        studyGeneration++; val generation = studyGeneration
        pause(); studyJob?.cancel()
        _state.update { it.copy(study = null, studyLoading = true, active = true, message = null) }
        try {
            val session = withContext(Dispatchers.Default) {
                val repository = requireNotNull(store)
                val score = if (ref.privateImport) LibraryScore.Private(requireNotNull(repository.privateGame(ref.recordId))) else {
                    val packId = requireNotNull(ref.packId)
                    val pack = requireNotNull(repository.retainedPack(packId))
                    require(pack.manifestSha256 == ref.manifestSha256)
                    val game = requireNotNull(repository.gameInPack(packId, ref.recordId))
                    val dependency = pack.manifest.dependencies.single()
                    require(repository.retainedPack(dependency.packId)?.manifestSha256 == dependency.manifestSha256)
                    val names = repository.openings(dependency.packId).associate { it.id to it.name }
                    LibraryScore.Broadcast(game, listOf(pack), game.openingIds.map { requireNotNull(names[it]) })
                }
                val prepared = prepare(ref, score, bookmark.replay.playerSide)
                require(bookmark.lessonId == ref.lessonId && bookmark.contentVersion == prepared.fingerprint && bookmark.mode == "STUDY")
                require(bookmark.replay.rootPathId == prepared.replay.graph.originalPathId && bookmark.replay.branches.isEmpty())
                prepared.copy(replay = LessonReplay.restore(prepared.replay.graph, bookmark.replay), playbackDelayMillis = bookmark.speedMillis.coerceIn(600, 2400))
            }
            if (generation == studyGeneration) {
                _state.update { it.copy(study = session, studyLoading = false) } // Restore never overwrites a source or learner row.
                requestTeaching()
            }
        } catch (error: Exception) { if (error is CancellationException) throw error
            if (generation == studyGeneration) _state.update { it.copy(studyLoading = false, message = "Saved original game or exact source version is unavailable. The bookmark is retained; no newer score was substituted.") }
        }
    }

    fun savedBookmark(): LessonBookmark? = if (saved.get<Boolean>("active_game") == true)
        saved.get<String>("game_session")?.let { runCatching { json.decodeFromString<LessonBookmark>(it) }.getOrNull() } else null

    fun jump(ply: Int) {
        val study = _state.value.study ?: return
        if (ply !in 0..study.replay.moves.size) return
        pause(); updateStudy(study.copy(replay = study.replay.jump(ply), isPlaying = false))
    }
    fun flip() { _state.value.study?.let { pausePlayback(); updateStudy(it.copy(replay = it.replay.withSide(it.replay.playerSide.opposite), isPlaying = false)) } }
    fun speed() { _state.value.study?.let { updateStudy(it.copy(playbackDelayMillis = when (it.playbackDelayMillis) { 1200L -> 600; 600L -> 2400; else -> 1200 })) } }
    fun togglePlayback() {
        val initial = _state.value.study ?: return
        if ((_state.value.engineAnalysis as? EngineAnalysisUiState.Ready)?.previewIndex != null) return
        if (initial.isPlaying) { pausePlayback(); return }
        if (initial.replay.atEnd) return
        stopAnalysis()
        _state.update { it.copy(study = initial.copy(isPlaying = true)) }
        playback = scope.launch {
            while (isActive) {
                val before = _state.value.study ?: break
                if (!before.isPlaying || before.replay.atEnd) break
                delay(before.playbackDelayMillis)
                val current = _state.value.study ?: break
                if (!current.isPlaying) break
                updateStudy(current.copy(replay = current.replay.next(), isPlaying = !current.replay.next().atEnd))
            }
        }
    }
    private fun pausePlayback() { playback?.cancel(); playback = null; _state.update { it.copy(study = it.study?.copy(isPlaying = false)) } }
    fun pause() { pausePlayback(); stopAnalysis(); cancelTeaching() }
    fun leaveReplay() {
        pause(); studyGeneration++; studyJob?.cancel()
        _state.update { it.copy(studyLoading = false) }
    }
    fun leaveForOpening() { pause(); studyGeneration++; studyJob?.cancel(); saved["active_game"] = false; _state.update { it.copy(active = false, studyLoading = false) } }

    private fun updateStudy(study: GameStudyUiState) {
        _state.update { it.copy(study = study, active = true) }
        requestTeaching()
        val bookmark = LessonBookmark(study.reference.lessonId, study.fingerprint, study.replay.snapshot(), "STUDY", studied = true,
            speedMillis = study.playbackDelayMillis, gameReference = study.reference)
        saved["active_game"] = true; saved["game_session"] = json.encodeToString(bookmark)
        onBookmark(bookmark)
    }

    private fun cancelTeaching() {
        teachingGeneration++; teachingJob?.cancel(); teachingJob = null; teachingKey = null
        _state.update { it.copy(moveTeaching = OriginalMoveTeachingUiState.Idle) }
    }

    fun requestTeaching() {
        val study = _state.value.study?.takeIf { _state.value.active && !_state.value.studyLoading } ?: return
        if (study.replay.atStart) { cancelTeaching(); return }
        val key = "${OriginalGameCoach.VERSION}:${study.fingerprint}:${study.replay.ply}"
        if (key == teachingKey && _state.value.moveTeaching !is OriginalMoveTeachingUiState.Failed) return
        teachingGeneration++; val generation = teachingGeneration
        teachingJob?.cancel(); teachingKey = key
        val cached = teachingCache.remove(key)
        if (cached != null) {
            teachingCache[key] = cached
            _state.update { it.copy(moveTeaching = OriginalMoveTeachingUiState.Ready(cached, true)) }; return
        }
        _state.update { it.copy(moveTeaching = OriginalMoveTeachingUiState.Loading) }
        teachingJob = scope.launch {
            try {
                val facts = withContext(Dispatchers.Default) {
                    val context = currentCoroutineContext()
                    OriginalGameCoach.explain(study.replay.graph, study.replay.ply) { context.ensureActive() }
                }
                if (generation == teachingGeneration && teachingKey == key) {
                    teachingCache[key] = facts
                    while (teachingCache.size > 128) teachingCache.remove(teachingCache.keys.first())
                    _state.update { it.copy(moveTeaching = OriginalMoveTeachingUiState.Ready(facts, false)) }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (generation == teachingGeneration) _state.update { it.copy(moveTeaching = OriginalMoveTeachingUiState.Failed) }
            }
        }
    }

    fun analyze() {
        val study = _state.value.study?.takeIf { _state.value.active && !_state.value.studyLoading } ?: return
        pausePlayback(); stopAnalysis()
        if (study.replay.ply > AnalysisPosition.MAX_HISTORY_PLIES) {
            _state.update { it.copy(engineAnalysis = EngineAnalysisUiState.Error("Replay and move teaching remain available. Engine analysis supports at most 512 original half-moves of history; earlier repetitions were not discarded to invent an isolated-FEN recommendation.")) }; return
        }
        val engine = engine
        if (engine == null) {
            _state.update { it.copy(engineAnalysis = EngineAnalysisUiState.Error("Offline engine is unavailable in this install.")) }; return
        }
        val generation = analysisGeneration
        _state.update { it.copy(engineAnalysis = EngineAnalysisUiState.Loading) }
        analysisJob = scope.launch {
            try {
                val prepared = withContext(Dispatchers.Default) {
                    val context = currentCoroutineContext()
                    val position = OriginalGameCoach.analysisPosition(study.replay)
                    val original = study.replay.nextMove?.move?.uci
                    val budget = AnalysisBudget()
                    val result = engine.analyze(position, original, budget)
                    require(result.position == position && result.budget == budget && result.rootSide == study.replay.position.sideToMove)
                    val root = position.board()
                    val terminal = root.status().takeUnless { it in listOf(com.openinglab.shared.chess.PositionStatus.ONGOING, com.openinglab.shared.chess.PositionStatus.CHECK) }
                    require(result.terminal == terminal)
                    if (terminal != null) require(result.alternatives == null && result.original == null)
                    else {
                        val candidates = requireNotNull(result.alternatives)
                        require(candidates.requestedCandidates == minOf(budget.multiPv, root.legalMoves().size))
                        require(candidates.lines.size in 1..candidates.requestedCandidates)
                    }
                    result.original?.let { compared ->
                        require(original != null && compared.requestedCandidates == 1 && compared.lines.size == 1 && compared.lines.all { it.uci.firstOrNull() == original })
                    }
                    if (original != null && result.terminal == null) require(result.original != null)
                    listOfNotNull(result.alternatives, result.original).forEach { search ->
                        require(search.lines.first().rank == 1 && search.lines.first().uci.firstOrNull() == search.bestMove)
                        require(search.lines.map { it.rank }.distinct().size == search.lines.size && search.lines.all { it.rank in 1..search.requestedCandidates })
                        require(search.lines.map { it.uci.firstOrNull() }.distinct().size == search.lines.size)
                        if (search.completeCandidateSet) require(search.lines.map { it.rank }.toSet() == (1..search.requestedCandidates).toSet())
                    }
                    val continuations = (result.alternatives?.lines.orEmpty() + result.original?.lines.orEmpty()).map { line ->
                        GroundedContinuation.build(root, line.uci, line.san) { context.ensureActive() }
                    }
                    EngineAnalysisUiState.Ready(result, continuations.map { it.positions }, previewExplanations = continuations.map { it.explanations })
                }
                if (generation == analysisGeneration && _state.value.active && _state.value.study?.reference == study.reference &&
                    _state.value.study?.replay?.ply == study.replay.ply) _state.update { it.copy(engineAnalysis = prepared) }
            } catch (cancelled: CancellationException) {
                if (!currentCoroutineContext().isActive) throw cancelled
                if (generation == analysisGeneration) _state.update { it.copy(engineAnalysis = EngineAnalysisUiState.Error("Analysis timed out. Retry deliberately; no partial output was promoted to a recommendation.")) }
            } catch (_: Exception) {
                if (generation == analysisGeneration) _state.update { it.copy(engineAnalysis = EngineAnalysisUiState.Error("Original-game analysis could not complete. Invalid/stale output is not shown as a recommendation; the source score stays unchanged.")) }
            }
        }
    }

    fun stopAnalysis() {
        analysisGeneration++; analysisJob?.cancel(); analysisJob = null
        _state.update { it.copy(engineAnalysis = EngineAnalysisUiState.Idle) }
    }
    fun exploreAnalysis(index: Int) {
        val ready = _state.value.engineAnalysis as? EngineAnalysisUiState.Ready ?: return
        if (index !in ready.lines.indices) return
        pausePlayback()
        _state.update { it.copy(engineAnalysis = ready.copy(previewIndex = index, previewPly = 1)) }
    }
    fun jumpAnalysis(ply: Int) {
        val ready = _state.value.engineAnalysis as? EngineAnalysisUiState.Ready ?: return
        val index = ready.previewIndex ?: return
        if (ply in ready.previewPositions[index].indices) _state.update { it.copy(engineAnalysis = ready.copy(previewPly = ply)) }
    }
    fun returnAnalysis() {
        val ready = _state.value.engineAnalysis as? EngineAnalysisUiState.Ready ?: return
        _state.update { it.copy(engineAnalysis = ready.copy(previewIndex = null, previewPly = 0)) }
    }
}
