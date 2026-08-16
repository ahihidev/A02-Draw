package com.a02.draw

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a02.draw.core.ui.ads.PremiumEntitlementController
import com.a02.draw.domain.model.AppPreferences
import com.a02.draw.domain.usecase.ObserveAppPreferencesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class StartupDestination {
    LANGUAGE,
    ONBOARDING,
    MAIN,
}

@HiltViewModel
class StartupViewModel @Inject constructor(
    observeAppPreferences: ObserveAppPreferencesUseCase,
    premiumEntitlement: PremiumEntitlementController,
) : ViewModel() {
    val destination = combine(
        observeAppPreferences().catch { emit(AppPreferences()) },
        premiumEntitlement.isPremium,
        premiumEntitlement.isInitialized,
    ) { preferences, isPremium, isInitialized ->
        if (!isInitialized) {
            null
        } else if (!isPremium) {
            StartupDestination.LANGUAGE
        } else if (preferences.onboardingCompleted) {
            StartupDestination.MAIN
        } else {
            StartupDestination.ONBOARDING
        }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null,
        )
}
