package com.a02.draw.core.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.content.res.use
import androidx.core.view.isVisible
import com.a02.draw.core.ui.R
import kotlin.math.ceil

class ApiSkeletonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {
    enum class Mode { LIST, GRID }

    var skeletonMode: Mode = Mode.LIST
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
            invalidate()
        }

    var skeletonItemCount: Int = DEFAULT_ITEM_COUNT
        set(value) {
            val next = value.coerceAtLeast(1)
            if (field == next) return
            field = next
            requestLayout()
            invalidate()
        }

    var skeletonGridColumnCount: Int = ADAPTIVE_GRID_COLUMN_COUNT
        set(value) {
            val next = value.coerceAtLeast(ADAPTIVE_GRID_COLUMN_COUNT)
            if (field == next) return
            field = next
            requestLayout()
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val surfacePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SKELETON_SURFACE }
    private val bonePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shimmerMatrix = Matrix()
    private var shimmerGradient: LinearGradient? = null
    private var shimmerOffset = -1f
    private val shimmerAnimator = ValueAnimator.ofFloat(-1f, 1f).apply {
        duration = SHIMMER_DURATION_MILLIS
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
            shimmerOffset = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        context.obtainStyledAttributes(attrs, R.styleable.ApiSkeletonView).use { values ->
            skeletonMode = if (
                values.getInt(R.styleable.ApiSkeletonView_skeletonMode, MODE_LIST) == MODE_GRID
            ) {
                Mode.GRID
            } else {
                Mode.LIST
            }
            skeletonItemCount = values.getInt(
                R.styleable.ApiSkeletonView_skeletonItemCount,
                DEFAULT_ITEM_COUNT,
            )
            skeletonGridColumnCount = values.getInt(
                R.styleable.ApiSkeletonView_skeletonGridColumnCount,
                ADAPTIVE_GRID_COLUMN_COUNT,
            )
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val fallbackWidth = dp(DEFAULT_WIDTH_DP)
        val measuredWidth = resolveSize(fallbackWidth, widthMeasureSpec)
        val contentWidth = (measuredWidth - paddingLeft - paddingRight).coerceAtLeast(0)
        val desiredHeight = paddingTop + contentHeight(contentWidth) + paddingBottom
        setMeasuredDimension(measuredWidth, resolveSize(desiredHeight, heightMeasureSpec))
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (width <= 0) return
        shimmerGradient = LinearGradient(
            -width * SHIMMER_WIDTH_RATIO,
            0f,
            width * SHIMMER_WIDTH_RATIO,
            0f,
            intArrayOf(SKELETON_BASE, SKELETON_HIGHLIGHT, SKELETON_BASE),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
        bonePaint.shader = shimmerGradient
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val gradient = shimmerGradient ?: return
        shimmerMatrix.setTranslate(shimmerOffset * width, 0f)
        gradient.setLocalMatrix(shimmerMatrix)
        when (skeletonMode) {
            Mode.LIST -> drawList(canvas)
            Mode.GRID -> drawGrid(canvas)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateAnimation()
    }

    override fun onDetachedFromWindow() {
        shimmerAnimator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        updateAnimation()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        updateAnimation()
    }

    private fun updateAnimation() {
        val shouldAnimate = isAttachedToWindow && isVisible && windowVisibility == VISIBLE
        if (shouldAnimate && !shimmerAnimator.isStarted) {
            shimmerAnimator.start()
        } else if (!shouldAnimate && shimmerAnimator.isStarted) {
            shimmerAnimator.cancel()
        }
    }

    private fun drawList(canvas: Canvas) {
        val inset = dp(LIST_HORIZONTAL_INSET_DP).toFloat()
        val itemHeight = dp(LIST_ITEM_HEIGHT_DP).toFloat()
        val spacing = dp(LIST_ITEM_SPACING_DP).toFloat()
        val radius = dp(CARD_RADIUS_DP).toFloat()
        val imageSize = dp(LIST_IMAGE_SIZE_DP).toFloat()
        var top = paddingTop + dp(CONTENT_TOP_INSET_DP).toFloat()
        repeat(skeletonItemCount) {
            if (top >= height) return
            val card = RectF(inset, top, width - inset, top + itemHeight)
            canvas.drawRoundRect(card, radius, radius, surfacePaint)
            val imageLeft = card.left + dp(CARD_CONTENT_INSET_DP)
            val imageTop = card.top + (itemHeight - imageSize) / 2f
            canvas.drawRoundRect(
                RectF(imageLeft, imageTop, imageLeft + imageSize, imageTop + imageSize),
                dp(IMAGE_RADIUS_DP).toFloat(),
                dp(IMAGE_RADIUS_DP).toFloat(),
                bonePaint,
            )
            val textLeft = imageLeft + imageSize + dp(LIST_TEXT_GAP_DP)
            drawBone(canvas, textLeft, card.top + dp(15), card.right - dp(18), dp(13))
            drawBone(canvas, textLeft, card.top + dp(37), card.right - dp(72), dp(9))
            drawBone(canvas, textLeft, card.top + dp(55), card.right - dp(112), dp(9))
            top += itemHeight + spacing
        }
    }

    private fun drawGrid(canvas: Canvas) {
        val inset = dp(GRID_HORIZONTAL_INSET_DP).toFloat()
        val gap = dp(GRID_GAP_DP).toFloat()
        val columns = gridColumnCount(width - paddingLeft - paddingRight)
        val totalGap = gap * (columns - 1)
        val cardWidth = ((width - inset * 2f - totalGap) / columns).coerceAtLeast(0f)
        val cardHeight = cardWidth + dp(GRID_FOOTER_HEIGHT_DP)
        val radius = dp(CARD_RADIUS_DP).toFloat()
        val imageInset = dp(CARD_CONTENT_INSET_DP).toFloat()
        val imageSize = (cardWidth - imageInset * 2f).coerceAtLeast(0f)
        repeat(skeletonItemCount) { index ->
            val column = index % columns
            val row = index / columns
            val left = inset + column * (cardWidth + gap)
            val top = paddingTop + dp(CONTENT_TOP_INSET_DP) + row * (cardHeight + gap)
            if (top >= height) return
            val card = RectF(left, top, left + cardWidth, top + cardHeight)
            canvas.drawRoundRect(card, radius, radius, surfacePaint)
            canvas.drawRoundRect(
                RectF(
                    card.left + imageInset,
                    card.top + imageInset,
                    card.left + imageInset + imageSize,
                    card.top + imageInset + imageSize,
                ),
                dp(IMAGE_RADIUS_DP).toFloat(),
                dp(IMAGE_RADIUS_DP).toFloat(),
                bonePaint,
            )
            val barWidth = cardWidth * GRID_TITLE_WIDTH_RATIO
            val barLeft = card.centerX() - barWidth / 2f
            drawBone(
                canvas,
                barLeft,
                card.bottom - dp(24),
                barLeft + barWidth,
                dp(11),
            )
        }
    }

    private fun gridColumnCount(contentWidth: Int): Int {
        if (skeletonGridColumnCount > ADAPTIVE_GRID_COLUMN_COUNT) {
            return skeletonGridColumnCount
        }
        val horizontalInsets = dp(GRID_HORIZONTAL_INSET_DP * 2)
        val usableWidth = (contentWidth - horizontalInsets).coerceAtLeast(0)
        return maxOf(MINIMUM_GRID_COLUMNS, usableWidth / dp(GRID_MINIMUM_CELL_WIDTH_DP))
    }

    private fun drawBone(canvas: Canvas, left: Float, top: Float, right: Float, height: Int) {
        val safeRight = right.coerceAtLeast(left)
        val radius = height / 2f
        canvas.drawRoundRect(RectF(left, top, safeRight, top + height), radius, radius, bonePaint)
    }

    private fun contentHeight(contentWidth: Int): Int = when (skeletonMode) {
        Mode.LIST -> {
            dp(CONTENT_TOP_INSET_DP * 2) +
                    skeletonItemCount * dp(LIST_ITEM_HEIGHT_DP) +
                    (skeletonItemCount - 1) * dp(LIST_ITEM_SPACING_DP)
        }

        Mode.GRID -> {
            val columns = gridColumnCount(contentWidth)
            val gaps = dp(GRID_GAP_DP) * (columns - 1)
            val availableWidth = contentWidth - dp(GRID_HORIZONTAL_INSET_DP * 2) - gaps
            val cardWidth = (availableWidth / columns).coerceAtLeast(0)
            val cardHeight = cardWidth + dp(GRID_FOOTER_HEIGHT_DP)
            val rows = ceil(skeletonItemCount / columns.toFloat()).toInt()
            dp(CONTENT_TOP_INSET_DP * 2) + rows * cardHeight + (rows - 1) * dp(GRID_GAP_DP)
        }
    }

    private fun dp(value: Int): Int = (value * density).toInt()

    private companion object {
        const val MODE_LIST = 0
        const val MODE_GRID = 1
        const val DEFAULT_ITEM_COUNT = 6
        const val DEFAULT_WIDTH_DP = 360
        const val ADAPTIVE_GRID_COLUMN_COUNT = 0
        const val MINIMUM_GRID_COLUMNS = 2
        const val GRID_MINIMUM_CELL_WIDTH_DP = 156
        const val CONTENT_TOP_INSET_DP = 8
        const val CARD_RADIUS_DP = 12
        const val CARD_CONTENT_INSET_DP = 8
        const val IMAGE_RADIUS_DP = 9
        const val LIST_HORIZONTAL_INSET_DP = 16
        const val LIST_ITEM_HEIGHT_DP = 76
        const val LIST_ITEM_SPACING_DP = 8
        const val LIST_IMAGE_SIZE_DP = 58
        const val LIST_TEXT_GAP_DP = 12
        const val GRID_HORIZONTAL_INSET_DP = 10
        const val GRID_GAP_DP = 12
        const val GRID_FOOTER_HEIGHT_DP = 40
        const val SHIMMER_DURATION_MILLIS = 1_100L
        const val SHIMMER_WIDTH_RATIO = 0.65f
        const val GRID_TITLE_WIDTH_RATIO = 0.55f
        const val SKELETON_SURFACE = 0xFFF7F8FB.toInt()
        const val SKELETON_BASE = 0xFFE3E8EF.toInt()
        const val SKELETON_HIGHLIGHT = 0xFFF4F6F9.toInt()
    }
}
