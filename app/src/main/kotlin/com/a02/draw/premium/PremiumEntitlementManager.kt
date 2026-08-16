package com.a02.draw.premium

import com.a02.draw.core.ui.ads.PremiumEntitlementController
import com.a02.draw.domain.repository.AppPreferencesRepository
import com.kiro.sdk.KiroSdk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PremiumEntitlementManager @Inject constructor(
    private val preferencesRepository: AppPreferencesRepository,
) : PremiumEntitlementController, PremiumAccessProvider {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _isPremium = MutableStateFlow(false)
    private val _isInitialized = MutableStateFlow(false)

    override val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()
    override val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    override fun isPremium(): Boolean = _isPremium.value

    override fun setPremiumOwned(isOwned: Boolean) {
        applyPremiumState(isOwned)
        _isInitialized.value = true
        scope.launch {
            // Startup never reads this cache to grant access. Only the Application-triggered
            // Billing plus verifier result above can update the in-memory entitlement.
            preferencesRepository.setPremium(isOwned)
        }
    }

    private fun applyPremiumState(isOwned: Boolean) {
        _isPremium.value = isOwned
        KiroSdk.setAdsDisabled(isOwned)
        if (isOwned) KiroSdk.ads.hideAllActiveAds()
    }
}
