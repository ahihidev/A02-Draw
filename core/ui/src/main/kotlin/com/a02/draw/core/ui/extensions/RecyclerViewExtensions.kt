package com.a02.draw.core.ui.extensions

import androidx.annotation.Px
import androidx.core.view.doOnLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

fun RecyclerView.setAdaptiveGridLayoutManager(
    @Px minimumItemWidth: Int,
    minimumSpanCount: Int = 1,
) {
    require(minimumItemWidth > 0) { "minimumItemWidth must be greater than zero" }
    require(minimumSpanCount > 0) { "minimumSpanCount must be greater than zero" }

    val gridLayoutManager = GridLayoutManager(context, minimumSpanCount)
    layoutManager = gridLayoutManager

    fun updateSpanCount(viewWidth: Int) {
        val availableWidth = (viewWidth - paddingStart - paddingEnd).coerceAtLeast(0)
        val spanCount = calculateAdaptiveSpanCount(
            availableWidth = availableWidth,
            minimumItemWidth = minimumItemWidth,
            minimumSpanCount = minimumSpanCount,
        )
        if (gridLayoutManager.spanCount != spanCount) {
            gridLayoutManager.spanCount = spanCount
        }
    }

    doOnLayout { recyclerView ->
        updateSpanCount(recyclerView.width)
        // RecyclerView restores LayoutManager state after the first layout callback.
        // Re-apply the adaptive count once that restoration has completed so a
        // rotation/window resize cannot bring back the previous screen's span count.
        recyclerView.post {
            if (recyclerView.isAttachedToWindow) updateSpanCount(recyclerView.width)
        }
    }
    addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
        val width = right - left
        if (width > 0 && width != oldRight - oldLeft) updateSpanCount(width)
    }
}

internal fun calculateAdaptiveSpanCount(
    availableWidth: Int,
    minimumItemWidth: Int,
    minimumSpanCount: Int,
): Int = maxOf(minimumSpanCount, availableWidth / minimumItemWidth)
