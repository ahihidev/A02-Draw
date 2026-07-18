package com.a02.draw.core.ui.extensions

import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

fun View.applySystemBarsPadding() {
    val initialPadding = Rect(paddingLeft, paddingTop, paddingRight, paddingBottom)
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        view.updatePadding(
            left = initialPadding.left + bars.left,
            top = initialPadding.top + bars.top,
            right = initialPadding.right + bars.right,
            bottom = initialPadding.bottom + bars.bottom,
        )
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
