package com.a02.draw.feature.home.screen.learncategorydetail

import com.a02.draw.core.ui.base.UiEffect
import com.a02.draw.core.ui.base.UiState
import com.a02.draw.domain.model.DrawingLesson

data class LearnCategoryDetailUiState(
    val title: String = "",
    val description: String = "",
    val lessons: List<DrawingLesson> = emptyList(),
    val completedSteps: Map<String, Int> = emptyMap(),
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
) : UiState

sealed interface LearnCategoryDetailAction {
    data object Back : LearnCategoryDetailAction
    data object Retry : LearnCategoryDetailAction
    data class OpenLesson(val lessonId: String) : LearnCategoryDetailAction
}

sealed interface LearnCategoryDetailEffect : UiEffect {
    data object NavigateBack : LearnCategoryDetailEffect
    data object NavigateTutorial : LearnCategoryDetailEffect
}
