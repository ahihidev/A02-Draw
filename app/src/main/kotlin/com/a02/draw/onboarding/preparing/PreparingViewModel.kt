package com.a02.draw.onboarding.preparing

import androidx.lifecycle.viewModelScope
import com.a02.draw.R
import com.a02.draw.core.common.result.AppResult
import com.a02.draw.core.ui.base.BaseViewModel
import com.a02.draw.domain.usecase.UpdateAppPreferencesUseCase
import com.a02.draw.onboarding.common.session.OnboardingSessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PreparingViewModel @Inject constructor(
    private val updatePreferences: UpdateAppPreferencesUseCase,
    sessionStore: OnboardingSessionStore,
) : BaseViewModel<PreparingUiState, PreparingEffect>(
    PreparingUiState(sessionStore.state.value.selectedTopic),
) {
    private var completionJob: Job? = null

    init {
        viewModelScope.launch {
            sessionStore.state.collect { session ->
                updateState { copy(selectedTopic = session.selectedTopic) }
            }
        }
        start()
    }

    fun onAction(action: PreparingAction) {
        when (action) {
            PreparingAction.Start -> start()
        }
    }

    private fun start() {
        if (completionJob != null) return
        completionJob = viewModelScope.launch {
            updateState {
                copy(
                    favoritesProgress = 0,
                    referencesProgress = 0,
                    toolsProgress = 0,
                )
            }
            animateProgress { progress -> copy(favoritesProgress = progress) }
            animateProgress { progress -> copy(referencesProgress = progress) }
            animateProgress { progress -> copy(toolsProgress = progress) }
            if (updatePreferences.setOnboardingCompleted(true) is AppResult.Failure) {
                completionJob = null
                sendEffect(PreparingEffect.ShowMessage(R.string.generic_error))
                return@launch
            }
            sendEffect(PreparingEffect.OpenMain)
        }
    }

    private suspend fun animateProgress(
        updateProgress: PreparingUiState.(progress: Int) -> PreparingUiState,
    ) {
        repeat(MAX_PROGRESS) { index ->
            delay(PROGRESS_STEP_DELAY_MILLIS)
            updateState { updateProgress(index + 1) }
        }
    }

    private companion object {
        const val MAX_PROGRESS = 100
        const val PROGRESS_DURATION_MILLIS = 2_000L
        const val PROGRESS_STEP_DELAY_MILLIS = PROGRESS_DURATION_MILLIS / MAX_PROGRESS
    }
}
