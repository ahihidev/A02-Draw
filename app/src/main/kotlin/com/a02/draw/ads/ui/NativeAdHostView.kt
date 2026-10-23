package com.a02.draw.ads.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.VisibleForTesting
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat
import androidx.core.content.res.use
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.a02.draw.R
import com.google.android.gms.ads.nativead.NativeAd
import com.kiro.sdk.KiroSdk
import com.kiro.sdk.ads.KiroNativeAdView

class NativeAdHostView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    requestedFormat: NativeAdFormat? = null,
) : FrameLayout(context, attrs, defStyleAttr) {
    val format: NativeAdFormat

    val state: NativeAdHostState
        get() = stateMachine.state

    var onStateChanged: ((NativeAdHostState) -> Unit)? = null
    var onFullScreenExit: ((NativeFullAdExitReason) -> Unit)? = null

    private val stateMachine = NativeAdStateMachine()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var nativeAdView: KiroNativeAdView
    private val skeletonView: NativeAdSkeletonView
    private val closeButton: AppCompatImageButton?
    private val fullScreenExitDispatcher = NativeFullAdExitDispatcher()
    private var coordinatorOwnedAd: NativeAd? = null
    private var retiringCoordinatorAd: NativeAd? = null
    private var retiringNativeAdView: KiroNativeAdView? = null

    init {
        format = requestedFormat ?: context.obtainStyledAttributes(
            attrs,
            R.styleable.NativeAdHostView,
        ).use { values ->
            when (values.getInt(R.styleable.NativeAdHostView_nativeAdFormat, FORMAT_MEDIUM)) {
                FORMAT_LARGE -> NativeAdFormat.LARGE
                FORMAT_FULL -> NativeAdFormat.FULL
                else -> NativeAdFormat.MEDIUM
            }
        }

        if (format == NativeAdFormat.FULL) {
            setBackgroundColor(ContextCompat.getColor(context, R.color.native_ad_full_surface))
        }

        nativeAdView = KiroNativeAdView(context, format.layoutResId).apply {
            visibility = View.INVISIBLE
        }
        addView(
            nativeAdView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )

        skeletonView = NativeAdSkeletonView(context).apply {
            format = this@NativeAdHostView.format
        }
        addView(
            skeletonView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )

        closeButton = if (format == NativeAdFormat.FULL) createCloseButton() else null
        closeButton?.let(::addView)
        if (format == NativeAdFormat.FULL) applySystemBarInsets()

        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        visibility = View.GONE
    }

    fun load(request: NativeAdRequest) {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "NativeAdHostView.load must be called from the main thread."
        }

        val requestToken = stateMachine.startLoading()
        fullScreenExitDispatcher.reset()
        renderLoading()
        notifyStateChanged()

        if (!KiroSdk.consent.canRequestAds(context)) {
            handleLoadResult(requestToken, wasSuccessful = false)
            return
        }

        try {
            when (request) {
                is NativeAdRequest.Single -> nativeAdView.loadAndShowAd(request.adUnitId) { success ->
                    dispatchLoadResult(requestToken, success)
                }

                is NativeAdRequest.TwoFloor -> nativeAdView.loadAndShowAd2F(
                    request.highAdUnitId,
                    request.lowAdUnitId,
                ) { success ->
                    dispatchLoadResult(requestToken, success)
                }
            }
        } catch (error: RuntimeException) {
            Log.w(TAG, "Could not start native ad load.", error)
            handleLoadResult(requestToken, wasSuccessful = false)
        }
    }

    fun hide() {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "NativeAdHostView.hide must be called from the main thread."
        }
        stateMachine.hide()
        renderHidden()
        notifyStateChanged()
    }

    /** Shows the stable-size skeleton while StartupAdsCoordinator owns the network request. */
    fun beginCoordinatorLoad() {
        check(Looper.myLooper() == Looper.getMainLooper())
        stateMachine.startLoading()
        fullScreenExitDispatcher.reset()
        renderLoading()
        notifyStateChanged()
    }

    /**
     * Binds an already loaded ad. The caller owns the returned previous ad and must destroy it.
     * KiroNativeAdView performs the SDK-compatible asset population and impression tracking.
     */
    fun bindCoordinatorAd(ad: NativeAd) {
        check(Looper.myLooper() == Looper.getMainLooper())
        val previousAd = coordinatorOwnedAd
        if (previousAd != null && previousAd !== ad) {
            crossfadeCoordinatorReplacement(previousAd, ad)
            return
        }
        coordinatorOwnedAd = ad
        nativeAdView.setNativeAd(ad)
        val token = stateMachine.startLoading()
        stateMachine.completeLoading(token, wasSuccessful = true)
        renderLoaded()
        notifyStateChanged()
    }

    fun releaseCoordinatorAd() {
        check(Looper.myLooper() == Looper.getMainLooper())
        coordinatorOwnedAd?.destroy()
        coordinatorOwnedAd = null
        retiringCoordinatorAd?.destroy()
        retiringCoordinatorAd = null
        retiringNativeAdView?.let(::removeView)
        retiringNativeAdView = null
        hide()
    }

    fun isFullyVisibleForRefresh(): Boolean {
        if (
            !isAttachedToWindow || !isShown || alpha < 1f || !hasWindowFocus() ||
            width <= 0 || height <= 0
        ) {
            return false
        }
        val visibleRect = Rect()
        return getGlobalVisibleRect(visibleRect) &&
                visibleRect.width() >= width && visibleRect.height() >= height
    }

    fun closeFullScreen() {
        check(format == NativeAdFormat.FULL) {
            "closeFullScreen is only valid for the FULL native ad format."
        }
        stateMachine.hide()
        renderHidden()
        notifyStateChanged()
        dispatchFullScreenExit(NativeFullAdExitReason.USER_CLOSED)
    }

    @VisibleForTesting
    internal fun showLoadingForTesting() {
        stateMachine.startLoading()
        fullScreenExitDispatcher.reset()
        renderLoading()
        notifyStateChanged()
    }

    @VisibleForTesting
    internal fun showLoadedForTesting() {
        val requestToken = stateMachine.startLoading()
        stateMachine.completeLoading(requestToken, wasSuccessful = true)
        renderLoaded()
        notifyStateChanged()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val cardHeight = format.heightResId?.let(resources::getDimensionPixelSize)
        if (cardHeight == null) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }
        val exactHeightSpec = MeasureSpec.makeMeasureSpec(cardHeight, MeasureSpec.EXACTLY)
        super.onMeasure(widthMeasureSpec, exactHeightSpec)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        when (state) {
            NativeAdHostState.LOADING -> renderLoading()
            NativeAdHostState.LOADED -> {
                skeletonView.animate().cancel()
                skeletonView.stopShimmer()
                skeletonView.isVisible = false
                skeletonView.alpha = 0f
                nativeAdView.animate().cancel()
                nativeAdView.isVisible = true
                nativeAdView.alpha = 1f
                isVisible = true
                closeButton?.isVisible = true
            }

            NativeAdHostState.HIDDEN -> Unit
        }
        if (format == NativeAdFormat.FULL) {
            ViewCompat.requestApplyInsets(this)
        }
    }

    override fun onDetachedFromWindow() {
        nativeAdView.animate().cancel()
        skeletonView.animate().cancel()
        skeletonView.stopShimmer()
        // Activity windows can temporarily detach their view tree while the app is in the
        // background. The coordinator owns the NativeAd until the LifecycleOwner is destroyed,
        // so releasing it here leaves a live session with an empty host after foreground return.
        super.onDetachedFromWindow()
    }

    private fun createCloseButton(): AppCompatImageButton = AppCompatImageButton(context).apply {
        id = R.id.native_ad_close
        background = AppCompatResources.getDrawable(context, R.drawable.bg_native_ad_close)
        setImageDrawable(AppCompatResources.getDrawable(context, R.drawable.ic_native_ad_close))
        contentDescription = context.getString(R.string.native_ad_close)
        imageTintList = null
        isFocusable = true
        setPadding(dp(12), dp(12), dp(12), dp(12))
        setOnClickListener { closeFullScreen() }
        layoutParams = LayoutParams(
            resources.getDimensionPixelSize(R.dimen.native_ad_close_touch_size),
            resources.getDimensionPixelSize(R.dimen.native_ad_close_touch_size),
            Gravity.TOP or Gravity.END,
        ).apply {
            topMargin = dp(8)
            marginEnd = dp(8)
        }
    }

    private fun applySystemBarInsets() {
        val initialLeft = paddingLeft
        val initialTop = paddingTop
        val initialRight = paddingRight
        val initialBottom = paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                initialLeft + systemBars.left,
                initialTop + systemBars.top,
                initialRight + systemBars.right,
                initialBottom + systemBars.bottom,
            )
            insets
        }
    }

    private fun dispatchLoadResult(requestToken: Long, wasSuccessful: Boolean) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            handleLoadResult(requestToken, wasSuccessful)
        } else {
            mainHandler.post { handleLoadResult(requestToken, wasSuccessful) }
        }
    }

    private fun handleLoadResult(requestToken: Long, wasSuccessful: Boolean) {
        val nextState = stateMachine.completeLoading(requestToken, wasSuccessful) ?: return
        if (nextState == NativeAdHostState.LOADED) {
            renderLoaded()
        } else {
            renderHidden()
        }
        notifyStateChanged()
        if (!wasSuccessful && format == NativeAdFormat.FULL) {
            dispatchFullScreenExit(NativeFullAdExitReason.LOAD_FAILED)
        }
    }

    private fun renderLoading() {
        nativeAdView.animate().cancel()
        retiringNativeAdView?.animate()?.cancel()
        retiringNativeAdView?.visibility = View.GONE
        skeletonView.animate().cancel()
        isVisible = true
        nativeAdView.visibility = View.INVISIBLE
        nativeAdView.alpha = 1f
        skeletonView.alpha = 1f
        skeletonView.isVisible = true
        closeButton?.isVisible = true
    }

    private fun crossfadeCoordinatorReplacement(previousAd: NativeAd, replacementAd: NativeAd) {
        val previousView = nativeAdView
        val replacementView = KiroNativeAdView(context, format.layoutResId).apply {
            alpha = if (animationsEnabled()) 0f else 1f
            setNativeAd(replacementAd)
        }
        addView(
            replacementView,
            indexOfChild(previousView) + 1,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
        coordinatorOwnedAd = replacementAd
        retiringCoordinatorAd = previousAd
        retiringNativeAdView = previousView
        nativeAdView = replacementView
        isVisible = true
        closeButton?.isVisible = true

        val token = stateMachine.startLoading()
        stateMachine.completeLoading(token, wasSuccessful = true)
        notifyStateChanged()
        if (!animationsEnabled()) {
            finishCoordinatorReplacement(previousView, previousAd)
            return
        }
        replacementView.animate()
            .alpha(1f)
            .setDuration(CROSSFADE_DURATION_MILLIS)
            .start()
        previousView.animate()
            .alpha(0f)
            .setDuration(CROSSFADE_DURATION_MILLIS)
            .withEndAction { finishCoordinatorReplacement(previousView, previousAd) }
            .start()
    }

    private fun finishCoordinatorReplacement(previousView: KiroNativeAdView, previousAd: NativeAd) {
        if (previousView.parent === this) removeView(previousView)
        previousAd.destroy()
        if (retiringNativeAdView === previousView) retiringNativeAdView = null
        if (retiringCoordinatorAd === previousAd) retiringCoordinatorAd = null
    }

    private fun renderLoaded() {
        isVisible = true
        nativeAdView.isVisible = true
        closeButton?.isVisible = true
        if (!animationsEnabled()) {
            skeletonView.isVisible = false
            nativeAdView.alpha = 1f
            return
        }

        nativeAdView.alpha = 0f
        nativeAdView.animate()
            .alpha(1f)
            .setDuration(CROSSFADE_DURATION_MILLIS)
            .start()
        skeletonView.animate()
            .alpha(0f)
            .setDuration(CROSSFADE_DURATION_MILLIS)
            .withEndAction {
                skeletonView.isVisible = false
                skeletonView.alpha = 1f
            }
            .start()
    }

    private fun renderHidden() {
        nativeAdView.animate().cancel()
        retiringNativeAdView?.animate()?.cancel()
        skeletonView.animate().cancel()
        skeletonView.stopShimmer()
        nativeAdView.alpha = 1f
        skeletonView.alpha = 1f
        nativeAdView.visibility = View.GONE
        retiringNativeAdView?.visibility = View.GONE
        skeletonView.visibility = View.GONE
        closeButton?.visibility = View.GONE
        visibility = View.GONE
    }

    private fun dispatchFullScreenExit(reason: NativeFullAdExitReason) {
        fullScreenExitDispatcher.dispatch(reason, onFullScreenExit)
    }

    private fun notifyStateChanged() {
        onStateChanged?.invoke(stateMachine.state)
    }

    private fun animationsEnabled(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val TAG = "NativeAdHostView"
        const val FORMAT_MEDIUM = 0
        const val FORMAT_LARGE = 1
        const val FORMAT_FULL = 2
        const val CROSSFADE_DURATION_MILLIS = 150L
    }
}
