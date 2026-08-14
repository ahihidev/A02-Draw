package com.a02.draw.core.ui.extensions

import android.annotation.SuppressLint
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding

fun View.applySystemBarsPadding(includeTop: Boolean = true) {
    val initialPadding = Rect(paddingLeft, paddingTop, paddingRight, paddingBottom)
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        view.updatePadding(
            left = initialPadding.left + bars.left,
            top = initialPadding.top + if (includeTop) bars.top else 0,
            right = initialPadding.right + bars.right,
            bottom = initialPadding.bottom + bars.bottom,
        )
        insets
    }
    requestApplyInsetsWhenAttached()
}

fun View.applyStatusBarPadding(lightStatusBarIcons: Boolean = true) {
    val initialPadding = Rect(paddingLeft, paddingTop, paddingRight, paddingBottom)
    val initialHeight = layoutParams?.height ?: 0
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        ViewCompat.getWindowInsetsController(view)?.isAppearanceLightStatusBars =
            lightStatusBarIcons
        val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
        view.updatePadding(
            left = initialPadding.left,
            top = initialPadding.top + statusBar.top,
            right = initialPadding.right,
            bottom = initialPadding.bottom,
        )
        if (initialHeight > 0) {
            view.layoutParams = view.layoutParams.apply { height = initialHeight + statusBar.top }
        }
        insets
    }
    requestApplyInsetsWhenAttached()
}

fun View.applyStatusBarHeight(lightStatusBarIcons: Boolean = true) {
    val initialHeight = layoutParams?.height?.coerceAtLeast(0) ?: 0
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        ViewCompat.getWindowInsetsController(view)?.isAppearanceLightStatusBars =
            lightStatusBarIcons
        val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
        view.updateLayoutParams { height = initialHeight + statusBar.top }
        insets
    }
    requestApplyInsetsWhenAttached()
}

@SuppressLint("ClickableViewAccessibility")
fun View.setDebouncedClickListener(intervalMillis: Long = 500L, action: (View) -> Unit) {
    var lastClickAt = 0L
    val restingScaleX = scaleX
    val restingScaleY = scaleY
    setOnTouchListener { view, event ->
        if (!view.isEnabled) return@setOnTouchListener false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> view.animate()
                .scaleX(restingScaleX * PRESSED_SCALE)
                .scaleY(restingScaleY * PRESSED_SCALE)
                .setDuration(PRESS_IN_DURATION_MILLIS)
                .setInterpolator(PRESS_INTERPOLATOR)
                .start()

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL,
                -> view.animate()
                .scaleX(restingScaleX)
                .scaleY(restingScaleY)
                .setDuration(PRESS_OUT_DURATION_MILLIS)
                .setInterpolator(PRESS_INTERPOLATOR)
                .start()
        }
        false
    }
    setOnClickListener { view ->
        val now = SystemClock.elapsedRealtime()
        if (now - lastClickAt >= intervalMillis) {
            lastClickAt = now
            action(view)
        }
    }
}

private const val PRESSED_SCALE = 0.965f
private const val PRESS_IN_DURATION_MILLIS = 70L
private const val PRESS_OUT_DURATION_MILLIS = 180L
private val PRESS_INTERPOLATOR = DecelerateInterpolator(1.8f)

private fun View.requestApplyInsetsWhenAttached() {
    if (isAttachedToWindow) {
        ViewCompat.requestApplyInsets(this)
    } else {
        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                view.removeOnAttachStateChangeListener(this)
                ViewCompat.requestApplyInsets(view)
            }

            override fun onViewDetachedFromWindow(view: View) = Unit
        })
    }
}
