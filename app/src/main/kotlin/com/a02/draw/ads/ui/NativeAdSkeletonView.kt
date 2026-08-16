package com.a02.draw.ads.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.view.View
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.a02.draw.R

internal class NativeAdSkeletonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {
    var format: NativeAdFormat = NativeAdFormat.MEDIUM
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
            invalidate()
        }

    @get:VisibleForTesting
    internal val isShimmerRunning: Boolean
        get() = shimmerAnimator.isStarted

    private val density = resources.displayMetrics.density
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.native_ad_surface)
    }
    private val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.native_ad_media_placeholder)
    }
    private val bonePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shimmerMatrix = Matrix()
    private var shimmerGradient: LinearGradient? = null
    private var shimmerOffset = -1f
    private val shimmerAnimator = ValueAnimator.ofFloat(-1f, 1f).apply {
        duration = SHIMMER_DURATION_MILLIS
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener { animator ->
            shimmerOffset = animator.animatedValue as Float
            invalidate()
        }
    }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = dp(REFERENCE_WIDTH_DP)
        val measuredWidth = resolveSize(desiredWidth, widthMeasureSpec)
        val desiredHeight = when (format) {
            NativeAdFormat.MEDIUM -> resources.getDimensionPixelSize(R.dimen.native_ad_medium_height)
            NativeAdFormat.LARGE -> resources.getDimensionPixelSize(R.dimen.native_ad_large_height)
            NativeAdFormat.FULL -> resources.getDimensionPixelSize(R.dimen.native_ad_full_reference_height)
        }
        setMeasuredDimension(measuredWidth, resolveSize(desiredHeight, heightMeasureSpec))
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (width <= 0) return
        val base = ContextCompat.getColor(context, R.color.native_ad_skeleton_base)
        val highlight = ContextCompat.getColor(context, R.color.native_ad_skeleton_highlight)
        shimmerGradient = LinearGradient(
            -width * SHIMMER_WIDTH_RATIO,
            0f,
            width * SHIMMER_WIDTH_RATIO,
            0f,
            intArrayOf(base, highlight, base),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
        bonePaint.shader = shimmerGradient
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        backgroundPaint.color = ContextCompat.getColor(
            context,
            if (format == NativeAdFormat.FULL) {
                R.color.native_ad_full_surface
            } else {
                R.color.native_ad_surface
            },
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        val gradient = shimmerGradient ?: return
        shimmerMatrix.setTranslate(shimmerOffset * width, 0f)
        gradient.setLocalMatrix(shimmerMatrix)
        when (format) {
            NativeAdFormat.MEDIUM -> drawMedium(canvas)
            NativeAdFormat.LARGE -> drawLarge(canvas)
            NativeAdFormat.FULL -> drawFull(canvas)
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

    fun stopShimmer() {
        shimmerAnimator.cancel()
    }

    private fun updateAnimation() {
        val animationsEnabled = Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                ValueAnimator.areAnimatorsEnabled()
        val shouldAnimate = animationsEnabled && isAttachedToWindow && isVisible &&
                windowVisibility == VISIBLE
        if (shouldAnimate && !shimmerAnimator.isStarted) {
            shimmerAnimator.start()
        } else if (!shouldAnimate && shimmerAnimator.isStarted) {
            shimmerAnimator.cancel()
        }
    }

    private fun drawMedium(canvas: Canvas) {
        val horizontalPadding = dp(16).toFloat()
        val top = dp(10).toFloat()
        val innerRight = width - horizontalPadding
        drawBone(canvas, horizontalPadding, top, innerRight, top + dp(48), dp(24))

        val contentTop = top + dp(56)
        val contentBottom = contentTop + dp(120)
        val innerWidth = innerRight - horizontalPadding
        val mediaWidth = innerWidth * MEDIUM_MEDIA_WIDTH_RATIO
        canvas.drawRect(
            horizontalPadding,
            contentTop,
            horizontalPadding + mediaWidth,
            contentBottom,
            placeholderPaint,
        )

        val detailsLeft = horizontalPadding + mediaWidth + dp(8)
        drawBone(canvas, detailsLeft, contentTop, detailsLeft + dp(40), contentTop + dp(40), dp(8))
        val textLeft = detailsLeft + dp(48)
        drawBone(canvas, textLeft, contentTop + dp(4), innerRight, contentTop + dp(17), dp(6))
        drawBone(
            canvas,
            textLeft,
            contentTop + dp(23),
            innerRight - dp(24),
            contentTop + dp(36),
            dp(6)
        )
        drawBone(canvas, detailsLeft, contentTop + dp(52), innerRight, contentTop + dp(64), dp(6))
        drawBone(
            canvas,
            detailsLeft,
            contentTop + dp(72),
            innerRight - dp(12),
            contentTop + dp(84),
            dp(6)
        )
        drawBone(
            canvas,
            detailsLeft,
            contentTop + dp(92),
            innerRight - dp(40),
            contentTop + dp(104),
            dp(6)
        )
    }

    private fun drawLarge(canvas: Canvas) {
        val left = dp(16).toFloat()
        val right = width - left
        val top = dp(12).toFloat()
        canvas.drawRect(left, top, right, top + dp(142), placeholderPaint)

        val infoTop = top + dp(150)
        drawBone(canvas, left, infoTop, left + dp(40), infoTop + dp(40), dp(8))
        drawBone(canvas, left + dp(48), infoTop + dp(3), right, infoTop + dp(16), dp(6))
        drawBone(canvas, left + dp(48), infoTop + dp(24), right - dp(34), infoTop + dp(36), dp(6))

        val ctaTop = infoTop + dp(48)
        drawBone(canvas, left, ctaTop, right, ctaTop + dp(48), dp(24))
    }

    private fun drawFull(canvas: Canvas) {
        val panelHeight = dp(136).toFloat()
        val panelTop = height - panelHeight
        val left = dp(16).toFloat()
        val right = width - left
        canvas.drawRoundRect(
            RectF(left, dp(72).toFloat(), right, panelTop - dp(16)),
            dp(12).toFloat(),
            dp(12).toFloat(),
            placeholderPaint,
        )
        drawBone(canvas, left, dp(16).toFloat(), left + dp(40), dp(56).toFloat(), dp(8))

        canvas.drawRect(0f, panelTop, width.toFloat(), height.toFloat(), panelPaint)
        drawBone(canvas, left, panelTop + dp(12), right - dp(18), panelTop + dp(28), dp(7))
        drawBone(canvas, left, panelTop + dp(36), right, panelTop + dp(48), dp(6))
        drawBone(canvas, left, panelTop + dp(56), right - dp(34), panelTop + dp(68), dp(6))
        drawBone(canvas, left, panelTop + dp(76), right, panelTop + dp(124), dp(24))
    }

    private fun drawBone(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        radius: Int,
    ) {
        canvas.drawRoundRect(
            RectF(left, top, right, bottom),
            radius.toFloat(),
            radius.toFloat(),
            bonePaint
        )
    }

    private fun dp(value: Int): Int = (value * density).toInt()

    private companion object {
        const val REFERENCE_WIDTH_DP = 375
        const val SHIMMER_DURATION_MILLIS = 1_100L
        const val SHIMMER_WIDTH_RATIO = 0.65f
        const val MEDIUM_MEDIA_WIDTH_RATIO = 160f / 343f
    }
}
