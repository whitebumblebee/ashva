package com.openinglab.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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

@Composable
fun OpeningLabApp(viewModel: AppViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val backStack = rememberNavBackStack(MainRoute)
    val popBack: () -> Unit = {
        viewModel.pauseTrainer()
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
                            openings = viewModel.openings,
                            onOpeningClick = { backStack.add(OpeningRoute(it)) },
                            onExploreAll = { viewModel.selectTab(MainTab.EXPLORE) },
                            onContinue = { if (state.trainer == null) backStack.add(OpeningRoute("ruy-lopez")) else {
                                viewModel.resumeTrainer(); backStack.add(TrainerRoute)
                            } },
                            onOfflineLibrary = { backStack.add(OfflineLibraryRoute) },
                            resume = state.trainer,
                            persistenceStatus = state.persistenceStatus,
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
                                modifier = Modifier.fillMaxSize().padding(innerPadding),
                            )
                        }
                        MainTab.REVIEW -> ReviewScreen(
                            onStartReview = {
                                viewModel.startTrainer("ruy-lopez", PieceColor.WHITE)
                                backStack.add(TrainerRoute)
                            },
                            modifier = Modifier.fillMaxSize().padding(innerPadding),
                        )
                        MainTab.PROFILE -> ProfileScreen(Modifier.fillMaxSize().padding(innerPadding))
                    }
                }
            }
            entry<OpeningRoute> { route ->
                OpeningDetailScreen(
                    opening = viewModel.getOpening(route.openingId),
                    onBack = popBack,
                    onStart = { side, variationId ->
                        viewModel.startTrainer(route.openingId, side, variationId)
                        backStack.add(TrainerRoute)
                    },
                    modifier = Modifier.fillMaxSize().statusBarsPadding(),
                )
            }
            entry<TrainerRoute> {
                if (state.trainer == null) Column(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp)) {
                    Text("This saved lesson is no longer available. Open an opening to start again.", color = Cream)
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
                        modifier = Modifier.fillMaxSize().statusBarsPadding(),
                    )
                }
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
                    onOpenMatch = { backStack.add(OpeningRoute(it)) },
                    onPromotion = viewModel::chooseIdentifierPromotion,
                    onCancelPromotion = viewModel::cancelIdentifierPromotion,
                    onImport = viewModel::importIdentifier,
                    modifier = Modifier.fillMaxSize().statusBarsPadding(),
                )
            }
            entry<OfflineLibraryRoute> {
                OfflineLibraryScreen(state, viewModel::installPack, popBack, Modifier.fillMaxSize().statusBarsPadding())
            }
        },
    )
}
