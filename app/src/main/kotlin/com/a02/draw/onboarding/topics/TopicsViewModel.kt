package com.a02.draw.onboarding.topics

import androidx.lifecycle.viewModelScope
import com.a02.draw.core.common.result.AppResult
import com.a02.draw.core.ui.base.BaseViewModel
import com.a02.draw.domain.usecase.GetArCatalogUseCase
import com.a02.draw.onboarding.common.session.OnboardingSessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TopicsViewModel @Inject constructor(
    private val getCatalog: GetArCatalogUseCase,
    private val sessionStore: OnboardingSessionStore,
) : BaseViewModel<TopicsUiState, TopicsEffect>(sessionStore.state.value.toUiState()) {
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            sessionStore.state.collect { session -> updateState { session.toUiState() } }
        }
        if (sessionStore.state.value.topics.isEmpty()) load()
    }

    fun onAction(action: TopicsAction) {
        when (action) {
            is TopicsAction.ToggleTopic -> sessionStore.toggleTopic(action.topicId)
            TopicsAction.Continue -> viewModelScope.launch {
                sendEffect(TopicsEffect.NavigateNext)
            }

            TopicsAction.Retry -> load(forceRefresh = true)
        }
    }

    private fun load(forceRefresh: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            sessionStore.setLoading()
            when (val result = getCatalog(forceRefresh)) {
                is AppResult.Success -> sessionStore.setTopics(result.data.topics)
                is AppResult.Failure -> sessionStore.setError()
            }
        }
    }
}

private fun com.a02.draw.onboarding.common.session.OnboardingSession.toUiState() = TopicsUiState(
    topics = topics,
    selectedTopicIds = selectedTopicIds,
    isLoading = isLoading,
    hasError = hasError,
)
