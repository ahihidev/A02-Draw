package com.a02.draw.onboarding.topics

import com.a02.draw.core.ui.base.UiEffect
import com.a02.draw.core.ui.base.UiState
import com.a02.draw.domain.model.DrawingTopic

data class TopicsUiState(
    val topics: List<DrawingTopic> = emptyList(),
    val selectedTopicIds: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val hasError: Boolean = false,
) : UiState

sealed interface TopicsAction {
    data class ToggleTopic(val topicId: String) : TopicsAction
    data object Continue : TopicsAction
    data object Retry : TopicsAction
}

sealed interface TopicsEffect : UiEffect {
    data object NavigateNext : TopicsEffect
}
