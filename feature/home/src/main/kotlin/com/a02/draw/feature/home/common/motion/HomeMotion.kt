package com.a02.draw.feature.home.common.motion

import android.animation.ValueAnimator
import android.os.Build
import android.view.View
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.recyclerview.widget.RecyclerView

internal object HomeMotion {
    const val MICRO = 140L
    const val CONTENT = 300L
    const val PANEL = 280L
    const val STAGGER = 45L

    fun enabled(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            ValueAnimator.areAnimatorsEnabled()
}

internal fun View.dp(value: Float): Float = value * resources.displayMetrics.density

internal fun View.cancelMotion() {
    animate().cancel()
    clearAnimation()
}

internal fun View.pulse(scale: Float = 1.08f, rotationBy: Float = 0f) {
    cancelMotion()
    if (!HomeMotion.enabled()) {
        scaleX = 1f
        scaleY = 1f
        rotation = 0f
        return
    }
    animate()
        .scaleX(scale)
        .scaleY(scale)
        .rotationBy(rotationBy)
        .setDuration(HomeMotion.MICRO / 2)
        .withEndAction {
            animate()
                .scaleX(1f)
                .scaleY(1f)
                .rotation(0f)
                .setDuration(HomeMotion.MICRO / 2)
                .start()
        }
        .start()
}

internal fun View.crossfadeVisible(visible: Boolean, duration: Long = HomeMotion.CONTENT) {
    cancelMotion()
    if (!HomeMotion.enabled()) {
        alpha = 1f
        isVisible = visible
        return
    }
    if (visible) {
        if (isVisible && alpha == 1f) return
        if (!isVisible) {
            alpha = 0f
            isVisible = true
        }
        animate().alpha(1f).setDuration(duration).start()
    } else if (isVisible) {
        animate().alpha(0f).setDuration(duration).withEndAction {
            isVisible = false
            alpha = 1f
        }.start()
    }
}

internal fun View.slideFadeVisible(
    visible: Boolean,
    fromBottomDp: Float = 18f,
    duration: Long = HomeMotion.PANEL,
) {
    cancelMotion()
    if (!HomeMotion.enabled()) {
        alpha = 1f
        translationY = 0f
        isVisible = visible
        return
    }
    if (visible) {
        if (isVisible && alpha == 1f && translationY == 0f) return
        if (!isVisible) {
            alpha = 0f
            translationY = dp(fromBottomDp)
            isVisible = true
        }
        animate().alpha(1f).translationY(0f).setDuration(duration)
            .setInterpolator(FastOutSlowInInterpolator()).start()
    } else if (isVisible) {
        animate().alpha(0f).translationY(dp(fromBottomDp)).setDuration(duration)
            .setInterpolator(FastOutSlowInInterpolator()).withEndAction {
                isVisible = false
                alpha = 1f
                translationY = 0f
            }.start()
    }
}

internal fun playStaggeredEntrance(
    views: List<View>,
    horizontalDirections: List<Int> = emptyList(),
    verticalDp: Float = 12f,
) {
    if (!HomeMotion.enabled()) {
        views.forEach { it.alpha = 1f; it.translationX = 0f; it.translationY = 0f }
        return
    }
    views.forEachIndexed { index, view ->
        view.cancelMotion()
        view.alpha = 0f
        view.translationX = view.dp(16f) * horizontalDirections.getOrElse(index) { 0 }
        view.translationY = view.dp(verticalDp)
        view.doOnPreDraw {
            view.animate().alpha(1f).translationX(0f).translationY(0f)
                .setStartDelay(index * HomeMotion.STAGGER)
                .setDuration(HomeMotion.CONTENT)
                .setInterpolator(FastOutSlowInInterpolator()).start()
        }
    }
}

internal fun View.enterFromBottom(delay: Long = 0L, distanceDp: Float = 18f) {
    cancelMotion()
    if (!HomeMotion.enabled()) {
        alpha = 1f
        translationY = 0f
        return
    }
    alpha = 0f
    translationY = dp(distanceDp)
    doOnPreDraw {
        animate().alpha(1f).translationY(0f).setStartDelay(delay)
            .setDuration(HomeMotion.CONTENT).setInterpolator(FastOutSlowInInterpolator()).start()
    }
}

internal fun View.renderSelectedCard(selected: Boolean) {
    cancelMotion()
    isSelected = selected
    val targetScale = if (selected) 1f else 0.97f
    val targetElevation = if (selected) dp(6f) else dp(1f)
    if (!HomeMotion.enabled()) {
        scaleX = targetScale
        scaleY = targetScale
        elevation = targetElevation
        return
    }
    if (selected) {
        scaleX = 0.97f
        scaleY = 0.97f
    }
    animate().scaleX(targetScale).scaleY(targetScale).translationY(if (selected) -dp(2f) else 0f)
        .setDuration(HomeMotion.MICRO).setInterpolator(FastOutSlowInInterpolator())
        .withStartAction { elevation = targetElevation }.start()
}

internal fun RecyclerView.animateFirstVisibleItems() {
    if (!HomeMotion.enabled()) return
    doOnPreDraw {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            child.cancelMotion()
            child.alpha = 0f
            child.translationY = child.dp(14f)
            child.animate().alpha(1f).translationY(0f)
                .setStartDelay(index * HomeMotion.STAGGER)
                .setDuration(HomeMotion.CONTENT)
                .setInterpolator(FastOutSlowInInterpolator()).start()
        }
    }
}
