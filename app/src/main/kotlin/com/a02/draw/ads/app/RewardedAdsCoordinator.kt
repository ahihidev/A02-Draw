package com.a02.draw.ads.app

import android.app.Activity
import android.content.Context
import androidx.core.view.isVisible
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.a02.draw.R
import com.a02.draw.ads.startup.StartupAdsEnvironment
import com.a02.draw.core.ui.ads.PremiumEntitlementController
import com.a02.draw.core.ui.ads.RewardContentKey
import com.a02.draw.core.ui.ads.RewardUnlockResult
import com.a02.draw.core.ui.ads.RewardUnlockStore
import com.a02.draw.databinding.ViewRewardUnlockSheetBinding
import com.a02.draw.premium.RewardTimeSource
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.kiro.sdk.KiroSdk
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@Singleton
internal class RewardedAdsCoordinator @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val gateway: RewardedAdGateway,
    private val premiumEntitlement: PremiumEntitlementController,
    private val rewardUnlockStore: RewardUnlockStore,
    private val fullScreenArbiter: FullscreenAdArbiter,
    private val environment: StartupAdsEnvironment,
    private val timeSource: RewardTimeSource,
) {
    private class ActiveRequest(
        val token: Long,
        var activity: Activity?,
        var lifecycleOwner: LifecycleOwner?,
        val content: RewardContentKey,
        var onResult: ((RewardUnlockResult) -> Unit)?,
    ) {
        var observer: DefaultLifecycleObserver? = null
        var isShowingAd: Boolean = false
        var dialogDismissHandled: Boolean = false
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val adUnitId: String
        get() = context.getString(R.string.rewarded_unlock_item)

    private var nextToken = 0L
    private var generation = 0L
    private var activeRequest: ActiveRequest? = null
    private var dialog: BottomSheetDialog? = null
    private var binding: ViewRewardUnlockSheetBinding? = null
    private var prepareJob: Job? = null
    private var preloadLoopJob: Job? = null
    private var loadDeferred: Deferred<Boolean>? = null

    init {
        scope.launch {
            premiumEntitlement.isPremium.collect { premium ->
                if (premium) release()
            }
        }
    }

    fun preload() {
        if (!canRequestAds() || gateway.isReady(adUnitId) || preloadLoopJob?.isActive == true) {
            return
        }
        preloadLoopJob = scope.launch {
            var failureIndex = 0
            while (canRequestAds() && !gateway.isReady(adUnitId)) {
                environment.isForeground.first { it }
                environment.hasValidatedNetwork.first { it }
                if (!canRequestAds()) return@launch
                if (ensureLoaded().await() || gateway.isReady(adUnitId)) return@launch
                delay(RETRY_DELAYS_MILLIS[failureIndex])
                failureIndex = (failureIndex + 1).coerceAtMost(RETRY_DELAYS_MILLIS.lastIndex)
            }
        }.also { job ->
            job.invokeOnCompletion {
                if (preloadLoopJob === job) preloadLoopJob = null
            }
        }
    }

    fun requestUnlock(
        activity: Activity,
        lifecycleOwner: LifecycleOwner,
        content: RewardContentKey,
        itemName: CharSequence,
        onResult: (RewardUnlockResult) -> Unit,
    ) {
        if (premiumEntitlement.isPremium.value || rewardUnlockStore.accessState.value.hasAccess(
                content
            )
        ) {
            deliverIfActive(lifecycleOwner, onResult, RewardUnlockResult.GRANTED)
            return
        }
        if (activeRequest != null) {
            deliverIfActive(lifecycleOwner, onResult, RewardUnlockResult.BUSY)
            return
        }
        if (!canRequestAds() || activity.isFinishing || activity.isDestroyed ||
            !lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        ) {
            deliverIfActive(lifecycleOwner, onResult, RewardUnlockResult.NOT_READY)
            return
        }

        val request = ActiveRequest(
            token = ++nextToken,
            activity = activity,
            lifecycleOwner = lifecycleOwner,
            content = content,
            onResult = onResult,
        )
        val observer = object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                onRequesterDestroyed(request.token)
            }
        }
        request.observer = observer
        lifecycleOwner.lifecycle.addObserver(observer)
        activeRequest = request
        showPrompt(request, itemName)
    }

    fun release() {
        generation += 1
        prepareJob?.cancel()
        prepareJob = null
        preloadLoopJob?.cancel()
        preloadLoopJob = null
        loadDeferred?.cancel()
        loadDeferred = null
        clearActiveRequest(deliver = null)
    }

    private fun showPrompt(request: ActiveRequest, itemName: CharSequence) {
        val activity = request.activity ?: return
        val sheetBinding = ViewRewardUnlockSheetBinding.inflate(activity.layoutInflater)
        binding = sheetBinding
        sheetBinding.rewardTitle.setText(request.content.titleRes)
        sheetBinding.rewardMessage.text = when (request.content) {
            is RewardContentKey.Artwork -> activity.getString(
                R.string.reward_unlock_artwork_message,
                itemName,
            )

            is RewardContentKey.Lesson -> activity.getString(R.string.reward_unlock_lesson_message)
            is RewardContentKey.Emoji -> activity.getString(R.string.reward_unlock_emoji_message)
        }
        sheetBinding.notNowButton.setOnClickListener { decline(request.token) }

        val sheetDialog = BottomSheetDialog(activity).apply {
            setContentView(sheetBinding.root)
            setCanceledOnTouchOutside(true)
            setOnDismissListener {
                val current = activeRequest
                if (current?.token == request.token && !current.dialogDismissHandled) {
                    decline(request.token, dismissDialog = false)
                }
            }
            show()
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
        }
        dialog = sheetDialog
        if (gateway.isReady(adUnitId)) renderReady(request.token) else prepare(request.token)
    }

    private fun prepare(token: Long) {
        val request = activeRequest?.takeIf { it.token == token } ?: return
        if (request.isShowingAd) return
        renderPreparing(token)
        preload()
        prepareJob?.cancel()
        prepareJob = scope.launch {
            val loaded = withTimeoutOrNull(PREPARE_TIMEOUT_MILLIS) {
                environment.isForeground.first { it }
                environment.hasValidatedNetwork.first { it }
                if (!canRequestAds()) false else ensureLoaded().await()
            } == true
            if (activeRequest?.token != token || request.isShowingAd) return@launch
            if ((loaded || gateway.isReady(adUnitId)) && canRequestAds()) {
                renderReady(token)
            } else {
                renderRetry(token, R.string.reward_retry_message)
            }
        }
    }

    private fun renderPreparing(token: Long) {
        if (activeRequest?.token != token) return
        binding?.apply {
            rewardProgress.isVisible = true
            rewardStatus.setText(R.string.reward_preparing_ad)
            watchAdButton.isEnabled = false
            watchAdButton.setText(R.string.reward_preparing_ad)
            watchAdButton.setOnClickListener(null)
        }
    }

    private fun renderReady(token: Long) {
        if (activeRequest?.token != token) return
        binding?.apply {
            rewardProgress.isVisible = false
            rewardStatus.setText(R.string.reward_ad_ready)
            watchAdButton.isEnabled = true
            watchAdButton.setText(R.string.reward_watch_ad)
            watchAdButton.setOnClickListener { showRewarded(token) }
        }
    }

    private fun renderRetry(token: Long, messageRes: Int) {
        if (activeRequest?.token != token) return
        binding?.apply {
            rewardProgress.isVisible = false
            rewardStatus.setText(messageRes)
            watchAdButton.isEnabled = true
            watchAdButton.setText(R.string.reward_retry)
            watchAdButton.setOnClickListener { prepare(token) }
        }
    }

    private fun showRewarded(token: Long) {
        val request = activeRequest?.takeIf { it.token == token } ?: return
        val activity = request.activity ?: return
        if (!gateway.isReady(adUnitId)) {
            prepare(token)
            return
        }
        if (!fullScreenArbiter.tryAcquire(timeSource.elapsedRealtimeMillis(), 0L)) {
            renderRetry(token, R.string.reward_another_ad_showing)
            return
        }

        prepareJob?.cancel()
        request.isShowingAd = true
        request.dialogDismissHandled = true
        dialog?.dismiss()
        dialog = null
        binding = null
        request.activity = null
        val earned = AtomicBoolean(false)
        val showGeneration = generation
        gateway.show(
            activity = activity,
            adUnitId = adUnitId,
            onRewardEarned = {
                if (earned.compareAndSet(false, true)) {
                    scope.launch {
                        if (showGeneration == generation && activeRequest?.token == token &&
                            !premiumEntitlement.isPremium.value
                        ) {
                            rewardUnlockStore.grant(request.content)
                        }
                    }
                }
            },
            onComplete = {
                scope.launch {
                    fullScreenArbiter.release(resetNavigationCount = true)
                    preload()
                    if (showGeneration != generation || activeRequest?.token != token) return@launch
                    if (earned.get()) rewardUnlockStore.grant(request.content)
                    clearActiveRequest(
                        deliver = if (earned.get()) {
                            RewardUnlockResult.GRANTED
                        } else {
                            RewardUnlockResult.NOT_COMPLETED
                        },
                    )
                }
            },
        )
    }

    private fun decline(token: Long, dismissDialog: Boolean = true) {
        val request = activeRequest?.takeIf { it.token == token } ?: return
        request.dialogDismissHandled = true
        clearActiveRequest(
            deliver = RewardUnlockResult.DECLINED,
            dismissDialog = dismissDialog,
        )
    }

    private fun onRequesterDestroyed(token: Long) {
        val request = activeRequest?.takeIf { it.token == token } ?: return
        request.onResult = null
        removeLifecycleObserver(request)
        request.lifecycleOwner = null
        if (!request.isShowingAd) clearActiveRequest(deliver = null)
    }

    private fun clearActiveRequest(
        deliver: RewardUnlockResult?,
        dismissDialog: Boolean = true,
    ) {
        val request = activeRequest ?: return
        activeRequest = null
        prepareJob?.cancel()
        prepareJob = null
        removeLifecycleObserver(request)
        val owner = request.lifecycleOwner
        val callback = request.onResult
        request.lifecycleOwner = null
        request.onResult = null
        request.activity = null
        dialog?.setOnDismissListener(null)
        if (dismissDialog) dialog?.dismiss()
        dialog = null
        binding = null
        if (deliver != null && owner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) == true) {
            callback?.invoke(deliver)
        }
    }

    private fun removeLifecycleObserver(request: ActiveRequest) {
        val owner = request.lifecycleOwner ?: return
        request.observer?.let(owner.lifecycle::removeObserver)
        request.observer = null
    }

    private fun ensureLoaded(): Deferred<Boolean> {
        if (gateway.isReady(adUnitId)) return CompletableDeferred(true)
        loadDeferred?.takeIf { it.isActive }?.let { return it }
        return scope.async { canRequestAds() && gateway.load(adUnitId) }.also { deferred ->
            loadDeferred = deferred
            deferred.invokeOnCompletion {
                scope.launch {
                    if (loadDeferred === deferred) loadDeferred = null
                    val request = activeRequest
                    if (request != null && !request.isShowingAd && canRequestAds() &&
                        gateway.isReady(adUnitId)
                    ) {
                        renderReady(request.token)
                    }
                }
            }
        }
    }

    private fun deliverIfActive(
        owner: LifecycleOwner,
        callback: (RewardUnlockResult) -> Unit,
        result: RewardUnlockResult,
    ) {
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) callback(result)
    }

    private fun canRequestAds(): Boolean =
        premiumEntitlement.isInitialized.value &&
                !premiumEntitlement.isPremium.value &&
                KiroSdk.consent.canRequestAds(context)

    private val RewardContentKey.titleRes: Int
        get() = when (this) {
            is RewardContentKey.Artwork -> R.string.reward_unlock_artwork_title
            is RewardContentKey.Lesson -> R.string.reward_unlock_lesson_title
            is RewardContentKey.Emoji -> R.string.reward_unlock_emoji_title
        }

    private companion object {
        const val PREPARE_TIMEOUT_MILLIS = 3_000L
        val RETRY_DELAYS_MILLIS = longArrayOf(5_000L, 15_000L, 30_000L)
    }
}
