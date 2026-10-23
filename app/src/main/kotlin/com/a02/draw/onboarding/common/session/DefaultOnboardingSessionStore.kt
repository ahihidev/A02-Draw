package com.a02.draw.onboarding.common.session

import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@ActivityRetainedScoped
class DefaultOnboardingSessionStore @Inject constructor() : OnboardingSessionStore {
    private val mutableState = MutableStateFlow(OnboardingSession())
    override val state: StateFlow<OnboardingSession> = mutableState.asStateFlow()

    override fun setLoading() {
        mutableState.value = mutableState.value.copy(isLoading = true, hasError = false)
    }

    override fun setTopics(topics: List<com.a02.draw.domain.model.DrawingTopic>) {
        mutableState.value = mutableState.value.copy(
            topics = topics,
            isLoading = false,
            hasError = false,
        )
    }

    override fun setError() {
        mutableState.value = mutableState.value.copy(isLoading = false, hasError = true)
    }

    override fun toggleTopic(topicId: String) {
        val current = mutableState.value
        if (current.topics.none { it.id == topicId }) return
        val next = current.selectedTopicIds.toMutableSet()
        if (!next.add(topicId)) next.remove(topicId)
        while (next.size > MAX_SELECTED_TOPICS) next.remove(next.first())
        mutableState.value = current.copy(selectedTopicIds = next)
    }

    private companion object {
        const val MAX_SELECTED_TOPICS = 3
    }
}
