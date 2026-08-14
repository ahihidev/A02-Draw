package com.a02.draw.onboarding.common.session

import com.a02.draw.domain.model.DrawingTopic
import kotlinx.coroutines.flow.StateFlow

data class OnboardingSession(
    val topics: List<DrawingTopic> = emptyList(),
    val selectedTopicIds: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val hasError: Boolean = false,
) {
    val selectedTopic: DrawingTopic?
        get() = topics.firstOrNull { it.id in selectedTopicIds }
}

interface OnboardingSessionStore {
    val state: StateFlow<OnboardingSession>
    fun setLoading()
    fun setTopics(topics: List<DrawingTopic>)
    fun setError()
    fun toggleTopic(topicId: String)
}
