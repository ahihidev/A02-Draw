package com.a02.draw.core.ui.extensions

import android.graphics.Rect
import android.os.SystemClock
import android.view.View
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

fun View.setDebouncedClickListener(intervalMillis: Long = 500L, action: (View) -> Unit) {
    var lastClickAt = 0L
    setOnClickListener { view ->
        val now = SystemClock.elapsedRealtime()
        if (now - lastClickAt >= intervalMillis) {
            lastClickAt = now
            action(view)
        }
    }
}

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
