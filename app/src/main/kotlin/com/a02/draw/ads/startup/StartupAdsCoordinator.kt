package com.a02.draw.ads.startup

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.a02.draw.ads.app.FullscreenAdArbiter
import com.a02.draw.ads.ui.NativeAdHostView
import com.a02.draw.core.ui.ads.PremiumEntitlementController
import com.a02.draw.premium.PremiumAccessProvider
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.nativead.NativeAd
import com.kiro.sdk.KiroSdk
import com.kiro.sdk.ads.AdType
import com.kiro.sdk.ads.KiroAdPool
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StartupAdsCoordinator @Inject internal constructor(
    @param:ApplicationContext private val context: Context,
    private val gateway: GmaStartupAdGateway,
    private val environment: StartupAdsEnvironment,
    private val premiumAccessProvider: PremiumAccessProvider,
    private val premiumEntitlementController: PremiumEntitlementController,
    private val fullScreenArbiter: FullscreenAdArbiter,
) {
    private data class CachedInterstitial(
        val ad: InterstitialAd,
        val adUnitId: String,
        val loadedAt: Long,
    )

    private data class CachedNative(
        val ad: NativeAd,
        val loadedAt: Long,
        val origin: NativeInventoryOrigin,
    )

    private data class NativeSession(
        val generation: Long,
        val placement: StartupAdPlacement,
        val owner: LifecycleOwner,
        val observer: DefaultLifecycleObserver,
        var job: Job? = null,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val policy = TripleFloorPolicy()
    private val specs = startupAdSpecs.associateBy(TripleFloorAdSpec::placement)
    private val interstitials = mutableMapOf<AdInventoryKey, CachedInterstitial>()
    private val natives = mutableMapOf<AdInventoryKey, CachedNative>()
    private val loadTokens = mutableMapOf<AdInventoryKey, Long>()
    private val requestedOnce = mutableSetOf<AdInventoryKey>()
    private val retryJobs = mutableMapOf<AdInventoryKey, Job>()
    private val sessions = mutableMapOf<NativeAdHostView, NativeSession>()
    private var nextGeneration = 0L

    init {
        scope.launch {
            premiumEntitlementController.isPremium.collect { premium ->
                if (premium) releaseAll()
            }
        }
    }

    fun showSplashInterstitial(activity: Activity, onComplete: () -> Unit) {
        val completed = AtomicBoolean(false)
        fun completeOnce() {
            if (completed.compareAndSet(false, true)) onComplete()
        }

        scope.launch {
            val entitlementInitialized = withTimeoutOrNull(PREMIUM_INIT_TIMEOUT_MILLIS) {
                premiumEntitlementController.isInitialized.first { it }
                true
            } ?: false
            if (!entitlementInitialized) {
                Log.w(TAG, "Premium initialization timed out; skipping splash ads.")
                completeOnce()
                return@launch
            }
            if (!canRequestAds()) {
                delay(SPLASH_MINIMUM_MILLIS)
                preloadStartupNativeAds()
                completeOnce()
                return@launch
            }

            // Fill the next screens' inventory while Splash is still visible. Starting these
            // first gives Language and Intro the full interstitial wait/show window to preload.
            preloadStartupNativeAds()
            startWaterfall(StartupAdPlacement.SPLASH)
            val startedAt = SystemClock.elapsedRealtime()
            var selected: Pair<AdInventoryKey, CachedInterstitial>? = null
            while (isActive && SystemClock.elapsedRealtime() - startedAt < TripleFloorTiming.SPLASH_DEADLINE_MILLIS) {
                if (!canRequestAds()) {
                    Log.d(TAG, "Splash ads disabled while loading; continuing startup.")
                    completeOnce()
                    return@launch
                }
                purgeExpired()
                val elapsed = SystemClock.elapsedRealtime() - startedAt
                if (elapsed >= SPLASH_MINIMUM_MILLIS) {
                    selected = selectSplashBeforeDeadline()
                    if (selected != null) break
                    if (areAllSplashFloorsTerminal()) {
                        Log.d(TAG, "All splash floors failed; continuing startup.")
                        break
                    }
                }
                delay(POLL_MILLIS)
            }
            selected = selected ?: takeBestInterstitial()

            val chosen = selected
            if (chosen == null || activity.isFinishing || activity.isDestroyed) {
                preloadStartupNativeAds()
                completeOnce()
                return@launch
            }

            interstitials.remove(chosen.first)
            policy.markConsumed(chosen.first)
            KiroAdPool.putAd(AdType.INTERSTITIAL, chosen.second.adUnitId, chosen.second.ad)
            Log.d(TAG, "SHOW ${chosen.first.placement}/${chosen.first.floor}")
            try {
                fullScreenArbiter.recordFullScreenShown(SystemClock.elapsedRealtime())
                KiroSdk.ads.showInterstitial(activity, chosen.second.adUnitId) {
                    preloadStartupNativeAds()
                    if (chosen.first.floor == AdFloor.TWO_FLOOR) {
                        scheduleReload(chosen.first)
                    }
                    completeOnce()
                }
            } catch (error: RuntimeException) {
                Log.w(TAG, "Could not show splash interstitial.", error)
                preloadStartupNativeAds()
                completeOnce()
            }
        }
    }

    fun preloadStartupNativeAds() {
        if (!canRequestAds()) {
            releaseAll()
            return
        }
        startupNativePreloadOrder.forEach { placement ->
            startWaterfall(placement, NativeInventoryOrigin.SPLASH_PRELOAD)
        }
        ensureLanguageTwoFloorSpare()
    }

    internal fun ensureLanguageTwoFloorSpare() {
        if (!canRequestAds()) return
        purgeExpired()
        val key = languageTwoFloorKey
        if (
            key in natives ||
            policy.state(key) != FloorRequestState.IDLE ||
            retryJobs[key]?.isActive == true
        ) {
            return
        }
        requestFloor(
            key = key,
            forceReload = true,
            nativeOrigin = NativeInventoryOrigin.LANGUAGE_SPARE,
        )
    }

    internal fun takeSharedNativeAd(): NativeAd? {
        if (!canRequestAds()) return null
        purgeExpired()
        ensureLanguageTwoFloorSpare()
        val inventory = natives.mapValues { it.value.origin }
        val key = StartupNativeInventoryPolicy.sharedCandidates(inventory).firstOrNull()
            ?: run {
                ensureLanguageTwoFloorSpare()
                return null
            }
        return consumeNative(key).ad
    }

    fun attachNative(
        host: NativeAdHostView,
        placement: StartupAdPlacement,
        lifecycleOwner: LifecycleOwner,
        onUnavailable: () -> Unit = {},
    ) {
        require(placement != StartupAdPlacement.SPLASH)
        detachNative(host)
        val generation = ++nextGeneration
        val observer = object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                detachNative(host)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        val session = NativeSession(generation, placement, lifecycleOwner, observer)
        sessions[host] = session
        Log.d(TAG, "ATTACH $placement generation=$generation")
        host.beginCoordinatorLoad()

        if (!canRequestAds()) {
            host.hide()
            onUnavailable()
            return
        }

        ensureLanguageTwoFloorSpare()
        startWaterfall(placement, NativeInventoryOrigin.PLACEMENT)
        requestFloor(
            AdInventoryKey(placement, AdFloor.TWO_FLOOR),
            forceReload = true,
            nativeOrigin = NativeInventoryOrigin.PLACEMENT,
        )
        // AP may have been consumed or failed before this screen became visible. It is the
        // placement-local fallback and can be requested again without starting duplicate work.
        requestFloor(
            AdInventoryKey(placement, AdFloor.ALL_PRICES),
            forceReload = true,
            nativeOrigin = NativeInventoryOrigin.PLACEMENT,
        )
        session.job = scope.launch {
            val deadline =
                SystemClock.elapsedRealtime() + TripleFloorTiming.NATIVE_FIRST_RENDER_TIMEOUT_MILLIS
            var didBind = false
            while (isCurrent(host, generation) && SystemClock.elapsedRealtime() < deadline) {
                val candidate = takeBestNative(placement)
                if (candidate != null) {
                    bindNative(host, generation, placement, candidate)
                    didBind = true
                    break
                }
                delay(POLL_MILLIS)
            }
            if (!didBind && isCurrent(host, generation)) {
                Log.d(TAG, "UNAVAILABLE $placement generation=$generation")
                host.hide()
                onUnavailable()
                if (placement == StartupAdPlacement.ONBOARDING_FULL) return@launch
            }
            if (didBind) runNativeRefreshLoop(host, generation, placement)
        }
    }

    fun detachNative(host: NativeAdHostView) {
        val session = sessions.remove(host) ?: return
        Log.d(TAG, "DETACH ${session.placement} generation=${session.generation}")
        session.job?.cancel()
        session.owner.lifecycle.removeObserver(session.observer)
        if (host.isAttachedToWindow) host.releaseCoordinatorAd()
    }

    private suspend fun runNativeRefreshLoop(
        host: NativeAdHostView,
        generation: Long,
        placement: StartupAdPlacement,
    ) {
        while (isCurrent(host, generation)) {
            delay(TripleFloorTiming.NATIVE_REFRESH_MILLIS)
            if (
                !isCurrent(host, generation) ||
                !environment.isForeground.value ||
                !host.isFullyVisibleForRefresh() ||
                !sessions.getValue(host).owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            ) {
                continue
            }

            var replacement = takeBestNative(placement)
            if (replacement == null) {
                ensureLanguageTwoFloorSpare()
                requestFloor(
                    AdInventoryKey(placement, AdFloor.ALL_PRICES),
                    forceReload = true,
                    nativeOrigin = NativeInventoryOrigin.PLACEMENT,
                )
                val replacementDeadline =
                    SystemClock.elapsedRealtime() + TripleFloorTiming.NATIVE_FIRST_RENDER_TIMEOUT_MILLIS
                while (isCurrent(
                        host,
                        generation
                    ) && SystemClock.elapsedRealtime() < replacementDeadline
                ) {
                    replacement = takeBestNative(placement)
                    if (replacement != null) break
                    delay(POLL_MILLIS)
                }
            }
            replacement?.let { bindNative(host, generation, placement, it) }
        }
    }

    private fun bindNative(
        host: NativeAdHostView,
        generation: Long,
        placement: StartupAdPlacement,
        candidate: Pair<AdInventoryKey, CachedNative>,
    ) {
        if (!isCurrent(host, generation)) {
            candidate.second.ad.destroy()
            return
        }
        Log.d(
            TAG,
            "BIND $placement using ${candidate.first.placement}/${candidate.first.floor} " +
                    "origin=${candidate.second.origin} generation=$generation",
        )
        host.bindCoordinatorAd(candidate.second.ad)
    }

    private fun startWaterfall(
        placement: StartupAdPlacement,
        nativeOrigin: NativeInventoryOrigin = NativeInventoryOrigin.PLACEMENT,
    ) {
        val spec = specs.getValue(placement)
        AdFloor.entries.forEach { floor ->
            scope.launch {
                delay(TripleFloorTiming.floorDelay(floor))
                requestFloor(
                    key = AdInventoryKey(spec.placement, floor),
                    nativeOrigin = nativeOrigin,
                )
            }
        }
    }

    private fun requestFloor(
        key: AdInventoryKey,
        forceReload: Boolean = false,
        nativeOrigin: NativeInventoryOrigin = NativeInventoryOrigin.PLACEMENT,
    ) {
        if (!canRequestAds()) return
        if (!forceReload && key in requestedOnce) return
        if (!policy.markLoading(key)) return
        requestedOnce += key
        val token = loadTokens.getOrDefault(key, 0L) + 1L
        loadTokens[key] = token
        val spec = specs.getValue(key.placement)
        val adUnitId = context.getString(spec.idFor(key.floor))
        Log.d(TAG, "REQUEST ${key.placement}/${key.floor}")

        when (spec.format) {
            StartupAdFormat.INTERSTITIAL -> gateway.loadInterstitial(adUnitId) { result ->
                scope.launch { handleInterstitialResult(key, token, adUnitId, result) }
            }

            StartupAdFormat.NATIVE -> gateway.loadNative(adUnitId) { result ->
                scope.launch { handleNativeResult(key, token, nativeOrigin, result) }
            }
        }
        scope.launch {
            delay(LOAD_TIMEOUT_MILLIS)
            if (loadTokens[key] == token && policy.state(key) == FloorRequestState.LOADING) {
                loadTokens[key] = token + 1L
                handleFailure(
                    key,
                    AdLoadResult.Failure(AdLoadFailure.TIMEOUT, "GMA load timed out"),
                    nativeOrigin,
                )
            }
        }
    }

    private suspend fun handleInterstitialResult(
        key: AdInventoryKey,
        token: Long,
        adUnitId: String,
        result: AdLoadResult<InterstitialAd>,
    ) {
        if (loadTokens[key] != token || !canRequestAds()) return
        when (result) {
            is AdLoadResult.Success -> {
                interstitials.put(
                    key,
                    CachedInterstitial(result.ad, adUnitId, SystemClock.elapsedRealtime()),
                )
                policy.markReady(key)
                Log.d(TAG, "READY ${key.placement}/${key.floor}")
            }

            is AdLoadResult.Failure -> handleFailure(key, result)
        }
    }

    private suspend fun handleNativeResult(
        key: AdInventoryKey,
        token: Long,
        origin: NativeInventoryOrigin,
        result: AdLoadResult<NativeAd>,
    ) {
        if (loadTokens[key] != token || !canRequestAds()) {
            if (result is AdLoadResult.Success) result.ad.destroy()
            return
        }
        when (result) {
            is AdLoadResult.Success -> {
                natives.put(
                    key,
                    CachedNative(result.ad, SystemClock.elapsedRealtime(), origin),
                )?.ad?.destroy()
                policy.markReady(key)
                Log.d(TAG, "READY ${key.placement}/${key.floor} origin=$origin")
            }

            is AdLoadResult.Failure -> handleFailure(key, result, origin)
        }
    }

    private fun handleFailure(
        key: AdInventoryKey,
        failure: AdLoadResult.Failure,
        nativeOrigin: NativeInventoryOrigin = NativeInventoryOrigin.PLACEMENT,
    ) {
        Log.w(TAG, "${key.placement}/${key.floor} failed: ${failure.reason}: ${failure.message}")
        val shouldRetry = policy.onFailure(key, failure.reason)
        if (!shouldRetry) return
        if (retryJobs[key]?.isActive == true) return

        val retryJob = scope.launch {
            when (failure.reason) {
                AdLoadFailure.NETWORK_ERROR -> environment.hasValidatedNetwork.first { it }
                else -> delay(TripleFloorTiming.INTERNAL_RETRY_DELAY_MILLIS)
            }
            if (key == languageTwoFloorKey) {
                environment.isForeground.first { it }
            }
            requestFloor(key, forceReload = true, nativeOrigin = nativeOrigin)
        }
        retryJobs[key] = retryJob
        retryJob.invokeOnCompletion {
            scope.launch {
                if (retryJobs[key] === retryJob) retryJobs.remove(key)
            }
        }
    }

    private fun selectSplashBeforeDeadline(): Pair<AdInventoryKey, CachedInterstitial>? {
        val highKey = AdInventoryKey(StartupAdPlacement.SPLASH, AdFloor.TWO_FLOOR)
        interstitials[highKey]?.let { return highKey to it }
        val mediumKey = AdInventoryKey(StartupAdPlacement.SPLASH, AdFloor.MEDIUM_FLOOR)
        if (policy.isTerminal(highKey)) interstitials[mediumKey]?.let { return mediumKey to it }
        val allKey = AdInventoryKey(StartupAdPlacement.SPLASH, AdFloor.ALL_PRICES)
        if (policy.isTerminal(highKey) && policy.isTerminal(mediumKey)) {
            interstitials[allKey]?.let { return allKey to it }
        }
        return null
    }

    private fun takeBestInterstitial(): Pair<AdInventoryKey, CachedInterstitial>? =
        AdFloor.entries.firstNotNullOfOrNull { floor ->
            val key = AdInventoryKey(StartupAdPlacement.SPLASH, floor)
            interstitials[key]?.let { key to it }
        }

    private fun areAllSplashFloorsTerminal(): Boolean = AdFloor.entries.all { floor ->
        policy.isTerminal(AdInventoryKey(StartupAdPlacement.SPLASH, floor))
    }

    private fun takeBestNative(placement: StartupAdPlacement): Pair<AdInventoryKey, CachedNative>? {
        purgeExpired()
        ensureLanguageTwoFloorSpare()
        val inventory = natives.mapValues { it.value.origin }
        val key = StartupNativeInventoryPolicy.startupCandidates(placement, inventory).firstOrNull()
            ?: run {
                ensureLanguageTwoFloorSpare()
                return null
            }
        return key to consumeNative(key)
    }

    private fun consumeNative(key: AdInventoryKey): CachedNative {
        val cached = checkNotNull(natives.remove(key))
        policy.markConsumed(key)
        if (key == languageTwoFloorKey) ensureLanguageTwoFloorSpare()
        return cached
    }

    private fun purgeExpired() {
        val now = SystemClock.elapsedRealtime()
        interstitials.entries.removeAll { entry ->
            val expired = now - entry.value.loadedAt >= TripleFloorTiming.STANDBY_MAX_AGE_MILLIS
            if (expired) policy.markExpired(entry.key)
            expired
        }
        natives.entries.removeAll { entry ->
            val expired = now - entry.value.loadedAt >= TripleFloorTiming.STANDBY_MAX_AGE_MILLIS
            if (expired) {
                entry.value.ad.destroy()
                policy.markExpired(entry.key)
            }
            expired
        }
    }

    private fun scheduleReload(key: AdInventoryKey) {
        scope.launch {
            delay(TripleFloorTiming.RELOAD_DELAY_MILLIS)
            if (
                key.placement == StartupAdPlacement.LANGUAGE &&
                !hasActiveSession(StartupAdPlacement.LANGUAGE)
            ) {
                return@launch
            }
            requestFloor(key, forceReload = true)
        }
    }

    private fun isCurrent(host: NativeAdHostView, generation: Long): Boolean =
        sessions[host]?.generation == generation

    private fun hasActiveSession(placement: StartupAdPlacement): Boolean =
        sessions.values.any { it.placement == placement }

    private fun canRequestAds(): Boolean =
        premiumEntitlementController.isInitialized.value &&
                !premiumAccessProvider.isPremium() &&
                KiroSdk.consent.canRequestAds(context)

    private fun releaseAll() {
        loadTokens.keys.forEach { loadTokens[it] = loadTokens.getValue(it) + 1L }
        retryJobs.values.forEach(Job::cancel)
        retryJobs.clear()
        interstitials.clear()
        natives.values.forEach { it.ad.destroy() }
        natives.clear()
        sessions.keys.toList().forEach(::detachNative)
    }

    private companion object {
        const val TAG = "StartupAds"
        const val POLL_MILLIS = 100L
        const val SPLASH_MINIMUM_MILLIS = 900L
        const val PREMIUM_INIT_TIMEOUT_MILLIS = 8_000L
        const val LOAD_TIMEOUT_MILLIS = 10_000L
        val languageTwoFloorKey = AdInventoryKey(
            StartupAdPlacement.LANGUAGE,
            AdFloor.TWO_FLOOR,
        )
    }
}
