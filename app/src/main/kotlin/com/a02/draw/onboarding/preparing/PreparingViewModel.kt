package com.a02.draw.onboarding.preparing

import androidx.lifecycle.viewModelScope
import com.a02.draw.R
import com.a02.draw.core.common.result.AppResult
import com.a02.draw.core.ui.base.BaseViewModel
import com.a02.draw.domain.usecase.UpdateAppPreferencesUseCase
import com.a02.draw.onboarding.common.session.OnboardingSessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
            delay(PERSONALIZATION_DELAY_MILLIS)
            if (updatePreferences.setOnboardingCompleted(true) is AppResult.Failure) {
                completionJob = null
                sendEffect(PreparingEffect.ShowMessage(R.string.generic_error))
                return@launch
            }
            sendEffect(PreparingEffect.OpenMain)
        }
    }

    private companion object {
        const val PERSONALIZATION_DELAY_MILLIS = 1_500L
    }
}
