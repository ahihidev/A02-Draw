package com.a02.draw.feature.home

import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.view.View
import android.view.accessibility.AccessibilityEvent
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper
import kotlin.math.roundToInt

internal data class ArDrawAccessibilityTarget(
    val id: Int,
    val label: String,
    val bounds: RectF,
    val className: String = "android.widget.Button",
    val selected: Boolean = false,
    val checkable: Boolean = false,
    val checked: Boolean = false,
    val enabled: Boolean = true,
    val stateDescription: String? = null,
    val onClick: () -> Unit,
)

internal class ArDrawAccessibilityHelper(
    host: View,
    private val layoutTransform: () -> ArDrawLayoutTransform,
    private val targets: () -> List<ArDrawAccessibilityTarget>,
) : ExploreByTouchHelper(host) {
    override fun getVirtualViewAt(x: Float, y: Float): Int {
        val transform = layoutTransform()
        val logicalX = (x - transform.offsetX) / transform.scale
        val logicalY = (y - transform.offsetY) / transform.scale
        return targets().firstOrNull { it.enabled && it.bounds.contains(logicalX, logicalY) }?.id
            ?: INVALID_ID
    }

    override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) {
        targets().filter { it.enabled }.forEach { virtualViewIds += it.id }
    }

    override fun onPopulateNodeForVirtualView(
        virtualViewId: Int,
        node: AccessibilityNodeInfoCompat,
    ) {
        val target = targets().firstOrNull { it.id == virtualViewId }
            ?: error("Unknown accessibility target $virtualViewId")
        val transform = layoutTransform()
        val bounds = target.bounds
        node.setBoundsInParent(
            Rect(
                (transform.offsetX + bounds.left * transform.scale).roundToInt(),
                (transform.offsetY + bounds.top * transform.scale).roundToInt(),
                (transform.offsetX + bounds.right * transform.scale).roundToInt(),
                (transform.offsetY + bounds.bottom * transform.scale).roundToInt(),
            ),
        )
        node.contentDescription = target.label
        node.className = target.className
        node.isClickable = true
        node.isFocusable = true
        node.isEnabled = target.enabled
        node.isSelected = target.selected
        node.isCheckable = target.checkable
        node.isChecked = target.checked
        target.stateDescription?.let { node.stateDescription = it }
        node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
    }

    override fun onPopulateEventForVirtualView(virtualViewId: Int, event: AccessibilityEvent) {
        targets().firstOrNull { it.id == virtualViewId }
            ?.let { event.contentDescription = it.label }
    }

    override fun onPerformActionForVirtualView(
        virtualViewId: Int,
        action: Int,
        arguments: Bundle?,
    ): Boolean {
        if (action != AccessibilityNodeInfoCompat.ACTION_CLICK) return false
        val target = targets().firstOrNull { it.id == virtualViewId && it.enabled } ?: return false
        target.onClick()
        invalidateVirtualView(virtualViewId)
        sendEventForVirtualView(virtualViewId, AccessibilityEvent.TYPE_VIEW_CLICKED)
        return true
    }
}
