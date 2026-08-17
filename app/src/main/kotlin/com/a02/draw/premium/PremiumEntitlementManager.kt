package com.a02.draw.premium

import com.a02.draw.core.ui.ads.PremiumEntitlementController
import com.a02.draw.domain.repository.AppPreferencesRepository
import com.kiro.sdk.KiroSdk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    private var hasAuthoritativeUpdate = false
    private var hasLoadedCachedState = false
    private var initializationRequested = false

    override val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()
    override val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    init {
        scope.launch {
            val cachedPremium = try {
                preferencesRepository.observePreferences().first().isPremium
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                false
            }
            if (!hasAuthoritativeUpdate) applyPremiumState(cachedPremium)
            hasLoadedCachedState = true
            if (initializationRequested) _isInitialized.value = true
        }
    }

    override fun isPremium(): Boolean = _isPremium.value

    override fun setPremiumOwned(isOwned: Boolean) {
        hasAuthoritativeUpdate = true
        applyPremiumState(isOwned)
        _isInitialized.value = true
        scope.launch {
            // Billing remains authoritative; this cache prevents ads from reappearing while a
            // later process start is reconnecting to Google Play.
            preferencesRepository.setPremium(isOwned)
        }
    }

    override fun completeInitialization() {
        if (hasAuthoritativeUpdate || hasLoadedCachedState) {
            _isInitialized.value = true
        } else {
            initializationRequested = true
        }
    }

    private fun applyPremiumState(isOwned: Boolean) {
        _isPremium.value = isOwned
        KiroSdk.setAdsDisabled(isOwned)
        if (isOwned) KiroSdk.ads.hideAllActiveAds()
    }
}
