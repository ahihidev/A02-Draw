package com.a02.draw.feature.home.common.drawing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.annotation.DrawableRes
import androidx.core.graphics.scale
import androidx.core.graphics.withClip
import androidx.core.graphics.withRotation
import androidx.core.graphics.withScale
import androidx.core.graphics.withTranslation
import com.a02.draw.core.ui.image.SharedImageBitmapStore
import com.a02.draw.feature.home.R
import com.a02.draw.feature.home.common.model.DrawingCropRatio
import com.a02.draw.feature.home.common.model.DrawingMode
import com.a02.draw.feature.home.common.session.DrawingSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.hypot

data class OverlayPreview(
    val offsetX: Float,
    val offsetY: Float,
    val zoom: Float,
    val opacity: Float,
)

data class ArDrawLayoutTransform(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
)

data class OverlayTransform(
    val offsetX: Float,
    val offsetY: Float,
    val zoom: Float,
)

/**
 * The only custom-rendered UI in the feature. It owns the reference bitmap, guide grid and
 * multi-touch transform surface; all controls and content screens are regular XML views.
 */
class ArDrawView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val bitmapSource = Rect()
    private val bitmapDestination = RectF()
    private val clipRect = RectF()
    private val clipPath = Path()
    private val resourceBitmaps = mutableMapOf<Int, Bitmap>()
    private val contentBitmaps = mutableMapOf<String, Bitmap>()
    private val loadingKeys = mutableSetOf<String>()
    private val imageScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val removeLightBackgroundFilter = ColorMatrixColorFilter(
        floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            -1f, -1f, -1f, 3f, 0f,
        ),
    )

    private var state = DrawingSession()
    private var exportOnly = false
    private var cameraOverlayExternal = false
    private var transformActive = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var lastPinchDistance = 0f
    private var previewOffsetX = 0f
    private var previewOffsetY = 0f
    private var previewZoom = 1f

    var onTransformCommitted: (OverlayTransform) -> Unit = {}
    var onOverlayPreview: (OverlayPreview) -> Unit = {}

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        contentDescription = null
        setBackgroundColor(Color.WHITE)
        cacheResource(R.drawable.drawing_trace_overlay)
        cacheResource(R.drawable.drawing_camera_background)
    }

    fun render(newState: DrawingSession) {
        state = newState
        setBackgroundColor(
            if (newState.mode == DrawingMode.CAMERA) {
                Color.TRANSPARENT
            } else {
                Color.WHITE
            },
        )
        preloadReference(newState)
        onOverlayPreview(newState.toOverlayPreview())
        invalidate()
    }

    fun setCameraOverlayExternal(enabled: Boolean) {
        if (cameraOverlayExternal == enabled) return
        cameraOverlayExternal = enabled
        invalidate()
    }

    fun drawExport(canvas: Canvas) {
        val previous = exportOnly
        exportOnly = true
        try {
            draw(canvas)
        } finally {
            exportOnly = previous
        }
    }

    fun layoutTransform(): ArDrawLayoutTransform {
        val safeWidth = width.coerceAtLeast(1).toFloat()
        val scale = safeWidth / LOGICAL_WIDTH
        return ArDrawLayoutTransform(
            scale = scale,
            offsetX = 0f,
            offsetY = 0f,
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val transform = layoutTransform()
        canvas.withTranslation(transform.offsetX, transform.offsetY) {
            canvas.withScale(transform.scale, transform.scale) {
                drawDrawingSurface(canvas)
            }
        }
    }

    private fun drawDrawingSurface(canvas: Canvas) {
        if (state.mode == DrawingMode.SCREEN) {
            paint.color = Color.rgb(250, 250, 250)
            canvas.drawRect(0f, 0f, LOGICAL_WIDTH, LOGICAL_HEIGHT, paint)
        }
        if (state.overlayVisible && (!cameraOverlayExternal || exportOnly)) drawReference(canvas)
        if (!exportOnly) drawGrid(canvas)
    }

    private fun drawReference(canvas: Canvas) {
        val bitmap = resolveReferenceBitmap() ?: return
        val (overlayWidth, overlayHeight) = when (state.cropRatio) {
            DrawingCropRatio.PORTRAIT -> 184f to 328f
            DrawingCropRatio.LANDSCAPE -> 328f to 184f
            DrawingCropRatio.RESET,
            DrawingCropRatio.SQUARE,
                -> 328f to 328f
        }
        val offsetX = if (transformActive) previewOffsetX else state.overlayOffsetX
        val offsetY = if (transformActive) previewOffsetY else state.overlayOffsetY
        val zoom = if (transformActive) previewZoom else state.zoom
        canvas.withTranslation(OVERLAY_CENTER_X + offsetX, OVERLAY_CENTER_Y + offsetY) {
            canvas.withRotation(state.overlayRotationDegrees) {
                canvas.withScale(if (state.overlayFlipped) -zoom else zoom, zoom) {
                    drawBitmap(
                        canvas = canvas,
                        bitmap = bitmap,
                        x = -overlayWidth / 2f,
                        y = -overlayHeight / 2f,
                        width = overlayWidth,
                        height = overlayHeight,
                        corner = OVERLAY_CORNER,
                        alpha = state.opacity,
                        removeLightBackground = state.removeImageEnabled,
                    )
                }
            }
        }
    }

    private fun drawGrid(canvas: Canvas) {
        val gridSize = if (state.mode == DrawingMode.CAMERA && state.cameraGuideEnabled) {
            CAMERA_GUIDE_GRID_SIZE
        } else {
            state.gridSize
        }
        if (gridSize <= 0) return
        paint.color = Color.argb(125, 255, 255, 255)
        paint.strokeWidth = GRID_STROKE_WIDTH
        repeat(gridSize - 1) { index ->
            val fraction = (index + 1f) / gridSize
            val offset = GRID_EXTENT * fraction
            canvas.drawLine(
                GRID_LEFT + offset,
                GRID_TOP,
                GRID_LEFT + offset,
                GRID_TOP + GRID_EXTENT,
                paint
            )
            canvas.drawLine(
                GRID_LEFT,
                GRID_TOP + offset,
                GRID_LEFT + GRID_EXTENT,
                GRID_TOP + offset,
                paint
            )
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!state.overlayVisible || state.overlayLocked) {
            return false
        }
        val transform = layoutTransform()
        val logicalX = (event.x - transform.offsetX) / transform.scale
        val logicalY = (event.y - transform.offsetY) / transform.scale
        val insideGestureSurface = logicalY in GESTURE_TOP..GESTURE_BOTTOM
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!insideGestureSurface) return false
                lastTouchX = logicalX
                lastTouchY = logicalY
                lastPinchDistance = 0f
                transformActive = false
                previewOffsetX = state.overlayOffsetX
                previewOffsetY = state.overlayOffsetY
                previewZoom = state.zoom
            }

            MotionEvent.ACTION_POINTER_DOWN -> if (event.pointerCount >= 2) {
                lastPinchDistance = event.pointerDistance()
                transformActive = true
            }

            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2) {
                    val distance = event.pointerDistance()
                    if (lastPinchDistance > 0f && distance > 0f) {
                        previewZoom = (previewZoom * distance / lastPinchDistance)
                            .coerceIn(MIN_ZOOM, MAX_ZOOM)
                    }
                    lastPinchDistance = distance
                    transformActive = true
                } else {
                    previewOffsetX = (previewOffsetX + logicalX - lastTouchX)
                        .coerceIn(MIN_OFFSET_X, MAX_OFFSET_X)
                    previewOffsetY = (previewOffsetY + logicalY - lastTouchY)
                        .coerceIn(MIN_OFFSET_Y, MAX_OFFSET_Y)
                    lastTouchX = logicalX
                    lastTouchY = logicalY
                    transformActive = true
                }
                refreshPreview()
            }

            MotionEvent.ACTION_POINTER_UP -> {
                lastPinchDistance = 0f
                val remainingIndex = if (event.actionIndex == 0) 1 else 0
                if (remainingIndex < event.pointerCount) {
                    lastTouchX = (event.getX(remainingIndex) - transform.offsetX) / transform.scale
                    lastTouchY = (event.getY(remainingIndex) - transform.offsetY) / transform.scale
                }
            }

            MotionEvent.ACTION_UP -> {
                performClick()
                commitPreview()
            }

            MotionEvent.ACTION_CANCEL -> cancelPreview()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun commitPreview() {
        if (transformActive) {
            state = state.copy(
                overlayOffsetX = previewOffsetX,
                overlayOffsetY = previewOffsetY,
                zoom = previewZoom,
            )
            onTransformCommitted(
                OverlayTransform(
                    offsetX = previewOffsetX,
                    offsetY = previewOffsetY,
                    zoom = previewZoom,
                ),
            )
        }
        transformActive = false
        lastPinchDistance = 0f
    }

    private fun cancelPreview() {
        transformActive = false
        lastPinchDistance = 0f
        onOverlayPreview(state.toOverlayPreview())
        invalidate()
    }

    private fun refreshPreview() {
        onOverlayPreview(
            OverlayPreview(
                offsetX = previewOffsetX,
                offsetY = previewOffsetY,
                zoom = previewZoom,
                opacity = state.opacity,
            ),
        )
        if (!cameraOverlayExternal) invalidate()
    }

    private fun resolveReferenceBitmap(): Bitmap? {
        val pickedUri = state.pickedImageUri
        if (pickedUri != null) return contentBitmaps[pickedUri]
        val image = state.traceImage
        image?.url?.let { return contentBitmaps[it] }
        image?.localKey?.let(::localAssetDrawable)?.let { return resourceBitmaps[it] }
        return resourceBitmaps[R.drawable.drawing_trace_overlay]
    }

    private fun preloadReference(newState: DrawingSession) {
        val image = newState.traceImage
        newState.pickedImageUri?.let(::loadContentUri)
        image?.url?.let(::loadRemoteImage)
        image?.localKey?.let(::localAssetDrawable)?.let(::cacheResource)
        if (newState.pickedImageUri == null && image == null) {
            cacheResource(R.drawable.drawing_trace_overlay)
        }
    }

    private fun cacheResource(@DrawableRes resource: Int) {
        if (resource in resourceBitmaps) return
        BitmapFactory.decodeResource(resources, resource)?.let { decoded ->
            resourceBitmaps[resource] = decoded.constrained()
        }
    }

    private fun loadRemoteImage(url: String) {
        SharedImageBitmapStore.getRemote(url)?.let {
            contentBitmaps[url] = it
            return
        }
        if (url in contentBitmaps || !loadingKeys.add(url)) return
        imageScope.launch {
            val bitmap = SharedImageBitmapStore.loadRemote(url)
            withContext(Dispatchers.Main) {
                loadingKeys.remove(url)
                bitmap?.let { contentBitmaps[url] = it }
                invalidate()
            }
        }
    }

    private fun loadContentUri(uri: String) {
        SharedImageBitmapStore.getContentUri(uri)?.let {
            contentBitmaps[uri] = it
            return
        }
        if (uri in contentBitmaps || !loadingKeys.add(uri)) return
        imageScope.launch {
            val bitmap = SharedImageBitmapStore.loadContentUri(context.contentResolver, uri)
            withContext(Dispatchers.Main) {
                loadingKeys.remove(uri)
                bitmap?.let { contentBitmaps[uri] = it }
                invalidate()
            }
        }
    }

    private fun drawBitmap(
        canvas: Canvas,
        bitmap: Bitmap,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        corner: Float,
        alpha: Float = 1f,
        removeLightBackground: Boolean = false,
    ) {
        bitmapSource.set(0, 0, bitmap.width, bitmap.height)
        val sourceRatio = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1)
        val targetRatio = width / height
        val fittedWidth: Float
        val fittedHeight: Float
        if (sourceRatio > targetRatio) {
            fittedWidth = width
            fittedHeight = width / sourceRatio
        } else {
            fittedWidth = height * sourceRatio
            fittedHeight = height
        }
        val fittedLeft = x + (width - fittedWidth) / 2f
        val fittedTop = y + (height - fittedHeight) / 2f
        bitmapDestination.set(
            fittedLeft,
            fittedTop,
            fittedLeft + fittedWidth,
            fittedTop + fittedHeight,
        )
        paint.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
        paint.colorFilter = if (removeLightBackground) removeLightBackgroundFilter else null
        if (corner > 0f) {
            clipRect.set(bitmapDestination)
            clipPath.reset()
            clipPath.addRoundRect(clipRect, corner, corner, Path.Direction.CW)
            canvas.withClip(clipPath) {
                canvas.drawBitmap(bitmap, bitmapSource, bitmapDestination, paint)
            }
        } else {
            canvas.drawBitmap(bitmap, bitmapSource, bitmapDestination, paint)
        }
        paint.alpha = 255
        paint.colorFilter = null
    }

    private fun MotionEvent.pointerDistance(): Float {
        if (pointerCount < 2) return 0f
        return hypot(getX(0) - getX(1), getY(0) - getY(1))
    }

    private fun Bitmap.constrained(): Bitmap {
        val largestDimension = maxOf(width, height)
        if (largestDimension <= MAX_BITMAP_DIMENSION) return this
        val factor = MAX_BITMAP_DIMENSION.toFloat() / largestDimension
        val resized = scale(
            width = (width * factor).toInt().coerceAtLeast(1),
            height = (height * factor).toInt().coerceAtLeast(1),
        )
        if (resized !== this) recycle()
        return resized
    }

    @DrawableRes
    private fun localAssetDrawable(key: String): Int = when (key) {
        "topic_chibi" -> R.drawable.figma_topic_chibi
        "topic_pixel" -> R.drawable.figma_topic_pixel
        "topic_anime" -> R.drawable.figma_topic_anime
        "topic_cartoon" -> R.drawable.figma_topic_cartoon
        "topic_world_cup" -> R.drawable.figma_topic_world_cup
        "topic_bricks", "topic_lego" -> R.drawable.figma_topic_lego
        "topic_animal" -> R.drawable.topic_animal
        "topic_flower" -> R.drawable.topic_flower
        "topic_kids" -> R.drawable.topic_kids
        "drawing_trace_overlay" -> R.drawable.drawing_trace_overlay
        else -> R.drawable.drawing_trace_overlay
    }

    override fun onDetachedFromWindow() {
        imageScope.cancel()
        resourceBitmaps.values.forEach { if (!it.isRecycled) it.recycle() }
        resourceBitmaps.clear()
        contentBitmaps.clear()
        super.onDetachedFromWindow()
    }

    private fun DrawingSession.toOverlayPreview() = OverlayPreview(
        offsetX = overlayOffsetX,
        offsetY = overlayOffsetY,
        zoom = zoom,
        opacity = opacity,
    )

    private companion object {
        const val LOGICAL_WIDTH = 360f
        const val LOGICAL_HEIGHT = 800f
        const val OVERLAY_CENTER_X = 180f
        const val OVERLAY_CENTER_Y = 358f
        const val OVERLAY_CORNER = 16f
        const val GRID_LEFT = 16f
        const val GRID_TOP = 194f
        const val GRID_EXTENT = 328f
        const val GRID_STROKE_WIDTH = 0.8f
        const val GESTURE_TOP = 58f
        const val GESTURE_BOTTOM = 640f
        const val MIN_ZOOM = 0.5f
        const val MAX_ZOOM = 3f
        const val CAMERA_GUIDE_GRID_SIZE = 3
        const val MIN_OFFSET_X = -150f
        const val MAX_OFFSET_X = 150f
        const val MIN_OFFSET_Y = -220f
        const val MAX_OFFSET_Y = 220f
        const val MAX_BITMAP_DIMENSION = 1_024
    }
}
