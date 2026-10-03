// SPDX-License-Identifier: Apache-2.0
package com.openinglab.app.ui

import com.openinglab.shared.games.*
import com.openinglab.shared.lesson.LessonReplay

sealed interface OriginalMoveTeachingUiState {
    data object Idle : OriginalMoveTeachingUiState
    data object Loading : OriginalMoveTeachingUiState
    data class Ready(val facts: OriginalMoveTeaching, val cached: Boolean) : OriginalMoveTeachingUiState
    data object Failed : OriginalMoveTeachingUiState
}

sealed interface GameLibraryUiState {
    data object Loading : GameLibraryUiState
    data object Missing : GameLibraryUiState
    data class Error(val message: String) : GameLibraryUiState
    data class Ready(val library: GameLibrary, val scores: List<LibraryScore>, val searching: Boolean = false,
        val players: List<LibraryPlayer> = library.players) : GameLibraryUiState
}

data class GameStudyUiState(
    val reference: GameStudyReference,
    val score: LibraryScore,
    val replay: LessonReplay,
    val isPlaying: Boolean = false,
    val playbackDelayMillis: Long = 1200,
    val fingerprint: String = com.openinglab.shared.storage.contentSha256(score.canonicalPgn.encodeToByteArray()),
) {
}

data class GameLearningUiState(
    val library: GameLibraryUiState = GameLibraryUiState.Missing,
    val filter: GameLibraryFilter = GameLibraryFilter(),
    val followers: List<FollowedPlayer> = emptyList(),
    val study: GameStudyUiState? = null,
    val studyLoading: Boolean = false,
    val active: Boolean = false,
    val importing: Boolean = false,
    val message: String? = null,
    val moveTeaching: OriginalMoveTeachingUiState = OriginalMoveTeachingUiState.Idle,
    val engineAnalysis: EngineAnalysisUiState = EngineAnalysisUiState.Idle,
)
