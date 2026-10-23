package com.a02.draw.ads.ui

import android.app.Activity
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Shows an interstitial with the host Activity in immersive mode, then restores the exact
 * ActionBar and system-bar state that was active before the ad started.
 */
internal fun Activity.showFullscreenInterstitial(
    showAd: (onFinished: () -> Unit) -> Unit,
    onFinished: (RuntimeException?) -> Unit,
) {
    val snapshot = InterstitialUiSnapshot.capture(this)
    val completed = AtomicBoolean(false)

    fun finishOnce(error: RuntimeException?) {
        if (!completed.compareAndSet(false, true)) return
        snapshot.restore()
        onFinished(error)
    }

    try {
        snapshot.enterFullscreen()
        showAd { finishOnce(null) }
    } catch (error: RuntimeException) {
        finishOnce(error)
    }
}

private class InterstitialUiSnapshot private constructor(
    activity: Activity,
    private val wasSupportActionBarVisible: Boolean?,
    private val wasFrameworkActionBarVisible: Boolean?,
    private val wasStatusBarVisible: Boolean,
    private val wasNavigationBarVisible: Boolean,
    private val previousSystemBarsBehavior: Int,
) {
    private val activityReference = WeakReference(activity)

    fun enterFullscreen() {
        val activity = activityReference.get() ?: return
        (activity as? AppCompatActivity)?.supportActionBar?.hide()
        activity.actionBar?.hide()
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    fun restore() {
        val activity = activityReference.get() ?: return
        if (activity.isFinishing || activity.isDestroyed) return

        (activity as? AppCompatActivity)?.supportActionBar?.let { actionBar ->
            when (wasSupportActionBarVisible) {
                true -> actionBar.show()
                false -> actionBar.hide()
                null -> Unit
            }
        }
        activity.actionBar?.let { actionBar ->
            when (wasFrameworkActionBarVisible) {
                true -> actionBar.show()
                false -> actionBar.hide()
                null -> Unit
            }
        }

        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            systemBarsBehavior = previousSystemBarsBehavior
            restoreVisibility(
                typeMask = WindowInsetsCompat.Type.statusBars(),
                wasVisible = wasStatusBarVisible,
            )
            restoreVisibility(
                typeMask = WindowInsetsCompat.Type.navigationBars(),
                wasVisible = wasNavigationBarVisible,
            )
        }
    }

    private fun WindowInsetsControllerCompat.restoreVisibility(
        typeMask: Int,
        wasVisible: Boolean,
    ) {
        if (wasVisible) show(typeMask) else hide(typeMask)
    }

    companion object {
        fun capture(activity: Activity): InterstitialUiSnapshot {
            val decorView = activity.window.decorView
            val insets = ViewCompat.getRootWindowInsets(decorView)
            val controller = WindowCompat.getInsetsController(activity.window, decorView)
            return InterstitialUiSnapshot(
                activity = activity,
                wasSupportActionBarVisible =
                    (activity as? AppCompatActivity)?.supportActionBar?.isShowing,
                wasFrameworkActionBarVisible = activity.actionBar?.isShowing,
                wasStatusBarVisible = insets?.isVisible(WindowInsetsCompat.Type.statusBars())
                    ?: true,
                wasNavigationBarVisible =
                    insets?.isVisible(WindowInsetsCompat.Type.navigationBars()) ?: true,
                previousSystemBarsBehavior = controller.systemBarsBehavior,
            )
        }
    }
}
