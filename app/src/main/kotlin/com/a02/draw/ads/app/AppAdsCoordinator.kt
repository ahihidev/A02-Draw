package com.a02.draw.ads.app

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.a02.draw.R
import com.a02.draw.ads.startup.AdLoadResult
import com.a02.draw.ads.startup.GmaStartupAdGateway
import com.a02.draw.ads.startup.StartupAdsCoordinator
import com.a02.draw.ads.startup.TripleFloorTiming
import com.a02.draw.ads.ui.NativeAdFormat
import com.a02.draw.ads.ui.NativeAdHostView
import com.a02.draw.core.ui.ads.AppAdPlacement
import com.a02.draw.core.ui.ads.AppAdsController
import com.a02.draw.core.ui.ads.AppNativeAdFormat
import com.a02.draw.core.ui.ads.PremiumEntitlementController
import com.a02.draw.core.ui.ads.RewardContentKey
import com.a02.draw.core.ui.ads.RewardUnlockResult
import com.a02.draw.core.ui.ads.RewardUnlockStore
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
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
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppAdsCoordinator @Inject internal constructor(
    @param:ApplicationContext private val context: Context,
    private val gateway: GmaStartupAdGateway,
    private val startupAdsCoordinator: StartupAdsCoordinator,
    private val premiumEntitlement: PremiumEntitlementController,
    private val rewardUnlockStore: RewardUnlockStore,
    private val rewardedAdsCoordinator: RewardedAdsCoordinator,
    private val fullScreenArbiter: FullscreenAdArbiter,
    private val backgroundTracker: AppBackgroundTracker,
) : AppAdsController {
    private data class NativeSession(
        val container: ViewGroup,
        val host: NativeAdHostView,
        val lifecycleOwner: LifecycleOwner,
        val observer: DefaultLifecycleObserver,
        var generation: Long,
        var timeoutJob: Job? = null,
        var refreshJob: Job? = null,
        var hasLoadedAd: Boolean = false,
    )

    private data class BannerSession(
        val owner: LifecycleOwner,
        val observer: DefaultLifecycleObserver,
    )

    override val isPremium = premiumEntitlement.isPremium
    override val rewardAccessState = rewardUnlockStore.accessState

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val nativeSessions = mutableMapOf<ViewGroup, NativeSession>()
    private val bannerSessions = mutableMapOf<ViewGroup, BannerSession>()
    private val nativeCache = mutableMapOf<AppAdPlacement, NativeAd>()
    private val nativeFallbackLoads = mutableSetOf<AppAdPlacement>()
    private var nativeFallbackGeneration = 0L
    private var nextGeneration = 0L
    private var interstitialLoadJob: Job? = null
    private var backgroundLoadJob: Job? = null

    init {
        scope.launch {
            isPremium.collect { premium ->
                if (premium) releaseAllAds()
            }
        }
    }

    override fun preloadMainAds() {
        if (!canRequestAds()) return
        startupAdsCoordinator.ensureLanguageTwoFloorSpare()
        if (interstitialLoadJob?.isActive != true) {
            preloadInterstitial(R.string.inter_app, AdType.INTERSTITIAL, ::setInterstitialJob)
        }
        if (backgroundLoadJob?.isActive != true) {
            preloadInterstitial(R.string.inter_background, AdType.INTERSTITIAL, ::setBackgroundJob)
        }
        rewardedAdsCoordinator.preload()
    }

    override fun attachNative(
        container: ViewGroup,
        placement: AppAdPlacement,
        format: AppNativeAdFormat,
        lifecycleOwner: LifecycleOwner,
    ) {
        detachNative(container)
        if (!canRequestAds()) {
            container.isVisible = false
            return
        }

        val hostFormat = when (format) {
            AppNativeAdFormat.MEDIUM -> NativeAdFormat.MEDIUM
            AppNativeAdFormat.LARGE -> NativeAdFormat.LARGE
        }
        val host = NativeAdHostView(container.context, requestedFormat = hostFormat)
        container.removeAllViews()
        container.addView(
            host,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        container.isVisible = true
        host.beginCoordinatorLoad()
        val observer = object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) = detachNative(container)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        val session = NativeSession(
            container = container,
            host = host,
            lifecycleOwner = lifecycleOwner,
            observer = observer,
            generation = ++nextGeneration,
        )
        nativeSessions[container] = session
        val shared = startupAdsCoordinator.takeSharedNativeAd()
        val cached = nativeCache.remove(placement)
        when {
            shared != null -> {
                cached?.let { cacheNative(placement, it) }
                bindNative(session, placement, shared)
                preloadNativeFallback(placement)
            }

            cached != null -> bindNative(session, placement, cached)
            else -> loadNative(session, placement, isRefresh = false)
        }
    }

    override fun detachNative(container: ViewGroup) {
        val session = nativeSessions.remove(container) ?: return
        session.generation = ++nextGeneration
        session.timeoutJob?.cancel()
        session.refreshJob?.cancel()
        session.lifecycleOwner.lifecycle.removeObserver(session.observer)
        session.host.releaseCoordinatorAd()
        container.removeAllViews()
        container.isVisible = false
    }

    override fun attachDrawingBanner(container: ViewGroup, lifecycleOwner: LifecycleOwner) {
        detachDrawingBanner(container)
        if (!canRequestAds()) {
            container.isVisible = false
            return
        }
        val observer = object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) = detachDrawingBanner(container)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        bannerSessions[container] = BannerSession(lifecycleOwner, observer)
        container.isVisible = true
        container.post {
            if (bannerSessions[container]?.owner !== lifecycleOwner || !canRequestAds()) {
                container.isVisible = false
                return@post
            }
            val widthPixels = container.width.takeIf { it > 0 }
                ?: container.resources.displayMetrics.widthPixels
            val widthDp = (widthPixels / container.resources.displayMetrics.density)
                .toInt()
                .coerceAtLeast(1)
            val size = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(
                container.context,
                widthDp,
            )
            KiroSdk.ads.showBanner(
                activity = container.context as Activity,
                container = container,
                adUnitId = context.getString(R.string.banner_draw),
                adSize = size,
            )
        }
    }

    override fun detachDrawingBanner(container: ViewGroup) {
        bannerSessions.remove(container)?.let { session ->
            session.owner.lifecycle.removeObserver(session.observer)
        }
        destroyBannerChildren(container)
        container.isVisible = false
    }

    override fun runNavigationInterstitial(
        activity: Activity,
        isEligible: Boolean,
        action: () -> Unit,
    ) {
        val completed = AtomicBoolean(false)
        fun completeOnce() {
            if (completed.compareAndSet(false, true)) action()
        }
        if (!canRequestAds() || activity.isFinishing || activity.isDestroyed) {
            completeOnce()
            return
        }
        val adUnitId = context.getString(R.string.inter_app)
        val ready = KiroAdPool.hasAd(AdType.INTERSTITIAL, adUnitId)
        fullScreenArbiter.recordNavigation(isEligible)
        val acquired =
            ready && fullScreenArbiter.tryAcquireNavigation(SystemClock.elapsedRealtime())
        if (!acquired) {
            preloadMainAds()
            completeOnce()
            return
        }
        KiroSdk.ads.showInterstitial(activity, adUnitId) {
            fullScreenArbiter.release()
            preloadMainAds()
            completeOnce()
        }
    }

    override fun runBackgroundInterstitial(activity: Activity, action: () -> Unit) {
        val completed = AtomicBoolean(false)
        fun completeOnce() {
            if (completed.compareAndSet(false, true)) action()
        }

        val adUnitId = context.getString(R.string.inter_background)
        if (!canRequestAds() ||
            !KiroAdPool.hasAd(AdType.INTERSTITIAL, adUnitId) ||
            !fullScreenArbiter.tryAcquire(
                SystemClock.elapsedRealtime(),
                FullscreenAdArbiter.FULL_SCREEN_COOLDOWN_MILLIS,
            )
        ) {
            preloadMainAds()
            completeOnce()
            return
        }
        KiroSdk.ads.showInterstitial(activity, adUnitId) {
            fullScreenArbiter.release()
            preloadMainAds()
            completeOnce()
        }
    }

    override fun suppressNextBackgroundInterstitial() {
        backgroundTracker.suppressNextReturn()
    }

    override fun requestRewardedUnlock(
        activity: Activity,
        lifecycleOwner: LifecycleOwner,
        content: RewardContentKey,
        itemName: CharSequence,
        onResult: (RewardUnlockResult) -> Unit,
    ) {
        rewardedAdsCoordinator.requestUnlock(
            activity = activity,
            lifecycleOwner = lifecycleOwner,
            content = content,
            itemName = itemName,
            onResult = onResult,
        )
    }

    private fun loadNative(
        session: NativeSession,
        placement: AppAdPlacement,
        isRefresh: Boolean,
    ) {
        val token = ++nextGeneration
        session.generation = token
        if (!isRefresh) {
            session.timeoutJob?.cancel()
            session.timeoutJob = scope.launch {
                delay(NATIVE_INITIAL_TIMEOUT_MILLIS)
                if (isCurrent(session, token) && !session.hasLoadedAd) {
                    session.generation = ++nextGeneration
                    session.host.hide()
                    session.container.isVisible = false
                }
            }
        }
        gateway.loadNative(context.getString(placement.adUnitRes)) { result ->
            scope.launch {
                if (!isCurrent(session, token) || !canRequestAds()) {
                    if (result is AdLoadResult.Success) {
                        if (canRequestAds()) cacheNative(
                            placement,
                            result.ad
                        ) else result.ad.destroy()
                    }
                    return@launch
                }
                when (result) {
                    is AdLoadResult.Success -> bindNative(session, placement, result.ad)
                    is AdLoadResult.Failure -> if (!isRefresh && !session.hasLoadedAd) {
                        session.timeoutJob?.cancel()
                        session.host.hide()
                        session.container.isVisible = false
                    } else if (isRefresh) {
                        scheduleRefreshRetry(session, placement)
                    }
                }
            }
        }
    }

    private fun bindNative(
        session: NativeSession,
        placement: AppAdPlacement,
        nativeAd: NativeAd,
    ) {
        session.timeoutJob?.cancel()
        session.hasLoadedAd = true
        session.container.isVisible = true
        session.host.bindCoordinatorAd(nativeAd)
        session.refreshJob?.cancel()
        session.refreshJob = scope.launch {
            delay(TripleFloorTiming.NATIVE_REFRESH_MILLIS)
            if (isCurrent(session, session.generation) &&
                session.lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                session.host.isFullyVisibleForRefresh()
            ) {
                refreshNative(session, placement)
            } else if (nativeSessions[session.container] === session) {
                scheduleRefreshRetry(session, placement)
            }
        }
    }

    private fun refreshNative(session: NativeSession, placement: AppAdPlacement) {
        if (nativeSessions[session.container] !== session || !canRequestAds()) return
        val shared = startupAdsCoordinator.takeSharedNativeAd()
        val placementFallback = nativeCache.remove(placement)
        when {
            shared != null -> {
                placementFallback?.let { cacheNative(placement, it) }
                bindNative(session, placement, shared)
                preloadNativeFallback(placement)
            }

            placementFallback != null -> bindNative(session, placement, placementFallback)
            else -> loadNative(session, placement, isRefresh = true)
        }
    }

    private fun scheduleRefreshRetry(session: NativeSession, placement: AppAdPlacement) {
        session.refreshJob?.cancel()
        session.refreshJob = scope.launch {
            delay(TripleFloorTiming.NATIVE_REFRESH_MILLIS)
            if (nativeSessions[session.container] === session && canRequestAds()) {
                if (session.lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                    session.host.isFullyVisibleForRefresh()
                ) {
                    refreshNative(session, placement)
                } else {
                    scheduleRefreshRetry(session, placement)
                }
            }
        }
    }

    private fun preloadNativeFallback(placement: AppAdPlacement) {
        if (!canRequestAds() || placement in nativeCache || !nativeFallbackLoads.add(placement)) {
            return
        }
        val generation = nativeFallbackGeneration
        gateway.loadNative(context.getString(placement.adUnitRes)) { result ->
            scope.launch {
                if (generation != nativeFallbackGeneration) {
                    if (result is AdLoadResult.Success) result.ad.destroy()
                    return@launch
                }
                nativeFallbackLoads.remove(placement)
                when (result) {
                    is AdLoadResult.Success -> {
                        if (canRequestAds()) cacheNative(
                            placement,
                            result.ad
                        ) else result.ad.destroy()
                    }

                    is AdLoadResult.Failure -> Unit
                }
            }
        }
    }

    private fun preloadInterstitial(
        idRes: Int,
        type: AdType,
        setJob: (Job?) -> Unit,
    ) {
        val adUnitId = context.getString(idRes)
        if (KiroAdPool.hasAd(type, adUnitId)) return
        val job = scope.launch { KiroSdk.ads.loadInterstitial(context, adUnitId) }
        setJob(job)
        job.invokeOnCompletion { setJob(null) }
    }

    private fun setInterstitialJob(job: Job?) {
        if (job == null || interstitialLoadJob?.isActive != true) interstitialLoadJob = job
    }

    private fun setBackgroundJob(job: Job?) {
        if (job == null || backgroundLoadJob?.isActive != true) backgroundLoadJob = job
    }

    private fun releaseAllAds() {
        nextGeneration += 1
        interstitialLoadJob?.cancel()
        backgroundLoadJob?.cancel()
        rewardedAdsCoordinator.release()
        nativeSessions.keys.toList().forEach(::detachNative)
        nativeCache.values.forEach(NativeAd::destroy)
        nativeCache.clear()
        nativeFallbackGeneration += 1L
        nativeFallbackLoads.clear()
        bannerSessions.keys.toList().forEach(::detachDrawingBanner)
        KiroAdPool.clearAll()
        KiroSdk.ads.hideAllActiveAds()
        fullScreenArbiter.cancelAcquire()
    }

    private fun destroyBannerChildren(container: ViewGroup) {
        for (index in 0 until container.childCount) {
            (container.getChildAt(index) as? AdView)?.destroy()
        }
        container.removeAllViews()
    }

    private fun cacheNative(placement: AppAdPlacement, ad: NativeAd) {
        nativeCache.put(placement, ad)?.destroy()
    }

    private fun isCurrent(session: NativeSession, generation: Long): Boolean =
        nativeSessions[session.container] === session && session.generation == generation

    private fun canRequestAds(): Boolean =
        premiumEntitlement.isInitialized.value &&
                !isPremium.value &&
                KiroSdk.consent.canRequestAds(context)

    private val AppAdPlacement.adUnitRes: Int
        get() = when (this) {
            AppAdPlacement.CHOOSE_TOPIC -> R.string.native_choose_topic
            AppAdPlacement.HOME -> R.string.native_home
            AppAdPlacement.TOPIC_DETAIL -> R.string.native_topic_detail
            AppAdPlacement.SEARCH -> R.string.native_search
            AppAdPlacement.LEARN -> R.string.native_learn
            AppAdPlacement.LEARN_DETAIL -> R.string.native_learn_detail
            AppAdPlacement.SETTING -> R.string.native_setting
            AppAdPlacement.SELECT_MODE -> R.string.native_select_mode
            AppAdPlacement.APP_GENERIC -> R.string.native_app
        }

    private companion object {
        const val NATIVE_INITIAL_TIMEOUT_MILLIS = 8_000L
    }
}
