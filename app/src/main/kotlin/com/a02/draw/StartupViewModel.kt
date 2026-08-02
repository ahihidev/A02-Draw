package com.a02.draw

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a02.draw.domain.usecase.ObserveAppPreferencesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class StartupDestination {
    ONBOARDING,
    MAIN,
}

@HiltViewModel
class StartupViewModel @Inject constructor(
    observeAppPreferences: ObserveAppPreferencesUseCase,
) : ViewModel() {
    val destination = observeAppPreferences()
        .map { preferences ->
            if (preferences.onboardingCompleted) {
                StartupDestination.MAIN
            } else {
                StartupDestination.ONBOARDING
            }
        }
        .catch { emit(StartupDestination.ONBOARDING) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null,
        )
}
