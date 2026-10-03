// SPDX-License-Identifier: Apache-2.0
package com.openinglab.app.ui

import com.openinglab.shared.analysis.EngineAnalysis
import com.openinglab.shared.chess.BoardPosition
import com.openinglab.shared.model.MoveStep

sealed interface EngineAnalysisUiState {
    data object Idle : EngineAnalysisUiState
    data object Loading : EngineAnalysisUiState
    data class Error(val message: String) : EngineAnalysisUiState
    data class Ready(
        val result: EngineAnalysis,
        val previewPositions: List<List<BoardPosition>>,
        val previewIndex: Int? = null,
        val previewPly: Int = 0,
        val previewExplanations: List<List<MoveStep>> = emptyList(),
    ) : EngineAnalysisUiState {
        val lines get() = result.alternatives?.lines.orEmpty() + result.original?.lines.orEmpty()
    }
}
