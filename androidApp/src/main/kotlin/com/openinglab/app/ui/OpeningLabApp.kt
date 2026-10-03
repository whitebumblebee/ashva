package com.openinglab.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.openinglab.app.ui.components.AppBottomBar
import com.openinglab.app.ui.screens.ExploreScreen
import com.openinglab.app.ui.screens.HomeScreen
import com.openinglab.app.ui.screens.IdentifierScreen
import com.openinglab.app.ui.screens.OpeningDetailScreen
import com.openinglab.app.ui.screens.ProfileScreen
import com.openinglab.app.ui.screens.ReviewScreen
import com.openinglab.app.ui.screens.TrainerScreen
import com.openinglab.app.ui.screens.OfflineLibraryScreen
import com.openinglab.app.ui.screens.RepertoireScreen
import com.openinglab.app.ui.screens.MyRepertoiresScreen
import com.openinglab.app.ui.screens.GameLibraryScreen
import com.openinglab.app.ui.screens.GameReplayScreen
import com.openinglab.app.ui.theme.Ink
import com.openinglab.app.ui.theme.Cream
import com.openinglab.shared.model.PieceColor
import kotlinx.serialization.Serializable

@Serializable
private data object MainRoute : NavKey

@Serializable
private data class OpeningRoute(val openingId: String) : NavKey

@Serializable
private data object TrainerRoute : NavKey

@Serializable
private data object IdentifierRoute : NavKey

@Serializable
private data object OfflineLibraryRoute : NavKey

@Serializable
private data object MyRepertoiresRoute : NavKey
@Serializable
private data object GameLibraryRoute : NavKey
@Serializable
private data object GameReplayRoute : NavKey
@Serializable
private data class RepertoireRoute(val openingId: String, val side: PieceColor, val pathId: String? = null, val ply: Int = 0, val forceCursor: Boolean = false) : NavKey

@Composable
fun OpeningLabApp(viewModel: AppViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val games by viewModel.gameLibrary.state.collectAsStateWithLifecycle()
    val recall by viewModel.recall.state.collectAsStateWithLifecycle()
    val backStack = rememberNavBackStack(MainRoute)
    val popBack: () -> Unit = {
        if (games.active) viewModel.gameLibrary.leaveReplay() else viewModel.pauseTrainer()
        if (backStack.size > 1) backStack.removeLastOrNull()
    }

    NavDisplay(
        backStack = backStack,
        modifier = Modifier.fillMaxSize().background(Ink),
        onBack = popBack,
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
        entryProvider = entryProvider {
            entry<MainRoute> {
                Scaffold(
                    containerColor = Ink,
                    bottomBar = { AppBottomBar(state.selectedTab, viewModel::selectTab) },
                ) { innerPadding ->
                    when (state.selectedTab) {
                        MainTab.LEARN -> HomeScreen(
                            openings = viewModel.learningOpenings(),
                            onOpeningClick = { backStack.add(OpeningRoute(it)) },
                            onExploreAll = { viewModel.selectTab(MainTab.EXPLORE) },
                            onContinue = { if (games.active && games.study != null) backStack.add(GameReplayRoute)
                                else if (state.trainer == null) backStack.add(OpeningRoute(viewModel.primaryOpeningId("ruy-lopez"))) else {
                                viewModel.resumeTrainer(); backStack.add(TrainerRoute)
                            } },
                            onOfflineLibrary = { backStack.add(OfflineLibraryRoute) },
                            onRepertoires = { backStack.add(MyRepertoiresRoute) },
                            onGames = { viewModel.gameLibrary.openLibrary(); backStack.add(GameLibraryRoute) },
                            gameResume = games.study?.takeIf { games.active },
                            recall = recall, onReview = { viewModel.selectTab(MainTab.REVIEW) },
                            resume = state.trainer,
                            persistenceStatus = state.persistenceStatus,
                            catalogLoading = state.catalogLoading,
                            catalogError = state.catalogError ?: state.packError,
                            modifier = Modifier.fillMaxSize().padding(innerPadding),
                        )
                        MainTab.EXPLORE -> {
                            val filtered = viewModel.filteredOpenings()
                            ExploreScreen(
                                openings = filtered,
                                query = state.searchQuery,
                                selectedDifficulty = state.selectedDifficulty,
                                onQueryChange = viewModel::updateSearch,
                                onDifficultyChange = viewModel::selectDifficulty,
                                onOpeningClick = { backStack.add(OpeningRoute(it)) },
                                onIdentify = { backStack.add(IdentifierRoute) },
                                sourcedOpenings = state.sourcedOpenings,
                                catalogLoading = state.catalogLoading,
                                catalogError = state.catalogError,
                                onOfflineLibrary = { backStack.add(OfflineLibraryRoute) },
                                modifier = Modifier.fillMaxSize().padding(innerPadding),
                            )
                        }
                        MainTab.REVIEW -> ReviewScreen(
                            state = recall,
                            onRetryRecall = viewModel.recall::retryFailedWrites,
                            onStartReview = { scopeId ->
                                viewModel.startRecallReview(scopeId)
                                backStack.add(TrainerRoute)
                            },
                            modifier = Modifier.fillMaxSize().padding(innerPadding),
                        )
                        MainTab.PROFILE -> ProfileScreen(recall, Modifier.fillMaxSize().padding(innerPadding))
                    }
                }
            }
            entry<OpeningRoute> { route ->
                val opening = runCatching { viewModel.getOpening(route.openingId) }.getOrNull()
                if (opening == null) Column(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp)) {
                    Text(if (state.catalogLoading) "Loading installed opening catalog…" else
                        "This source route is unavailable. Check the offline library; saved progress has been retained.", color = Cream)
                    if (state.catalogLoading) CircularProgressIndicator()
                    TextButton(popBack) { Text("Back to openings") }
                } else
                OpeningDetailScreen(
                    opening = opening,
                    onBack = popBack,
                    onStart = { side, variationId ->
                        viewModel.startTrainer(route.openingId, side, variationId)
                        backStack.add(TrainerRoute)
                    },
                    modifier = Modifier.fillMaxSize().statusBarsPadding(),
                    onExploreSources = {
                        viewModel.pauseTrainer()
                        viewModel.updateSearch(opening.name)
                        viewModel.selectDifficulty("Sourced")
                        viewModel.selectTab(MainTab.EXPLORE)
                        while (backStack.size > 1) backStack.removeLastOrNull()
                    },
                )
            }
            entry<TrainerRoute> {
                if (state.trainer == null) Column(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp)) {
                    Text(if (state.lessonLoading) "Preparing the sourced lesson…" else state.lessonError ?:
                        "This saved lesson is no longer available. Open an opening to start again.", color = Cream)
                    if (state.lessonLoading) CircularProgressIndicator()
                    TextButton(popBack) { Text("Back to openings") }
                }
                state.trainer?.let { trainer ->
                    TrainerScreen(
                        state = trainer,
                        onBack = popBack,
                        onSquareTap = viewModel::trainerTap,
                        onHint = viewModel::showHint,
                        onRestart = viewModel::restartTrainer,
                        onStudy = viewModel::studyTrainer,
                        onPractice = viewModel::practiceTrainer,
                        onJump = viewModel::jumpTrainer,
                        onPlayPause = viewModel::toggleTrainerPlayback,
                        onSpeed = viewModel::changeTrainerSpeed,
                        onFlip = viewModel::flipTrainerSide,
                        onStay = viewModel::stayOnTrainerLine,
                        onSwitch = { viewModel.switchTrainerBranch(it.pathId, it.targetPly) },
                        onReturn = viewModel::returnTrainerBranch,
                        onPromotion = viewModel::chooseTrainerPromotion,
                        onCancelPromotion = viewModel::cancelTrainerPromotion,
                        onPause = viewModel::pauseTrainer,
                        onResume = viewModel::resumeTrainer,
                        onBuildRepertoire = { backStack.add(RepertoireRoute(trainer.opening.id, trainer.playerSide, trainer.replay.pathId, trainer.ply)) },
                        modifier = Modifier.fillMaxSize().statusBarsPadding(),
                        analysis = state.engineAnalysis,
                        onAnalyze = viewModel::analyzeTrainer,
                        onStopAnalysis = viewModel::stopEngineAnalysis,
                        onExploreAnalysis = viewModel::exploreEngineLine,
                        onJumpAnalysis = viewModel::jumpEnginePreview,
                        onReturnAnalysis = viewModel::returnFromEnginePreview,
                        onSetPrevious = { viewModel.moveSetPractice(-1) },
                        onSetNext = { viewModel.moveSetPractice(1) },
                        preparing = state.lessonLoading, loadError = state.lessonError,
                        recallError = recall.error, onNextReview = viewModel::nextRecallReview,
                        recallRetryAvailable = recall.retryableWrites > 0 && recall.pendingWrites == 0,
                        onRetryRecall = viewModel.recall::retryFailedWrites,
                    )
                }
            }
            entry<GameLibraryRoute> {
                LaunchedEffect(Unit) { viewModel.gameLibrary.openLibrary() }
                GameLibraryScreen(games, popBack, { backStack.add(OfflineLibraryRoute) }, viewModel.gameLibrary::search,
                    viewModel.gameLibrary::follow, viewModel.gameLibrary::importPgn,
                    { id, side -> viewModel.gameLibrary.start(id, side); backStack.add(GameReplayRoute) }, viewModel.gameLibrary::retry,
                    Modifier.fillMaxSize().statusBarsPadding())
            }
            entry<GameReplayRoute> {
                DisposableEffect(Unit) { onDispose(viewModel.gameLibrary::leaveReplay) }
                val study = games.study
                LaunchedEffect(study?.reference, study?.replay?.ply) { viewModel.gameLibrary.requestTeaching() }
                if (games.studyLoading || study == null) Column(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp)) {
                    Text(if (games.studyLoading) "Preparing the exact original game…" else games.message ?: "Original game is unavailable; saved data is retained.", color = Cream,
                        modifier = Modifier.testTag("game-study-status"))
                    if (games.studyLoading) CircularProgressIndicator()
                    TextButton(popBack) { Text("Back") }
                } else GameReplayScreen(study, popBack, viewModel.gameLibrary::jump, viewModel.gameLibrary::flip,
                    viewModel.gameLibrary::togglePlayback, viewModel.gameLibrary::speed, viewModel.gameLibrary::pause,
                    Modifier.fillMaxSize().statusBarsPadding(),
                    moveTeaching = games.moveTeaching, analysis = games.engineAnalysis,
                    onAnalyze = viewModel.gameLibrary::analyze, onStopAnalysis = viewModel.gameLibrary::stopAnalysis,
                    onExplore = viewModel.gameLibrary::exploreAnalysis, onJumpAnalysis = viewModel.gameLibrary::jumpAnalysis,
                    onReturnAnalysis = viewModel.gameLibrary::returnAnalysis, onRequestTeaching = viewModel.gameLibrary::requestTeaching)
            }
            entry<IdentifierRoute> {
                IdentifierScreen(
                    state = state.identifier,
                    examples = viewModel.openings,
                    onBack = popBack,
                    onSquareTap = viewModel::identifierTap,
                    onUndo = viewModel::undoIdentifier,
                    onReset = viewModel::resetIdentifier,
                    onExample = viewModel::loadIdentifierExample,
                    onOpenMatch = { backStack.add(OpeningRoute(viewModel.primaryOpeningId(it))) },
                    onPromotion = viewModel::chooseIdentifierPromotion,
                    onCancelPromotion = viewModel::cancelIdentifierPromotion,
                    onImport = viewModel::importIdentifier,
                    modifier = Modifier.fillMaxSize().statusBarsPadding(),
                )
            }
            entry<OfflineLibraryRoute> {
                OfflineLibraryScreen(state, viewModel::installPack, popBack, Modifier.fillMaxSize().statusBarsPadding())
            }
            entry<MyRepertoiresRoute> {
                LaunchedEffect(state.repertoirePolicies, state.repertoireError, state.sourcedOpenings, state.catalogLoading, state.catalogError) { viewModel.loadRepertoireOverview() }
                DisposableEffect(Unit) { onDispose(viewModel::cancelRepertoireOverview) }
                MyRepertoiresScreen(state.repertoirePolicies, state.repertoireError, popBack,
                    onEdit = { backStack.add(RepertoireRoute(it.lessonId, it.side)) },
                    onPractice = { viewModel.practiceRepertoire(it); backStack.add(TrainerRoute) },
                    modifier = Modifier.fillMaxSize().statusBarsPadding(),
                    overview = state.repertoireOverview,
                    onEditConflict = { policy, origin -> backStack.add(RepertoireRoute(policy.lessonId, policy.side, origin.pathId, origin.ply, forceCursor = true)) },
                    onRetry = viewModel::loadRepertoireOverview,
                    sets = state.repertoireSets, checkedSet = state.checkedSet, setLoading = state.setLoading, setError = state.setError,
                    onSaveSet = viewModel::saveRepertoireSet, onCheckSet = viewModel::checkRepertoireSet,
                    onPracticeSet = { viewModel.practiceRepertoireSet(it); backStack.add(TrainerRoute) })
            }
            entry<RepertoireRoute> { route ->
                LaunchedEffect(route) { viewModel.openRepertoire(route.openingId, route.side, route.pathId, route.ply, route.forceCursor) }
                val editor = state.repertoireEditor?.takeIf { !state.repertoireLoading && it.policy.lessonId == route.openingId && it.policy.side == route.side }
                if (editor == null) Column(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp)) {
                    Text(state.repertoireError ?: "Loading repertoire choices…", color = Cream)
                    if (state.repertoireLoading) CircularProgressIndicator()
                    TextButton(popBack) { Text("Back") }
                } else RepertoireScreen(editor, popBack, viewModel::moveRepertoireCursor, viewModel::chooseRepertoireMove,
                    viewModel::includeAllRepertoireReplies, viewModel::adoptRepertoireRoute,
                    onPractice = { viewModel.practiceRepertoire(editor.policy, it); backStack.add(TrainerRoute) },
                    observations = state.observedReplies,
                    onSources = { backStack.add(OfflineLibraryRoute) },
                    onRetryObservations = viewModel::retryObservedReplies,
                    modifier = Modifier.fillMaxSize().statusBarsPadding())
            }
        },
    )
}
