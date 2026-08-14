package com.a02.draw.feature.home.screen.learnleveldetail

import com.a02.draw.core.ui.base.UiEffect
import com.a02.draw.core.ui.base.UiState
import com.a02.draw.domain.model.DrawingLesson

data class LearnLevelDetailUiState(
    val lesson: DrawingLesson? = null,
    val completedSteps: Int = 0,
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
) : UiState

sealed interface LearnLevelDetailAction {
    data object Back : LearnLevelDetailAction
    data object Retry : LearnLevelDetailAction
    data object OpenTutorial : LearnLevelDetailAction
}

sealed interface LearnLevelDetailEffect : UiEffect {
    data object NavigateBack : LearnLevelDetailEffect
    data object NavigateTutorial : LearnLevelDetailEffect
}
