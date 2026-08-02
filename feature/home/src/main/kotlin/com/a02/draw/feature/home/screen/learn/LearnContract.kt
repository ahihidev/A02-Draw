package com.a02.draw.feature.home.screen.learn

import com.a02.draw.core.ui.base.UiEffect
import com.a02.draw.core.ui.base.UiState
import com.a02.draw.domain.model.LessonCategory
import com.a02.draw.feature.home.common.model.BottomDestination

data class LearnUiState(
    val categories: List<LessonCategory> = emptyList(),
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
) : UiState

sealed interface LearnAction {
    data object Retry : LearnAction
    data object OpenSearch : LearnAction
    data class OpenCategory(val categoryId: String) : LearnAction
    data class OpenBottom(val destination: BottomDestination) : LearnAction
}

sealed interface LearnEffect : UiEffect {
    data object NavigateSearch : LearnEffect
    data class NavigateCategory(val categoryId: String) : LearnEffect
    data class NavigateBottom(val destination: BottomDestination) : LearnEffect
}
