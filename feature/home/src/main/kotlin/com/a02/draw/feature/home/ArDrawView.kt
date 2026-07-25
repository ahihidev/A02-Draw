package com.a02.draw.feature.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.OverScroller
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.scale
import androidx.core.graphics.toColorInt
import androidx.core.graphics.withClip
import androidx.core.graphics.withSave
import androidx.core.graphics.withScale
import androidx.core.graphics.withTranslation
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.a02.draw.domain.model.Artwork
import com.a02.draw.domain.model.ArtworkStyle
import com.a02.draw.domain.model.ContentImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

/**
 * Data-driven renderer for the complete AR Draw Figma flow. Only illustration artwork is bitmap
 * based; text, cards, lists, controls, state, selection, navigation and drawing tools are native
 * canvas elements backed by [HomeUiState]. Catalog text and imagery come from the repository;
 * bundled drawable keys are only the offline fallback for the same API contract.
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
    private val scratchPath = Path()
    private val headerGradient = LinearGradient(
        0f,
        0f,
        360f,
        180f,
        intArrayOf(Color.rgb(169, 74, 255), Color.rgb(22, 164, 244)),
        null,
        Shader.TileMode.CLAMP,
    )
    private val gallerySourceGradient = LinearGradient(
        16f, 219f, 117f, 219f,
        Color.rgb(37, 143, 246), Color.rgb(95, 211, 247), Shader.TileMode.CLAMP,
    )
    private val aiSourceGradient = LinearGradient(
        129f, 219f, 230f, 219f,
        Color.rgb(255, 82, 60), Color.rgb(255, 158, 37), Shader.TileMode.CLAMP,
    )
    private val webSourceGradient = LinearGradient(
        242f, 219f, 343f, 219f,
        Color.rgb(46, 180, 45), Color.rgb(83, 211, 66), Shader.TileMode.CLAMP,
    )
    private val settingsUpgradeGradient = LinearGradient(
        16f, 0f, 344f, 0f,
        Color.rgb(118, 75, 244), Color.rgb(35, 191, 174), Shader.TileMode.CLAMP,
    )
    private val removeLightBackgroundFilter = ColorMatrixColorFilter(
        floatArrayOf(
            0f, 0f, 0f, 0f, 22f,
            0f, 0f, 0f, 0f, 22f,
            0f, 0f, 0f, 0f, 22f,
            -0.333f, -0.333f, -0.333f, 0f, 255f,
        ),
    )
    private val bitmaps = LinkedHashMap<Int, Bitmap>(16, 0.75f, true)
    private val vectorDrawables = mutableMapOf<Int, Drawable>()
    private val contentBitmaps = LinkedHashMap<String, Bitmap>(16, 0.75f, true)
    private val loadingImages = mutableSetOf<String>()
    private val loadingDrawableResources = mutableSetOf<Int>()
    private var activeDrawableResources: Set<Int> = emptySet()
    private var bitmapCacheBytes = 0L
    private var contentBitmapCacheBytes = 0L
    private var activeContentImageKeys: Set<String> = emptySet()
    private val imageScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var state = HomeUiState()
    private var exportOnly = false
    private var cameraOverlayExternal = false
    private var exportFrameReady: (() -> Unit)? = null
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var lastPinchDistance = 0f
    private var transformingOverlay = false
    private var localTransformActive = false
    private var localOverlayOffsetX = 0f
    private var localOverlayOffsetY = 0f
    private var localOverlayZoom = 1f
    private var localOpacityActive = false
    private var localOpacity = 0.4f
    private val timeFormatter = SimpleDateFormat("H:mm", Locale.getDefault())
    private var cachedTimeMinute = Long.MIN_VALUE
    private var cachedTimeText = ""
    private var contentScrollOffset = 0f
    private var contentGestureActive = false
    private var tapGestureCancelled = false
    private var downTouchX = 0f
    private var downTouchY = 0f
    private var velocityTracker: VelocityTracker? = null
    private val contentScroller = OverScroller(context)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val accessibilityHelper = ArDrawAccessibilityHelper(
        host = this,
        layoutTransform = ::layoutTransform,
        targets = ::accessibilityTargets,
    )

    var onAction: (ArDrawAction) -> Unit = {}
    var onOverlayPreview: (OverlayPreview) -> Unit = {}

    init {
        setBackgroundColor(Color.WHITE)
        // The host itself is not an actionable accessibility item; its virtual descendants are.
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = null
        ViewCompat.setAccessibilityDelegate(this, accessibilityHelper)
        startupArtworkResources.forEach { resource ->
            cacheDrawable(
                resource,
                BitmapFactory.decodeResource(resources, resource).also(Bitmap::prepareToDraw)
            )
        }
    }

    fun render(newState: HomeUiState) {
        if (newState.screen != state.screen &&
            !(newState.screen == ArDrawScreen.FILTER && state.screen == ArDrawScreen.GALLERY) &&
            !(newState.screen == ArDrawScreen.GALLERY && state.screen == ArDrawScreen.FILTER) &&
            !(newState.screen == ArDrawScreen.HOME_SOURCE_MODAL && state.screen == ArDrawScreen.HOME) &&
            !(newState.screen == ArDrawScreen.HOME && state.screen == ArDrawScreen.HOME_SOURCE_MODAL)
        ) {
            contentScrollOffset = 0f
            contentScroller.abortAnimation()
        }
        state = newState
        setBackgroundColor(
            if (newState.screen.isDrawingScreen() &&
                newState.drawingWithCamera &&
                newState.cameraPermissionGranted
            ) {
                Color.TRANSPARENT
            } else {
                Color.WHITE
            },
        )
        preloadDrawableResources(newState)
        preloadContentImages(newState)
        accessibilityHelper.invalidateRoot()
        invalidate()
    }

    fun setExportOnly(enabled: Boolean) {
        exportOnly = enabled
        invalidate()
    }

    fun setCameraOverlayExternal(enabled: Boolean) {
        if (cameraOverlayExternal == enabled) return
        cameraOverlayExternal = enabled
        invalidate()
    }

    fun prepareExport(onFrameReady: () -> Unit) {
        exportOnly = true
        exportFrameReady = onFrameReady
        invalidate()
    }

    fun layoutTransform(): ArDrawLayoutTransform {
        val safeWidth = width.coerceAtLeast(1).toFloat()
        val safeHeight = height.coerceAtLeast(1).toFloat()
        val scale = kotlin.math.min(safeWidth / LOGICAL_WIDTH, safeHeight / LOGICAL_HEIGHT)
        return ArDrawLayoutTransform(
            scale = scale,
            offsetX = (safeWidth - LOGICAL_WIDTH * scale) / 2f,
            offsetY = (safeHeight - LOGICAL_HEIGHT * scale) / 2f,
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val transform = layoutTransform()
        canvas.withTranslation(transform.offsetX, transform.offsetY) {
            canvas.withScale(transform.scale, transform.scale) {
                when (state.screen) {
                    ArDrawScreen.ONBOARDING_PROJECTOR,
                    ArDrawScreen.ONBOARDING_LIGHTBOX,
                    ArDrawScreen.ONBOARDING_LESSONS,
                        -> drawOnboarding(canvas)

                    ArDrawScreen.ONBOARDING_TOPICS -> drawTopicSelection(canvas)
                    ArDrawScreen.ONBOARDING_LOADING -> drawPersonalizing(canvas)
                    ArDrawScreen.ONBOARDING_PAYWALL -> drawPaywall(canvas)
                    ArDrawScreen.HOME -> drawHome(canvas)
                    ArDrawScreen.HOME_SOURCE_MODAL -> drawSourceModal(canvas)
                    ArDrawScreen.SEARCH,
                    ArDrawScreen.SEARCH_RESULTS,
                        -> drawSearch(canvas)

                    ArDrawScreen.GALLERY,
                    ArDrawScreen.FILTER,
                        -> drawGallery(canvas)

                    ArDrawScreen.SETTINGS -> drawSettings(canvas)
                    ArDrawScreen.SETTINGS_DETAIL -> drawSettingsDetail(canvas)
                    ArDrawScreen.LEARN_PATH,
                    ArDrawScreen.LEARN_CATEGORIES,
                        -> drawLearn(canvas)

                    ArDrawScreen.LEARN_LEVEL_DETAIL,
                    ArDrawScreen.LEARN_CATEGORY_DETAIL,
                        -> drawLearnDetail(canvas)

                    ArDrawScreen.PROFILE_FAVORITE_EMPTY,
                    ArDrawScreen.PROFILE_ALBUM_EMPTY,
                    ArDrawScreen.PROFILE_FAVORITE,
                    ArDrawScreen.PROFILE_ALBUM,
                        -> drawProfile(canvas)

                    ArDrawScreen.TUTORIAL_CAMERA,
                    ArDrawScreen.TUTORIAL_SCREEN,
                        -> drawTutorial(canvas)

                    ArDrawScreen.DRAWING_CANVAS,
                    ArDrawScreen.DRAWING_CAMERA,
                    ArDrawScreen.DRAWING_OPACITY,
                        -> drawDrawing(canvas)

                    ArDrawScreen.DRAWING_COMPLETE -> drawComplete(canvas)
                }
            }
        }
        exportFrameReady?.let { callback ->
            exportFrameReady = null
            postOnAnimation(callback)
        }
    }

    private fun drawOnboarding(canvas: Canvas) {
        whiteBackground(canvas)
        statusBar(canvas)
        val page = when (state.screen) {
            ArDrawScreen.ONBOARDING_PROJECTOR -> OnboardingPage(
                "Image Projector",
                "Project your masterpiece through\nAR technology",
                R.drawable.onboarding_projector_art,
                "Image Projector",
                "Project images onto paper using AR\ntechnology, so you can sketch with ease",
                PINK,
                0,
            )

            ArDrawScreen.ONBOARDING_LIGHTBOX -> OnboardingPage(
                "Light Box",
                "Use your device to precisely\nilluminate and trace images",
                R.drawable.onboarding_lightbox_art,
                "Light Box",
                "Transform your device screen into an\neasy to use drawing tool",
                PURPLE,
                1,
            )

            else -> OnboardingPage(
                "Lessons",
                "Follow the step-by-step tutorials to\nimprove your drawing skills!",
                R.drawable.onboarding_lessons_art,
                "Lessons",
                "Learn to draw with step by step tutorials,\nsuitable for all skill levels",
                GREEN,
                2,
            )
        }
        text(canvas, page.title, 180f, 84f, 18f, NAVY, bold = true, align = Paint.Align.CENTER)
        multiline(canvas, page.subtitle, 180f, 112f, 14f, NAVY, Paint.Align.CENTER, 22f)
        image(canvas, page.artwork, 40f, 155f, 280f, 430f, corner = 8f)
        image(canvas, page.artwork, 18f, 641f, 46f, 46f, corner = 10f)
        text(canvas, page.featureTitle, 76f, 655f, 15f, page.accent, bold = true)
        multiline(canvas, page.featureBody, 76f, 679f, 12f, NAVY, Paint.Align.LEFT, 17f)
        repeat(3) { index ->
            circle(
                canvas,
                164f + index * 16f,
                724f,
                4f,
                if (index == page.dot) PURPLE else LIGHT_GRAY
            )
        }
        primaryButton(canvas, "Continue", 16f, 746f, 328f, 42f)
    }

    private fun drawTopicSelection(canvas: Canvas) {
        whiteBackground(canvas)
        statusBar(canvas)
        text(canvas, "Choose Favourite Topic", 180f, 78f, 17f, NAVY, true, Paint.Align.CENTER)
        text(canvas, "Select up to 3 options", 180f, 100f, 12f, MUTED, align = Paint.Align.CENTER)
        state.catalog?.topics.orEmpty().take(9).forEachIndexed { index, topic ->
            val col = index % 3
            val row = index / 3
            val x = 18f + col * 113f
            val y = 120f + row * 166f
            val selected = topic.id in state.selectedTopicIds
            card(canvas, x, y, 102f, 148f, 8f, if (selected) PURPLE else BORDER, 1.5f)
            image(canvas, topic.image, x + 5f, y + 5f, 92f, 112f, 7f)
            text(
                canvas,
                topic.title,
                x + 51f,
                y + 137f,
                11f,
                if (selected) PURPLE else NAVY,
                true,
                Paint.Align.CENTER
            )
            if (selected) {
                circle(canvas, x + 88f, y + 15f, 9f, PURPLE)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.6f
                paint.strokeCap = Paint.Cap.ROUND
                paint.color = Color.WHITE
                val check = Path().apply {
                    moveTo(x + 84f, y + 15f)
                    lineTo(x + 87f, y + 18f)
                    lineTo(x + 92f, y + 12f)
                }
                canvas.drawPath(check, paint)
                paint.style = Paint.Style.FILL
            }
        }
        primaryButton(canvas, "Continue", 16f, 744f, 328f, 42f)
    }

    private fun drawPersonalizing(canvas: Canvas) {
        whiteBackground(canvas)
        statusBar(canvas)
        val selectedImage = state.catalog?.topics
            ?.firstOrNull { it.id in state.selectedTopicIds }
            ?.image
        if (selectedImage != null) image(canvas, selectedImage, 40f, 145f, 280f, 330f, 14f)
        else image(canvas, R.drawable.topic_chibi, 40f, 145f, 280f, 330f, 14f)
        text(canvas, "Preparing your studio...", 180f, 562f, 18f, PURPLE, true, Paint.Align.CENTER)
        text(
            canvas,
            "Setting up content based on your choices",
            180f,
            586f,
            12f,
            MUTED,
            align = Paint.Align.CENTER
        )
        listOf("Saving favorite topics", "Loading drawing references", "Getting AR tools ready")
            .forEachIndexed { index, label ->
                val y = 625f + index * 48f
                text(canvas, label, 24f, y, 11f, MUTED)
                roundRect(canvas, 24f, y + 10f, 312f, 5f, 3f, LIGHT_GRAY)
                roundRect(
                    canvas,
                    24f,
                    y + 10f,
                    150f + index * 20f,
                    5f,
                    3f,
                    listOf(PINK, GREEN, BLUE)[index]
                )
            }
    }

    private fun drawPaywall(canvas: Canvas) {
        fill(canvas, 0f, 0f, 360f, 800f, Color.rgb(254, 241, 255))
        imageSourceCrop(canvas, R.drawable.onboarding_paywall_art, 0f, 0f, 360f, 334f, 0.885f)
        statusBar(canvas)
        multiline(
            canvas,
            "Bring your imagination to\nlife with AR Drawing",
            180f,
            365f,
            18f,
            Color.rgb(65, 14, 105),
            Paint.Align.CENTER,
            23f,
            true
        )
        multiline(
            canvas,
            "Learn, draw and create amazing art with\npowerful AR tools and step by step guides",
            180f,
            432f,
            12f,
            MUTED,
            Paint.Align.CENTER,
            17f
        )
        state.catalog?.plans.orEmpty().take(3).forEachIndexed { index, item ->
            plan(
                canvas,
                16f,
                468f + index * 74f,
                item.title,
                item.subtitle,
                item.price,
                state.selectedPlanId?.let { it == item.id } ?: item.isRecommended,
            )
        }
        primaryButton(canvas, "Continue", 16f, 694f, 328f, 44f)
        text(
            canvas,
            "Term of use",
            16f,
            774f,
            10f,
            NAVY,
        )
        text(canvas, "Cancel anytime", 344f, 774f, 10f, NAVY, align = Paint.Align.RIGHT)
    }

    private fun drawHome(canvas: Canvas) {
        whiteBackground(canvas)
        canvas.withClip(0f, 0f, 360f, 742f) {
            canvas.withTranslation(0f, -contentScrollOffset) {
                brandedHeader(canvas, showSearch = true)
                text(canvas, "Draw from", 16f, 203f, 16f, NAVY, true)
                sourceCard(
                    canvas,
                    16f,
                    219f,
                    101f,
                    "My\nGallery",
                    R.drawable.figma_source_gallery,
                    badge = "Hot trend",
                )
                sourceCard(
                    canvas,
                    129f,
                    219f,
                    101f,
                    "AI\nEmoji Mix",
                    R.drawable.figma_source_ai,
                    labelSize = 12f,
                )
                sourceCard(
                    canvas,
                    242f,
                    219f,
                    101f,
                    "Web\nBrowser",
                    R.drawable.figma_source_web,
                )
                text(canvas, "Choose topic", 16f, 311f, 16f, NAVY, true)
                drawTopicGrid(canvas, 325f, rows = HOME_TOPIC_LIMIT / 2)
            }
        }
        bottomNavigation(canvas, BottomDestination.HOME)
    }

    private fun drawSourceModal(canvas: Canvas) {
        drawHome(canvas)
        fill(canvas, 0f, 0f, 360f, 800f, Color.argb(112, 0, 0, 0))
        card(canvas, 16f, 250f, 328f, 228f, 12f, Color.TRANSPARENT, 0f, Color.WHITE)
        text(canvas, "From your device", 30f, 282f, 17f, NAVY, true)
        vectorIcon(canvas, IconAsset.CLOSE, 314f, 270f, 24f, MUTED)
        multiline(
            canvas,
            "Choose any image from your gallery or take\none with your phone's camera",
            30f,
            308f,
            11f,
            MUTED,
            Paint.Align.LEFT,
            16f
        )
        choiceCard(
            canvas,
            30f,
            346f,
            "Gallery",
            IconAsset.GALLERY,
            state.selectedDeviceSource == DeviceImageSource.GALLERY
        )
        choiceCard(
            canvas,
            185f,
            346f,
            "Camera",
            IconAsset.CAMERA,
            state.selectedDeviceSource == DeviceImageSource.CAMERA
        )
        primaryButton(canvas, "Confirm", 28f, 433f, 304f, 36f)
    }

    private fun drawSearch(canvas: Canvas) {
        whiteBackground(canvas)
        statusBar(canvas)
        backHeader(canvas, "Back", 13f, bold = false)
        // A real EditText is overlaid by HomeFragment on search screens for IME/accessibility.
        searchBar(canvas, 16f, 96f, "")
        if (state.screen == ArDrawScreen.SEARCH) {
            text(canvas, "Trending", 16f, 168f, 13f, NAVY, true)
            state.catalog?.trendingSearches.orEmpty().take(4).forEachIndexed { index, item ->
                val y = 199f + index * 45f
                text(canvas, item.title, 16f, y, 14f, NAVY)
                val accent = item.accentColorHex?.let { hex ->
                    runCatching { hex.toColorInt() }.getOrNull()
                }
                if (accent != null) circle(canvas, 329f, y - 5f, 10f, accent)
                else image(canvas, item.image, 316f, y - 18f, 26f, 26f, 7f)
                if (index < 3) line(canvas, 16f, y + 15f, 344f, y + 15f, BORDER, 1f)
            }
        } else {
            canvas.withClip(0f, 142f, 360f, 800f) {
                canvas.withTranslation(0f, -contentScrollOffset) {
                    drawArtworkGrid(
                        canvas,
                        160f,
                        rows = (state.visibleArtworks.size + 1) / 2,
                        state.visibleArtworks,
                    )
                }
            }
            if (state.visibleArtworks.isEmpty()) emptyState(
                canvas,
                "No drawings found",
                360f,
                IconAsset.SEARCH
            )
        }
    }

    private fun drawGallery(canvas: Canvas) {
        whiteBackground(canvas)
        statusBar(canvas)
        val galleryTitle =
            state.catalog?.topics?.firstOrNull { it.id == state.selectedGalleryTopicId }?.title
                ?: "Gallery"
        backHeader(canvas, galleryTitle, 15f)
        vectorIcon(
            canvas,
            IconAsset.FILTER,
            316f,
            64f,
            24f,
            if (state.selectedDifficulty != null) PURPLE else NAVY
        )
        GALLERY_QUICK_FILTERS.forEachIndexed { index, (filter, label) ->
            val x = 16f + GALLERY_FILTER_X_OFFSETS[index]
            chip(
                canvas,
                x,
                96f,
                GALLERY_FILTER_WIDTHS[index],
                label,
                state.selectedGalleryFilter == filter
            )
        }
        canvas.withClip(0f, 132f, 360f, 800f) {
            canvas.withTranslation(0f, -contentScrollOffset) {
                drawArtworkGrid(
                    canvas,
                    142f,
                    rows = (state.visibleGalleryArtworks.size + 1) / 2,
                    state.visibleGalleryArtworks,
                    showHearts = true,
                    showTitles = false,
                )
            }
        }
        if (state.visibleGalleryArtworks.isEmpty()) {
            emptyState(canvas, "No drawings match these filters", 360f, IconAsset.FILTER)
        }
        if (state.screen == ArDrawScreen.FILTER) {
            fill(canvas, 0f, 0f, 360f, 800f, Color.argb(105, 0, 0, 0))
            roundRect(canvas, 0f, 478f, 360f, 322f, 18f, Color.WHITE)
            text(canvas, "Filter", 16f, 519f, 18f, NAVY, true)
            text(canvas, "Difficulty level", 16f, 559f, 12f, NAVY)
            chip(canvas, 16f, 567f, 98f, "Easy", state.selectedDifficulty == "Easy")
            chip(canvas, 131f, 567f, 98f, "Medium", state.selectedDifficulty == "Medium")
            chip(canvas, 246f, 567f, 98f, "Hard", state.selectedDifficulty == "Hard")
            text(canvas, "Difficulty level", 16f, 631f, 12f, NAVY)
            chip(
                canvas,
                16f,
                639f,
                156f,
                "Line Sketch",
                state.selectedDrawingStyle == ArtworkStyle.LINE_SKETCH
            )
            chip(
                canvas,
                188f,
                639f,
                156f,
                "Color",
                state.selectedDrawingStyle == ArtworkStyle.COLOR
            )
            primaryButton(canvas, "Continue", 16f, 686f, 328f, 44f)
            outlineButton(canvas, "Reset filters", 16f, 740f, 328f, 44f)
        }
    }

    private fun drawSettings(canvas: Canvas) {
        whiteBackground(canvas)
        brandedHeader(canvas, showSearch = false)
        settingsUpgradeBanner(canvas)
        text(canvas, "Setting", 16f, 282f, 14f, NAVY, true)
        state.catalog?.settings.orEmpty().forEachIndexed { index, item ->
            val y = 315f + index * 40f
            settingVectorIcon(canvas, settingIconResource(item.id), 14f, y - 13f, 20f)
            text(canvas, item.title, 42f, y + 1f, 12f, NAVY)
            when (item.id) {
                "gift" -> {
                    roundRect(canvas, 247f, y - 14f, 58f, 23f, 11f, Color.rgb(247, 238, 255))
                    text(canvas, "Only you", 276f, y + 2f, 10f, PURPLE, align = Paint.Align.CENTER)
                    vectorIcon(canvas, IconAsset.CHEVRON_RIGHT, 320f, y - 12f, 18f, MUTED)
                }

                "music" -> toggle(canvas, 309f, y - 14f, state.musicEnabled)
                else -> vectorIcon(canvas, IconAsset.CHEVRON_RIGHT, 320f, y - 12f, 18f, MUTED)
            }
            line(canvas, 16f, y + 16f, 344f, y + 16f, BORDER, 0.8f)
        }
        bottomNavigation(canvas, BottomDestination.SETTINGS)
    }

    private fun settingsUpgradeBanner(canvas: Canvas) {
        val x = 16f
        val y = 136f
        val width = 328f
        val height = 112f
        paint.shader = settingsUpgradeGradient
        canvas.drawRoundRect(x, y, x + width, y + height, 10f, 10f, paint)
        paint.shader = null
        multiline(
            canvas,
            "Unlock all of our features,\nall template and remove ads",
            30f,
            165f,
            12f,
            Color.WHITE,
            Paint.Align.LEFT,
            17f,
            true,
        )
        roundRect(canvas, 30f, 198f, 118f, 34f, 7f, Color.WHITE)
        text(canvas, "Tap to Upgrade", 89f, 220f, 12f, PURPLE, true, Paint.Align.CENTER)
        image(canvas, R.drawable.figma_source_ai, 244f, 144f, 90f, 100f, 0f)
    }

    private fun drawSettingsDetail(canvas: Canvas) {
        whiteBackground(canvas)
        statusBar(canvas)
        val id = state.selectedSettingId
        val title = when (id) {
            "gift" -> "Gift Code"
            "help" -> "Help & FAQs"
            "privacy" -> "Privacy policy"
            "terms" -> "Terms of use"
            else -> "Settings"
        }
        backHeader(canvas, title, 17f)
        line(canvas, 0f, 96f, 360f, 96f, BORDER, 1f)
        when (id) {
            "gift" -> {
                circle(canvas, 180f, 200f, 48f, Color.rgb(247, 238, 255))
                vectorIcon(canvas, IconAsset.GIFT, 156f, 176f, 48f, PURPLE)
                text(canvas, "Redeem a gift code", 180f, 280f, 18f, NAVY, true, Paint.Align.CENTER)
                multiline(
                    canvas,
                    "Gift code validation will be available when the\naccount API is connected. No code is accepted locally.",
                    180f,
                    316f,
                    13f,
                    MUTED,
                    Paint.Align.CENTER,
                    19f
                )
            }

            "help" -> {
                text(canvas, "How AR Drawing works", 16f, 132f, 16f, NAVY, true)
                helpRow(
                    canvas,
                    166f,
                    "1",
                    "Choose a reference",
                    "Pick a template or an image from your device."
                )
                helpRow(
                    canvas,
                    242f,
                    "2",
                    "Place your phone",
                    "Keep the camera steady above your paper."
                )
                helpRow(
                    canvas,
                    318f,
                    "3",
                    "Adjust the overlay",
                    "Move, zoom, flip and change opacity as needed."
                )
                helpRow(
                    canvas,
                    394f,
                    "4",
                    "Trace and save",
                    "Draw on paper, then capture or record your result."
                )
            }

            "privacy" -> {
                text(canvas, "Your privacy", 16f, 132f, 16f, NAVY, true)
                wrappedText(
                    canvas = canvas,
                    value = "Camera access is used only while AR Drawing is open.\n\n" +
                            "Photos and videos are created on your device. They are not uploaded by the offline version of this app.\n\n" +
                            "When an API is connected, this notice must be updated to describe any data collected, stored or shared.",
                    x = 16f,
                    baseline = 170f,
                    maxWidth = 328f,
                    size = 13f,
                    color = MUTED,
                    lineHeight = 21f,
                )
            }

            "terms" -> {
                text(canvas, "Using AR Drawing", 16f, 132f, 16f, NAVY, true)
                wrappedText(
                    canvas = canvas,
                    value = "Use only images you have permission to trace or share.\n\n" +
                            "Keep your phone secure while drawing and do not operate the camera where device use is unsafe.\n\n" +
                            "Subscription purchases, if enabled later, must be confirmed through Google Play and follow its cancellation terms.",
                    x = 16f,
                    baseline = 170f,
                    maxWidth = 328f,
                    size = 13f,
                    color = MUTED,
                    lineHeight = 21f,
                )
            }
        }
        outlineButton(canvas, "Back to Settings", 16f, 732f, 328f, 46f)
    }

    private fun helpRow(canvas: Canvas, y: Float, number: String, title: String, body: String) {
        circle(canvas, 34f, y, 18f, Color.rgb(247, 238, 255))
        text(canvas, number, 34f, y + 5f, 13f, PURPLE, true, Paint.Align.CENTER)
        text(canvas, title, 64f, y - 5f, 13f, NAVY, true)
        text(canvas, body, 64f, y + 15f, 10.5f, MUTED)
    }

    private fun drawLearn(canvas: Canvas) {
        whiteBackground(canvas)
        brandedHeader(canvas, showSearch = true)
        text(canvas, "Learn to draw", 16f, 208f, 16f, NAVY, true)
        drawLearnBanner(canvas)
        val categories = state.screen == ArDrawScreen.LEARN_CATEGORIES
        tab(canvas, "Learn path", 0f, 378f, 180f, !categories)
        tab(canvas, "Categories", 180f, 378f, 180f, categories)
        canvas.withClip(0f, 420f, 360f, 742f) {
            canvas.withTranslation(0f, -contentScrollOffset) {
                if (categories) drawCategoryRows(canvas) else drawLevelRows(canvas)
            }
        }
        bottomNavigation(canvas, BottomDestination.LEARN)
    }

    private fun drawLearnDetail(canvas: Canvas) {
        whiteBackground(canvas)
        statusBar(canvas)
        val isCategory = state.screen == ArDrawScreen.LEARN_CATEGORY_DETAIL
        val category = state.selectedCategory
        val lesson = state.selectedLesson
        val levelNumber = state.learningPathLessons.indexOfFirst { it.id == lesson?.id }
            .takeIf { it >= 0 }
            ?.plus(1)
        backHeader(
            canvas,
            if (isCategory) category?.title ?: "Category" else "Level ${levelNumber ?: 1}",
            16f
        )
        line(canvas, 0f, 88f, 360f, 88f, BORDER, 1f)
        if (isCategory) {
            text(canvas, category?.title ?: "Category", 16f, 119f, 15f, PURPLE, true)
            multiline(
                canvas,
                "Discover an endless array of cool pictures to draw with\n" +
                        "our selection of plant, flower, and tree drawing tutorials.\n" +
                        "How might you use these easy drawing guides, designed\n" +
                        "for kids of all ages?",
                16f,
                140f,
                11f,
                MUTED,
                Paint.Align.LEFT,
                18f,
            )
            state.catalog?.lessons.orEmpty()
                .filter { category == null || it.categoryId == category.id }
                .forEachIndexed { index, item ->
                    lessonCard(
                        canvas,
                        16f,
                        216f + index * 100f,
                        item.title,
                        item.minutes,
                        item.image
                    )
                }
        } else {
            lesson?.let { item ->
                lessonCard(canvas, 16f, 104f, item.title, item.minutes, item.image)
            }
        }
    }

    private fun drawProfile(canvas: Canvas) {
        whiteBackground(canvas)
        brandedHeader(canvas, showSearch = false)
        text(canvas, "Learn to draw", 16f, 153f, 16f, NAVY, true)
        stat(
            canvas,
            16f,
            170f,
            101f,
            IconAsset.SKETCH,
            state.drawings.size.toString(),
            "Sketches",
            Color.rgb(248, 240, 255),
            Color.rgb(167, 118, 246)
        )
        stat(
            canvas,
            129f,
            170f,
            101f,
            IconAsset.LESSON,
            state.completedLessonCount.toString(),
            "Lessons",
            Color.rgb(235, 255, 243),
            Color.rgb(54, 199, 135)
        )
        stat(
            canvas,
            242f,
            170f,
            101f,
            IconAsset.CLOCK,
            state.completedLessonMinutes.toString(),
            "Time",
            Color.rgb(235, 247, 255),
            Color.rgb(105, 174, 249)
        )
        val album =
            state.screen == ArDrawScreen.PROFILE_ALBUM || state.screen == ArDrawScreen.PROFILE_ALBUM_EMPTY
        profileTabs(canvas, album)
        val empty = if (album) state.drawings.isEmpty() else state.favoriteArtworkIds.isEmpty()
        if (empty) {
            if (album) image(
                canvas,
                R.drawable.figma_profile_empty_album,
                81.5f,
                404f,
                197f,
                166f,
                0f
            )
            else image(canvas, R.drawable.figma_profile_empty_favorite, 102f, 404f, 156f, 166f, 0f)
        } else {
            canvas.withClip(0f, 358f, 360f, 742f) {
                canvas.withTranslation(0f, -contentScrollOffset) {
                    if (album) drawAlbumGrid(canvas, 366f, state.drawings)
                    else {
                        val favorites = state.catalog?.artworks.orEmpty()
                            .filter { it.id in state.favoriteArtworkIds }
                        drawArtworkGrid(
                            canvas,
                            366f,
                            rows = (favorites.size + 1) / 2,
                            artworks = favorites,
                            showHearts = true,
                        )
                    }
                }
            }
        }
        bottomNavigation(canvas, BottomDestination.PROFILE)
    }

    private fun profileTabs(canvas: Canvas, albumSelected: Boolean) {
        roundRect(canvas, 16f, 300f, 328f, 50f, 10f, Color.rgb(248, 249, 252))
        strokeRoundRect(canvas, 16f, 300f, 328f, 50f, 10f, BORDER, 1f)
        val selectedX = if (albumSelected) 180f else 20f
        roundRect(canvas, selectedX, 304f, 160f, 42f, 8f, Color.WHITE)
        strokeRoundRect(canvas, selectedX, 304f, 160f, 42f, 8f, BORDER, 1f)
        text(
            canvas,
            "Favorite",
            100f,
            330f,
            14f,
            if (albumSelected) MUTED else NAVY,
            !albumSelected,
            Paint.Align.CENTER
        )
        text(
            canvas,
            "My Album",
            260f,
            330f,
            14f,
            if (albumSelected) NAVY else MUTED,
            albumSelected,
            Paint.Align.CENTER
        )
    }

    private fun drawTutorial(canvas: Canvas) {
        whiteBackground(canvas)
        statusBar(canvas)
        backHeader(canvas, "Select mode", 17f)
        val camera = state.screen == ArDrawScreen.TUTORIAL_CAMERA
        val offset = if (camera) 0f else -182f
        canvas.withClip(0f, 104f, 360f, 570f) {
            drawTutorialModeCard(canvas, 16f + offset, useCamera = true, selected = camera)
            drawTutorialModeCard(canvas, 256f + offset, useCamera = false, selected = !camera)
        }
        primaryButton(canvas, "Draw now", 16f, 738f, 328f, 46f)
    }

    private fun drawTutorialModeCard(
        canvas: Canvas,
        x: Float,
        useCamera: Boolean,
        selected: Boolean,
    ) {
        card(
            canvas,
            x,
            112f,
            228f,
            430f,
            12f,
            if (selected) PURPLE else BORDER,
            if (selected) 1.5f else 1f
        )
        drawSelectedArtwork(canvas, x + 8f, 120f, 212f, 316f, 9f)
        text(
            canvas,
            if (useCamera) "Draw with camera" else "Draw with screen",
            x + 8f,
            466f,
            14f,
            NAVY,
            true,
        )
        wrappedText(
            canvas = canvas,
            value = if (useCamera) {
                "Use a cup to hold your phone steady to draw through the camera"
            } else {
                "Place the paper on top of the phone and trace the visible lines on the screen"
            },
            x = x + 8f,
            baseline = 489f,
            maxWidth = 212f,
            size = 11f,
            color = MUTED,
            lineHeight = 15f,
        )
    }

    private fun drawDrawing(canvas: Canvas) {
        if (!state.drawingWithCamera) {
            fill(canvas, 0f, 52f, 360f, 748f, Color.rgb(250, 250, 250))
        } else if (!state.cameraPermissionGranted) {
            image(canvas, R.drawable.drawing_camera_background, 0f, 52f, 360f, 748f, 0f)
        }
        if (state.overlayVisible && (!cameraOverlayExternal || exportOnly)) drawDrawingOverlay(
            canvas
        )
        if (!exportOnly) drawDrawingGrid(canvas)
        if (exportOnly) return
        drawDrawingHeader(canvas)
        fill(canvas, 0f, 678f, 360f, 48f, Color.argb(174, 0, 0, 0))
        fill(canvas, 0f, 726f, 360f, 74f, Color.argb(218, 0, 0, 0))
        statusBar(canvas, light = true)
        when (state.screen) {
            ArDrawScreen.DRAWING_OPACITY -> drawOpacityControls(canvas)
            ArDrawScreen.DRAWING_CAMERA -> drawCameraControls(canvas)
            else -> drawCanvasControls(canvas)
        }
        drawingTool(
            canvas,
            51f,
            ToolAsset.OPACITY,
            "Opacity",
            state.screen == ArDrawScreen.DRAWING_OPACITY
        )
        drawingTool(
            canvas,
            137f,
            ToolAsset.CANVAS,
            "Canvas",
            state.screen == ArDrawScreen.DRAWING_CANVAS
        )
        drawingTool(
            canvas,
            223f,
            ToolAsset.CAMERA,
            "Camera",
            state.screen == ArDrawScreen.DRAWING_CAMERA,
            enabled = state.drawingWithCamera,
        )
        drawingTool(canvas, 309f, ToolAsset.HIDE, "Hide", !state.overlayVisible)
    }

    private fun drawDrawingHeader(canvas: Canvas) {
        fill(canvas, 0f, 0f, 360f, 52f, Color.BLACK)
        fill(canvas, 0f, 52f, 360f, 52f, Color.argb(150, 40, 42, 40))
        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.8f
        paint.strokeCap = Paint.Cap.SQUARE
        val back = Path().apply {
            moveTo(26f, 72f)
            lineTo(20f, 78f)
            lineTo(26f, 84f)
            moveTo(20f, 78f)
            lineTo(33f, 78f)
        }
        canvas.drawPath(back, paint)
        paint.strokeCap = Paint.Cap.BUTT
        paint.style = Paint.Style.FILL
        text(canvas, "Back", 42f, 83f, 14f, Color.WHITE)
        roundRect(canvas, 247f, 60f, 97f, 34f, 17f, PURPLE)
        text(canvas, "Complete", 295.5f, 83f, 14f, Color.WHITE, align = Paint.Align.CENTER)
    }

    private fun drawOpacityControls(canvas: Canvas) {
        image(canvas, R.drawable.icon_eye_off, 18f, 693.25f, 20f, 17.5f, 0f)
        val start = 56f
        val width = 248f
        roundRect(canvas, start, 698f, width, 8f, 4f, Color.rgb(235, 235, 235))
        val displayOpacity = if (localOpacityActive) localOpacity else state.opacity
        val progress = width * displayOpacity
        roundRect(canvas, start, 698f, progress, 8f, 4f, PURPLE)
        circle(canvas, start + progress, 702f, 6f, Color.WHITE)
        image(canvas, R.drawable.icon_eye, 322f, 695f, 20f, 14f, 0f)
    }

    private fun drawCanvasControls(canvas: Canvas) {
        when {
            state.canvasPanel == DrawingCanvasPanel.CROP -> drawCropOptions(canvas)
            state.canvasPanel == DrawingCanvasPanel.GRID -> drawGridOptions(canvas)
            state.drawingStatus != DrawingStatus.NONE -> drawDrawingStatus(canvas)
        }
        drawingQuickChip(canvas, 12f, 682f, 56f, "Lock", state.overlayLocked)
        drawingQuickChip(canvas, 74f, 682f, 50f, "Flip", state.overlayFlipped)
        drawingQuickChip(canvas, 130f, 682f, 98f, "Remove BG", state.removeImageEnabled)
        drawingQuickChip(
            canvas,
            234f,
            682f,
            55f,
            "Crop",
            state.canvasPanel == DrawingCanvasPanel.CROP
        )
        drawingQuickChip(
            canvas,
            295f,
            682f,
            50f,
            "Grid",
            state.canvasPanel == DrawingCanvasPanel.GRID
        )
    }

    private fun drawCameraControls(canvas: Canvas) {
        when (state.cameraPanel) {
            DrawingCameraPanel.ZOOM -> {
                optionPill(
                    canvas,
                    CAMERA_ZOOM_HALF_X,
                    650f,
                    CAMERA_ZOOM_OPTION_WIDTH,
                    "0.5x",
                    state.cameraZoom == 0.5f,
                )
                optionPill(
                    canvas,
                    CAMERA_ZOOM_ONE_AND_HALF_X,
                    650f,
                    CAMERA_ZOOM_OPTION_WIDTH,
                    "1.5x",
                    state.cameraZoom == 1.5f,
                )
            }

            DrawingCameraPanel.FLASH -> drawDrawingStatus(canvas)
            DrawingCameraPanel.CAPTURE,
            DrawingCameraPanel.RECORD,
                -> drawCameraShutter(canvas)

            DrawingCameraPanel.RATIO -> drawCameraRatioOptions(canvas)
            DrawingCameraPanel.NONE -> Unit
        }
        drawingQuickChip(
            canvas,
            16f,
            682f,
            58f,
            "Zoom",
            state.cameraPanel == DrawingCameraPanel.ZOOM
        )
        drawingQuickChip(
            canvas,
            80f,
            682f,
            56f,
            "Flash",
            state.cameraPanel == DrawingCameraPanel.FLASH
        )
        drawingQuickChip(
            canvas,
            142f,
            682f,
            70f,
            "Capture",
            state.cameraPanel == DrawingCameraPanel.CAPTURE
        )
        drawingQuickChip(
            canvas,
            218f,
            682f,
            66f,
            "Record",
            state.cameraPanel == DrawingCameraPanel.RECORD
        )
        drawingQuickChip(
            canvas,
            290f,
            682f,
            54f,
            "Ratio",
            state.cameraPanel == DrawingCameraPanel.RATIO
        )
    }

    private fun drawCropOptions(canvas: Canvas) {
        optionPill(
            canvas,
            83f,
            650f,
            36f,
            "Reset",
            state.cropRatio == DrawingCropRatio.RESET,
        )
        optionPill(canvas, 125f, 650f, 30f, "1:1", state.cropRatio == DrawingCropRatio.SQUARE)
        optionPill(canvas, 162f, 650f, 32f, "9:16", state.cropRatio == DrawingCropRatio.PORTRAIT)
        optionPill(canvas, 201f, 650f, 36f, "16:9", state.cropRatio == DrawingCropRatio.LANDSCAPE)
    }

    private fun drawGridOptions(canvas: Canvas) {
        optionPill(canvas, 221f, 650f, 34f, "3x3", state.gridSize == 3)
        optionPill(canvas, 264f, 650f, 34f, "4x4", state.gridSize == 4)
        optionPill(canvas, 307f, 650f, 34f, "5x5", state.gridSize == 5)
    }

    private fun drawCameraRatioOptions(canvas: Canvas) {
        optionPill(canvas, 58f, 650f, 54f, "Full", state.cameraRatio == DrawingCameraRatio.FULL)
        optionPill(
            canvas,
            120f,
            650f,
            54f,
            "16:9",
            state.cameraRatio == DrawingCameraRatio.RATIO_16_9
        )
        optionPill(
            canvas,
            182f,
            650f,
            54f,
            "4:3",
            state.cameraRatio == DrawingCameraRatio.RATIO_4_3
        )
        optionPill(canvas, 244f, 650f, 54f, "1:1", state.cameraRatio == DrawingCameraRatio.SQUARE)
    }

    private fun drawDrawingStatus(canvas: Canvas) {
        val (label, enabled) = when (state.drawingStatus) {
            DrawingStatus.LOCK -> "Lock Image" to state.overlayLocked
            DrawingStatus.FLIP -> "Flip Image" to state.overlayFlipped
            DrawingStatus.REMOVE_IMAGE -> "Remove Image" to state.removeImageEnabled
            DrawingStatus.FLASH -> "Flash" to state.flashEnabled
            DrawingStatus.NONE -> return
        }
        val display = "$label: ${if (enabled) "ON" else "OFF"}"
        paint.textSize = 10f
        paint.typeface = ROUNDED_BOLD
        val width = paint.measureText(display) + 34f
        val x = (180f - width / 2f).coerceIn(10f, 350f - width)
        roundRect(canvas, x, 646f, width, 24f, 12f, Color.WHITE)
        drawDrawingStatusIcon(
            canvas,
            x + 14f,
            658f,
            state.drawingStatus,
            if (enabled) PURPLE else NAVY
        )
        text(canvas, display, x + 27f, 662f, 10f, if (enabled) PURPLE else NAVY, true)
    }

    private fun drawDrawingStatusIcon(
        canvas: Canvas,
        x: Float,
        y: Float,
        status: DrawingStatus,
        color: Int,
    ) {
        paint.shader = null
        paint.color = color
        paint.strokeWidth = 1.4f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.style = Paint.Style.STROKE
        when (status) {
            DrawingStatus.LOCK -> {
                canvas.drawRoundRect(x - 4.5f, y - 1f, x + 4.5f, y + 5f, 1.5f, 1.5f, paint)
                val shackle = Path().apply {
                    moveTo(x - 3f, y - 1f)
                    cubicTo(x - 3f, y - 6f, x + 3f, y - 6f, x + 3f, y - 1f)
                }
                canvas.drawPath(shackle, paint)
            }

            DrawingStatus.FLIP -> {
                canvas.drawLine(x - 5f, y - 3f, x + 5f, y - 3f, paint)
                canvas.drawLine(x - 5f, y + 3f, x + 5f, y + 3f, paint)
                canvas.drawLine(x - 5f, y - 3f, x - 2f, y - 6f, paint)
                canvas.drawLine(x + 5f, y + 3f, x + 2f, y + 6f, paint)
            }

            DrawingStatus.REMOVE_IMAGE -> {
                canvas.withSave {
                    rotate(-45f, x, y)
                    drawRoundRect(x - 4f, y - 6f, x + 4f, y + 5f, 1.5f, 1.5f, paint)
                    drawLine(x - 4f, y + 1f, x + 4f, y + 1f, paint)
                }
            }

            DrawingStatus.FLASH -> {
                paint.style = Paint.Style.FILL
                val flash = Path().apply {
                    moveTo(x + 1f, y - 7f)
                    lineTo(x - 5f, y + 1f)
                    lineTo(x - 1f, y + 1f)
                    lineTo(x - 2f, y + 7f)
                    lineTo(x + 5f, y - 2f)
                    lineTo(x + 1f, y - 2f)
                    close()
                }
                canvas.drawPath(flash, paint)
            }

            DrawingStatus.NONE -> Unit
        }
        paint.style = Paint.Style.FILL
        paint.strokeCap = Paint.Cap.BUTT
    }

    private fun drawCameraShutter(canvas: Canvas) {
        circle(canvas, 180f, 622f, 32f, Color.WHITE)
        circle(canvas, 180f, 622f, 25f, PURPLE)
        if (state.cameraPanel == DrawingCameraPanel.RECORD && state.isRecording) {
            roundRect(canvas, 170f, 612f, 20f, 20f, 4f, Color.WHITE)
        }
    }

    private fun optionPill(
        canvas: Canvas,
        x: Float,
        y: Float,
        width: Float,
        label: String,
        selected: Boolean
    ) {
        roundRect(canvas, x, y - 4f, width, 28f, 14f, if (selected) PURPLE else Color.WHITE)
        text(
            canvas,
            label,
            x + width / 2f,
            y + 14.5f,
            11f,
            if (selected) Color.WHITE else NAVY,
            true,
            Paint.Align.CENTER
        )
    }

    private fun drawingQuickChip(
        canvas: Canvas,
        x: Float,
        y: Float,
        width: Float,
        label: String,
        selected: Boolean
    ) {
        roundRect(
            canvas,
            x,
            y,
            width,
            32f,
            16f,
            if (selected) PURPLE else Color.argb(92, 34, 36, 40)
        )
        if (!selected) strokeRoundRect(canvas, x, y, width, 32f, 16f, Color.rgb(105, 108, 115), 1f)
        text(canvas, label, x + width / 2f, y + 21f, 13f, Color.WHITE, align = Paint.Align.CENTER)
    }

    private fun drawDrawingGrid(canvas: Canvas) {
        if (state.gridSize <= 0) return
        val size = state.gridSize
        val left = 16f
        val top = 194f
        val extent = 328f
        repeat(size - 1) { index ->
            val fraction = (index + 1f) / size
            line(
                canvas,
                left + extent * fraction,
                top,
                left + extent * fraction,
                top + extent,
                Color.argb(125, 255, 255, 255),
                0.8f
            )
            line(
                canvas,
                left,
                top + extent * fraction,
                left + extent,
                top + extent * fraction,
                Color.argb(125, 255, 255, 255),
                0.8f
            )
        }
    }

    private fun drawComplete(canvas: Canvas) {
        whiteBackground(canvas)
        statusBar(canvas)
        backHeader(canvas, "Back", 15f, bold = false, color = MUTED)
        vectorIcon(canvas, IconAsset.HOME, 316f, 64f, 24f, NAVY)
        text(canvas, "Good Job", 16f, 128f, 19f, NAVY, true)
        val captured = state.capturedImageUri
        wrappedText(
            canvas = canvas,
            value = if (captured == null) {
                "Take a photo of the finished drawing to share your\nfriend."
            } else {
                "Your drawing is saved and ready to share with your friend."
            },
            x = 16f,
            baseline = 156f,
            maxWidth = 328f,
            size = 12f,
            color = MUTED,
            lineHeight = 17f,
        )
        if (captured != null) image(canvas, captured, 52f, 218f, 256f, 360f, 12f)
        else image(canvas, R.drawable.complete_art, 82f, 230f, 196f, 252f, 0f)
        primaryButton(
            canvas,
            if (captured == null) "Take a photo" else "Share drawing",
            16f,
            682f,
            328f,
            46f
        )
        outlineButton(canvas, "It’s not finished yet", 16f, 738f, 328f, 44f)
    }

    private fun brandedHeader(canvas: Canvas, showSearch: Boolean) {
        val height = if (showSearch) 168f else 130f
        val bottomRadius = 20f
        val right = LOGICAL_WIDTH + HEADER_RIGHT_BLEED
        paint.shader = headerGradient
        scratchPath.reset()
        scratchPath.moveTo(0f, 0f)
        scratchPath.lineTo(right, 0f)
        scratchPath.lineTo(right, height - bottomRadius)
        scratchPath.quadTo(right, height, right - bottomRadius, height)
        scratchPath.lineTo(bottomRadius, height)
        scratchPath.quadTo(0f, height, 0f, height - bottomRadius)
        scratchPath.close()
        canvas.drawPath(scratchPath, paint)
        paint.shader = null
        statusBar(canvas, light = true)
        image(canvas, R.drawable.figma_app_icon, 16f, 57f, 46f, 46f, 10f)
        text(canvas, "AR Drawing", 72f, 88f, 20f, Color.WHITE, true)
        premiumBadge(canvas, 322f, 84f)
        if (showSearch) searchBar(canvas, 16f, 111f, "What will you draw today?", height = 44f)
    }

    private fun backHeader(
        canvas: Canvas,
        title: String,
        titleSize: Float,
        bold: Boolean = true,
        color: Int = NAVY,
    ) {
        vectorIcon(canvas, IconAsset.BACK, 12f, 60f, 24f, MUTED)
        text(canvas, title, 43f, 76f, titleSize, color, bold)
    }

    private fun drawLearnBanner(canvas: Canvas) {
        image(canvas, R.drawable.figma_learn_banner, 16f, 218f, 328f, 160f, 0f)
    }

    private fun emptyState(canvas: Canvas, message: String, centerY: Float, icon: IconAsset) {
        vectorIcon(canvas, icon, 156f, centerY - 42f, 48f, LIGHT_GRAY)
        text(canvas, message, 180f, centerY + 28f, 13f, MUTED, align = Paint.Align.CENTER)
    }

    private fun drawTopicGrid(
        canvas: Canvas,
        top: Float,
        rows: Int,
        horizontalPadding: Float = 16f,
    ) {
        val topics = state.catalog?.topics.orEmpty()
        val gap = 16f
        val width = (360f - horizontalPadding * 2f - gap) / 2f
        topics.take(rows * 2).forEachIndexed { index, topic ->
            val col = index % 2
            val row = index / 2
            val x = horizontalPadding + col * (width + gap)
            val y = top + row * 184f
            if (!isContentItemVisible(y, 172f)) return@forEachIndexed
            card(canvas, x, y, width, 172f, 10f, BORDER, 1f)
            image(canvas, topic.image, x + 7f, y + 7f, width - 14f, 138f, 8f)
            ellipsizedText(
                canvas,
                topic.title,
                x + width / 2f,
                y + 164f,
                14f,
                NAVY,
                width - 12f,
                true
            )
        }
    }

    private fun drawArtworkGrid(
        canvas: Canvas,
        top: Float,
        rows: Int,
        artworks: List<Artwork>,
        horizontalPadding: Float = 16f,
        showHearts: Boolean = false,
        showTitles: Boolean = true,
    ) {
        val gap = 16f
        val width = (360f - horizontalPadding * 2f - gap) / 2f
        val cardHeight = if (showTitles) 172f else 152f
        val rowHeight = if (showTitles) 184f else 170f
        artworks.take(rows * 2).forEachIndexed { index, artwork ->
            val col = index % 2
            val row = index / 2
            val x = horizontalPadding + col * (width + gap)
            val y = top + row * rowHeight
            if (!isContentItemVisible(y, cardHeight)) return@forEachIndexed
            card(canvas, x, y, width, cardHeight, 10f, BORDER, 1f)
            image(
                canvas,
                artwork.image,
                x + if (showTitles) 7f else 8f,
                y + if (showTitles) 7f else 8f,
                width - if (showTitles) 14f else 16f,
                if (showTitles) 138f else 140f,
                8f,
            )
            if (showTitles) {
                ellipsizedText(
                    canvas,
                    artwork.title,
                    x + width / 2f,
                    y + 164f,
                    13f,
                    NAVY,
                    width - 12f,
                    true
                )
            }
            if (showHearts) {
                circle(canvas, x + width - 22f, y + 22f, 14f, Color.argb(170, 255, 255, 255))
                vectorIcon(
                    canvas,
                    if (artwork.id in state.favoriteArtworkIds) IconAsset.HEART_FILLED else IconAsset.HEART,
                    x + width - 31f,
                    y + 13f,
                    18f,
                    if (artwork.id in state.favoriteArtworkIds) PINK else NAVY,
                )
            }
        }
    }

    private fun drawAlbumGrid(
        canvas: Canvas,
        top: Float,
        drawings: List<com.a02.draw.domain.model.Drawing>
    ) {
        val width = 156f
        drawings.forEachIndexed { index, drawing ->
            val x = 16f + (index % 2) * 172f
            val y = top + (index / 2) * 184f
            if (!isContentItemVisible(y, 172f)) return@forEachIndexed
            card(canvas, x, y, width, 172f, 10f, BORDER, 1f)
            when {
                drawing.mediaUri != null -> image(
                    canvas,
                    drawing.mediaUri!!,
                    x + 7f,
                    y + 7f,
                    width - 14f,
                    138f,
                    8f
                )

                drawing.artworkId != null -> state.catalog?.artworks
                    ?.firstOrNull { it.id == drawing.artworkId }
                    ?.let { image(canvas, it.image, x + 7f, y + 7f, width - 14f, 138f, 8f) }

                else -> image(
                    canvas,
                    R.drawable.complete_art,
                    x + 7f,
                    y + 7f,
                    width - 14f,
                    138f,
                    8f
                )
            }
            text(
                canvas,
                drawing.title,
                x + width / 2f,
                y + 164f,
                13f,
                NAVY,
                true,
                Paint.Align.CENTER
            )
        }
    }

    private fun drawLevelRows(canvas: Canvas) {
        state.learningPathLessons.forEachIndexed { index, lesson ->
            val y = 436f + index * 70f
            if (!isContentItemVisible(y, 62f)) return@forEachIndexed
            val progress = state.lessonProgressPercent(lesson)
            val completed = if (progress == 100) lesson.totalLessons else lesson.completedLessons
            card(canvas, 16f, y, 328f, 62f, 10f, BORDER, 1f)
            image(canvas, lesson.image, 24f, y + 7f, 48f, 48f, 7f)
            text(canvas, "Level ${index + 1}", 82f, y + 20f, 13f, NAVY, true)
            vectorIcon(canvas, IconAsset.LESSON, 82f, y + 26f, 12f, MUTED)
            text(canvas, "$completed/${lesson.totalLessons} Lessons", 98f, y + 39f, 10.5f, MUTED)
            roundRect(canvas, 82f, y + 48f, 226f, 4f, 2f, LIGHT_GRAY)
            roundRect(canvas, 82f, y + 48f, 226f * progress / 100f, 4f, 2f, PURPLE)
            // Keep a fixed gutter between the progress rail and its right-aligned percentage.
            text(canvas, "$progress%", 336f, y + 55f, 10f, MUTED, align = Paint.Align.RIGHT)
        }
    }

    private fun drawCategoryRows(canvas: Canvas) {
        state.catalog?.categories.orEmpty().forEachIndexed { index, category ->
            val y = 436f + index * 82f
            if (!isContentItemVisible(y, 74f)) return@forEachIndexed
            card(canvas, 16f, y, 328f, 74f, 10f, BORDER, 1f)
            image(canvas, category.image, 24f, y + 8f, 58f, 58f, 7f)
            text(canvas, category.title, 92f, y + 21f, 13f, NAVY, true)
            text(canvas, "Discover the best image examples", 92f, y + 39f, 10f, MUTED)
            chip(canvas, 92f, y + 44f, 58f, category.difficulty, category.difficulty == "Easy")
            vectorIcon(canvas, IconAsset.LESSON, 162f, y + 50f, 14f, MUTED)
            text(canvas, "${category.lessonCount} Lessons", 181f, y + 63f, 10.5f, MUTED)
        }
    }

    private fun lessonCard(
        canvas: Canvas,
        x: Float,
        y: Float,
        title: String,
        minutes: Int,
        image: ContentImage
    ) {
        card(canvas, x, y, 328f, 86f, 10f, BORDER, 1f)
        image(canvas, image, x + 8f, y + 8f, 72f, 70f, 8f)
        text(canvas, title, x + 92f, y + 26f, 13f, NAVY, true)
        vectorIcon(canvas, IconAsset.CLOCK, x + 92f, y + 34f, 14f, NAVY)
        text(canvas, "$minutes mins", x + 114f, y + 53f, 12f, NAVY)
        text(canvas, "Steps:", x + 92f, y + 75f, 12f, NAVY)
        text(canvas, "0/9 Complete", x + 138f, y + 75f, 12f, PURPLE)
    }

    private fun bottomNavigation(canvas: Canvas, selected: BottomDestination) {
        fill(canvas, 0f, 742f, 360f, 58f, Color.WHITE)
        line(canvas, 0f, 742f, 360f, 742f, BORDER, 1f)
        bottomNavigationItems.forEachIndexed { index, item ->
            val x = 45f + index * 90f
            val color = if (item.first == selected) PURPLE else MUTED
            image(
                canvas,
                item.second,
                x - 11f,
                747f,
                22f,
                22f,
                0f,
                if (item.first == selected) 1f else 0.52f
            )
            text(
                canvas,
                item.third,
                x,
                786f,
                10f,
                color,
                item.first == selected,
                Paint.Align.CENTER
            )
        }
    }

    private fun sourceCard(
        canvas: Canvas,
        x: Float,
        y: Float,
        width: Float,
        label: String,
        @DrawableRes artwork: Int,
        labelSize: Float = 13f,
        badge: String? = null,
    ) {
        paint.shader = when (artwork) {
            R.drawable.figma_source_gallery -> gallerySourceGradient
            R.drawable.figma_source_ai -> aiSourceGradient
            else -> webSourceGradient
        }
        canvas.drawRoundRect(x, y, x + width, y + 62f, 8f, 8f, paint)
        paint.shader = null
        multiline(
            canvas,
            label,
            x + 8f,
            y + 28f,
            labelSize,
            Color.WHITE,
            Paint.Align.LEFT,
            15f,
            true
        )
        val (assetWidth, assetHeight) = when (artwork) {
            R.drawable.figma_source_gallery -> 43f to 48f
            R.drawable.figma_source_ai -> 43f to 47f
            else -> 47f to 52f
        }
        image(
            canvas,
            artwork,
            x + width - assetWidth - 1f,
            y + 62f - assetHeight,
            assetWidth,
            assetHeight,
            0f
        )
        badge?.let {
            roundRect(canvas, x, y - 4f, 38f, 12f, 6f, Color.rgb(255, 70, 70))
            text(canvas, it, x + 19f, y + 5f, 7f, Color.WHITE, true, Paint.Align.CENTER)
        }
    }

    private fun choiceCard(
        canvas: Canvas,
        x: Float,
        y: Float,
        title: String,
        icon: IconAsset,
        selected: Boolean
    ) {
        card(canvas, x, y, 136f, 75f, 8f, if (selected) PURPLE else BORDER, 1.2f)
        vectorIcon(canvas, icon, x + 56f, y + 10f, 24f, if (selected) PURPLE else MUTED)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f
        paint.color = if (selected) PURPLE else MUTED
        canvas.drawCircle(x + 42f, y + 59f, 5f, paint)
        if (selected) {
            paint.style = Paint.Style.FILL
            canvas.drawCircle(x + 42f, y + 59f, 2.7f, paint)
        }
        paint.style = Paint.Style.FILL
        text(canvas, title, x + 51f, y + 63f, 11f, NAVY)
    }

    private fun searchBar(canvas: Canvas, x: Float, y: Float, hint: String, height: Float = 42f) {
        roundRect(canvas, x, y, 328f, height, 14f, Color.WHITE)
        strokeRoundRect(canvas, x, y, 328f, height, 14f, BORDER, 1f)
        vectorIcon(canvas, IconAsset.SEARCH, x + 12f, y + (height - 24f) / 2f, 24f, MUTED)
        text(canvas, hint, x + 48f, y + height / 2f + 5f, 14f, MUTED)
    }

    private fun primaryButton(
        canvas: Canvas,
        label: String,
        x: Float,
        y: Float,
        width: Float,
        height: Float
    ) {
        roundRect(canvas, x, y, width, height, 7f, PURPLE)
        text(
            canvas,
            label,
            x + width / 2f,
            y + height / 2f + 6f,
            15f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )
    }

    private fun outlineButton(
        canvas: Canvas,
        label: String,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        fill: Int = Color.WHITE,
        textColor: Int = NAVY,
    ) {
        roundRect(canvas, x, y, width, height, 7f, fill)
        strokeRoundRect(canvas, x, y, width, height, 7f, BORDER, 1f)
        text(
            canvas,
            label,
            x + width / 2f,
            y + height / 2f + 5f,
            13f,
            textColor,
            true,
            Paint.Align.CENTER
        )
    }

    private fun plan(
        canvas: Canvas,
        x: Float,
        y: Float,
        title: String,
        subtitle: String,
        price: String,
        selected: Boolean
    ) {
        card(
            canvas,
            x,
            y,
            328f,
            62f,
            8f,
            if (selected) PURPLE else BORDER,
            1.2f,
            if (selected) Color.rgb(248, 235, 255) else Color.WHITE
        )
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f
        paint.color = PURPLE
        canvas.drawCircle(x + 20f, y + 31f, 7f, paint)
        if (selected) {
            paint.style = Paint.Style.FILL
            canvas.drawCircle(x + 20f, y + 31f, 3.5f, paint)
        }
        paint.style = Paint.Style.FILL
        text(canvas, title, x + 40f, y + 27f, 12f, NAVY, true)
        text(canvas, subtitle, x + 40f, y + 48f, 10f, NAVY)
        val priceLines = price.lines()
        text(
            canvas,
            priceLines.firstOrNull().orEmpty(),
            x + 312f,
            y + 27f,
            11f,
            NAVY,
            true,
            Paint.Align.RIGHT
        )
        priceLines.getOrNull(1)?.let {
            text(canvas, it, x + 312f, y + 48f, 10f, NAVY, align = Paint.Align.RIGHT)
        }
        if (selected) {
            roundRect(canvas, x + 232f, y - 19f, 84f, 27f, 10f, Color.rgb(221, 199, 255))
            text(canvas, "Most popular", x + 274f, y - 1f, 10f, NAVY, true, Paint.Align.CENTER)
        }
    }

    private fun stat(
        canvas: Canvas,
        x: Float,
        y: Float,
        width: Float,
        icon: IconAsset,
        value: String,
        label: String,
        color: Int,
        iconColor: Int,
    ) {
        roundRect(canvas, x, y, width, 105f, 8f, color)
        vectorIcon(canvas, icon, x + 8f, y + 10f, 20f, iconColor)
        text(canvas, value, x + 9f, y + 68f, 16f, NAVY, true)
        text(canvas, label, x + 9f, y + 87f, 12f, MUTED)
    }

    private fun tab(
        canvas: Canvas,
        label: String,
        x: Float,
        y: Float,
        width: Float,
        selected: Boolean
    ) {
        fill(canvas, x, y, width, 42f, Color.WHITE)
        text(
            canvas,
            label,
            x + width / 2f,
            y + 26f,
            13f,
            if (selected) PURPLE else MUTED,
            selected,
            Paint.Align.CENTER
        )
        if (selected) fill(canvas, x, y + 40f, width, 2f, PURPLE)
    }

    private fun chip(
        canvas: Canvas,
        x: Float,
        y: Float,
        width: Float,
        label: String,
        selected: Boolean
    ) {
        roundRect(
            canvas,
            x,
            y,
            width,
            28f,
            8f,
            if (selected) Color.rgb(246, 238, 255) else Color.WHITE
        )
        strokeRoundRect(canvas, x, y, width, 28f, 8f, if (selected) PURPLE else BORDER, 1f)
        text(
            canvas,
            label,
            x + width / 2f,
            y + 18.5f,
            10.5f,
            if (selected) PURPLE else NAVY,
            selected,
            Paint.Align.CENTER
        )
    }

    private fun drawingTool(
        canvas: Canvas,
        x: Float,
        icon: ToolAsset,
        label: String,
        selected: Boolean,
        enabled: Boolean = true,
    ) {
        val color = when {
            !enabled -> Color.rgb(126, 126, 130)
            selected -> PURPLE
            else -> Color.WHITE
        }
        val drawable = when (icon) {
            ToolAsset.OPACITY -> if (selected) R.drawable.icon_tool_opacity_selected else R.drawable.icon_tool_opacity_unselected
            ToolAsset.CANVAS -> if (selected) R.drawable.icon_tool_canvas_selected else R.drawable.icon_tool_canvas_unselected
            ToolAsset.CAMERA -> if (selected) R.drawable.icon_tool_camera_selected else R.drawable.icon_tool_camera_unselected
            ToolAsset.HIDE -> R.drawable.icon_tool_hide
        }
        val (iconWidth, iconHeight) = when (icon) {
            ToolAsset.OPACITY -> 19.5f to 19.5f
            ToolAsset.CANVAS -> if (selected) 21.5f to 21.5f else 24f to 24f
            ToolAsset.CAMERA -> if (selected) 24f to 24f else 20f to 18f
            ToolAsset.HIDE -> 20f to 20f
        }
        image(
            canvas,
            drawable,
            x - iconWidth / 2f,
            754f - iconHeight / 2f,
            iconWidth,
            iconHeight,
            0f,
            if (enabled) 1f else 0.32f,
        )
        text(canvas, label, x, 785f, 12f, color, selected, Paint.Align.CENTER)
    }

    private fun toggle(canvas: Canvas, x: Float, y: Float, checked: Boolean) {
        roundRect(canvas, x, y, 32f, 18f, 9f, if (checked) PURPLE else LIGHT_GRAY)
        circle(canvas, x + if (checked) 23f else 9f, y + 9f, 7f, Color.WHITE)
    }

    private fun premiumBadge(canvas: Canvas, centerX: Float, centerY: Float) {
        image(canvas, R.drawable.figma_premium_crown, centerX - 24f, centerY - 24f, 48f, 48f, 0f)
    }

    private fun drawSelectedArtwork(
        canvas: Canvas,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        corner: Float
    ) {
        when {
            state.pickedImageUri != null -> image(
                canvas,
                state.pickedImageUri!!,
                x,
                y,
                width,
                height,
                corner
            )

            state.selectedReferenceImage != null -> image(
                canvas,
                state.selectedReferenceImage!!,
                x,
                y,
                width,
                height,
                corner
            )

            else -> image(canvas, R.drawable.topic_chibi, x, y, width, height, corner)
        }
    }

    private fun drawDrawingOverlay(canvas: Canvas) {
        val traceImage = state.selectedTraceImage
        val bitmap = when {
            state.pickedImageUri != null -> contentBitmaps[state.pickedImageUri]
            traceImage?.url != null -> contentBitmaps[traceImage.url]
            traceImage?.localKey != null -> traceImage.localKey?.let(::localAssetDrawable)
                ?.let(bitmaps::get)
            else -> bitmaps[R.drawable.drawing_trace_overlay]
        } ?: return
        val (overlayWidth, overlayHeight) = when (state.cropRatio) {
            DrawingCropRatio.PORTRAIT -> 184f to 328f
            DrawingCropRatio.LANDSCAPE -> 328f to 184f
            DrawingCropRatio.RESET,
            DrawingCropRatio.SQUARE,
                -> 328f to 328f
        }
        val offsetX = if (localTransformActive) localOverlayOffsetX else state.overlayOffsetX
        val offsetY = if (localTransformActive) localOverlayOffsetY else state.overlayOffsetY
        val zoom = if (localTransformActive) localOverlayZoom else state.zoom
        val centerX = 180f + offsetX
        val centerY = 358f + offsetY
        val displayWidth = overlayWidth * zoom
        val displayHeight = overlayHeight * zoom
        val left = centerX - displayWidth / 2f
        val top = centerY - displayHeight / 2f
        canvas.withTranslation(centerX, centerY) {
            canvas.withScale(if (state.overlayFlipped) -zoom else zoom, zoom) {
                drawBitmap(
                    canvas,
                    bitmap,
                    -overlayWidth / 2f,
                    -overlayHeight / 2f,
                    overlayWidth,
                    overlayHeight,
                    16f,
                    if (localOpacityActive) localOpacity else state.opacity,
                    removeLightBackground = state.removeImageEnabled && !bitmap.hasAlpha(),
                )
            }
        }
        strokeRoundRect(
            canvas,
            left,
            top,
            displayWidth,
            displayHeight,
            16f,
            Color.rgb(178, 206, 228),
            0.7f
        )
    }

    private fun statusBar(canvas: Canvas, light: Boolean = false) {
        val realStatusBarVisible = ViewCompat.getRootWindowInsets(this)
            ?.isVisible(WindowInsetsCompat.Type.statusBars()) == true
        if (realStatusBarVisible) return
        val color = if (light) Color.WHITE else NAVY
        val now = System.currentTimeMillis()
        val minute = now / 60_000L
        if (minute != cachedTimeMinute) {
            cachedTimeMinute = minute
            cachedTimeText = timeFormatter.format(Date(now))
        }
        text(canvas, cachedTimeText, 24f, 35f, 13f, color, true)
        circle(canvas, 180f, 24f, 9f, if (light) Color.WHITE else Color.rgb(18, 25, 39))
        paint.color = color
        paint.style = Paint.Style.FILL
        scratchPath.reset()
        scratchPath.moveTo(312f, 34f)
        scratchPath.lineTo(320f, 26f)
        scratchPath.lineTo(320f, 34f)
        scratchPath.close()
        canvas.drawPath(scratchPath, paint)
        roundRect(canvas, 329f, 26f, 4f, 9f, 1f, color)
    }

    @DrawableRes
    private fun settingIconResource(id: String): Int = when (id) {
        "gift" -> R.drawable.icon_setting_gift_figma
        "music" -> R.drawable.icon_setting_music_figma
        "help" -> R.drawable.icon_setting_help_figma
        "subscription" -> R.drawable.icon_setting_subscription_figma
        "update" -> R.drawable.icon_setting_update_figma
        "share" -> R.drawable.icon_setting_share_figma
        "rate" -> R.drawable.icon_setting_rate_figma
        "feedback" -> R.drawable.icon_setting_feedback_figma
        "privacy" -> R.drawable.icon_setting_privacy_figma
        else -> R.drawable.icon_setting_terms_figma
    }

    private fun settingVectorIcon(
        canvas: Canvas,
        @DrawableRes resource: Int,
        x: Float,
        y: Float,
        size: Float,
    ) {
        val drawable = vectorDrawables.getOrPut(resource) {
            checkNotNull(ResourcesCompat.getDrawable(resources, resource, context.theme)).mutate()
        }
        drawable.alpha = 255
        drawable.setBounds(x.toInt(), y.toInt(), (x + size).toInt(), (y + size).toInt())
        drawable.draw(canvas)
    }

    private fun vectorIcon(
        canvas: Canvas,
        icon: IconAsset,
        x: Float,
        y: Float,
        size: Float,
        color: Int
    ) {
        canvas.withTranslation(x, y) {
            canvas.withScale(size / 24f, size / 24f) {
                paint.shader = null
                paint.color = color
                paint.strokeWidth = 1.8f
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeJoin = Paint.Join.ROUND
                paint.style = Paint.Style.STROKE
                scratchPath.reset()
                val path = scratchPath
                when (icon) {
                    IconAsset.FILTER -> {
                        canvas.drawLine(3f, 6f, 21f, 6f, paint)
                        canvas.drawLine(3f, 12f, 21f, 12f, paint)
                        canvas.drawLine(3f, 18f, 21f, 18f, paint)
                        paint.style = Paint.Style.FILL
                        canvas.drawCircle(8f, 6f, 2.4f, paint)
                        canvas.drawCircle(16f, 12f, 2.4f, paint)
                        canvas.drawCircle(10f, 18f, 2.4f, paint)
                    }

                    IconAsset.CHEVRON_RIGHT -> {
                        path.moveTo(8f, 4f)
                        path.lineTo(16f, 12f)
                        path.lineTo(8f, 20f)
                        canvas.drawPath(path, paint)
                    }

                    IconAsset.HEART,
                    IconAsset.HEART_FILLED,
                        -> {
                        path.moveTo(12f, 21f)
                        path.cubicTo(10f, 18.7f, 3f, 14.4f, 3f, 8.8f)
                        path.cubicTo(3f, 4.8f, 8f, 2.6f, 12f, 6.5f)
                        path.cubicTo(16f, 2.6f, 21f, 4.8f, 21f, 8.8f)
                        path.cubicTo(21f, 14.4f, 14f, 18.7f, 12f, 21f)
                        if (icon == IconAsset.HEART_FILLED) paint.style = Paint.Style.FILL
                        canvas.drawPath(path, paint)
                    }

                    IconAsset.OPACITY -> {
                        path.moveTo(12f, 2.5f)
                        path.cubicTo(9f, 7f, 5.5f, 10.7f, 5.5f, 15f)
                        path.cubicTo(5.5f, 19.2f, 8.4f, 22f, 12f, 22f)
                        path.cubicTo(15.6f, 22f, 18.5f, 19.2f, 18.5f, 15f)
                        path.cubicTo(18.5f, 10.7f, 15f, 7f, 12f, 2.5f)
                        canvas.drawPath(path, paint)
                    }

                    IconAsset.CANVAS -> {
                        canvas.drawRoundRect(3f, 3f, 21f, 21f, 2.5f, 2.5f, paint)
                        canvas.drawRect(7f, 7f, 17f, 17f, paint)
                    }

                    IconAsset.CAMERA -> {
                        canvas.drawRoundRect(2f, 6f, 22f, 20f, 3f, 3f, paint)
                        path.moveTo(7f, 6f)
                        path.lineTo(9f, 3f)
                        path.lineTo(15f, 3f)
                        path.lineTo(17f, 6f)
                        canvas.drawPath(path, paint)
                        canvas.drawCircle(12f, 13f, 4f, paint)
                    }

                    IconAsset.HIDE -> {
                        path.moveTo(2f, 12f)
                        path.cubicTo(5f, 6f, 9f, 4f, 12f, 4f)
                        path.cubicTo(16f, 4f, 20f, 7f, 22f, 12f)
                        path.cubicTo(19f, 18f, 15f, 20f, 12f, 20f)
                        path.cubicTo(8f, 20f, 4f, 17f, 2f, 12f)
                        canvas.drawPath(path, paint)
                        canvas.drawLine(4f, 3f, 20f, 21f, paint)
                    }

                    IconAsset.BACK -> {
                        path.moveTo(15f, 5f)
                        path.lineTo(8f, 12f)
                        path.lineTo(15f, 19f)
                        canvas.drawPath(path, paint)
                    }

                    IconAsset.CLOSE -> {
                        canvas.drawLine(5f, 5f, 19f, 19f, paint)
                        canvas.drawLine(19f, 5f, 5f, 19f, paint)
                    }

                    IconAsset.SEARCH -> {
                        canvas.drawCircle(10.5f, 10.5f, 6.5f, paint)
                        canvas.drawLine(15.5f, 15.5f, 21f, 21f, paint)
                    }

                    IconAsset.GALLERY,
                    IconAsset.ADD_IMAGE,
                        -> {
                        canvas.drawRoundRect(3f, 4f, 21f, 20f, 2f, 2f, paint)
                        canvas.drawCircle(8f, 9f, 1.5f, paint)
                        path.moveTo(5f, 18f)
                        path.lineTo(10f, 13f)
                        path.lineTo(13f, 16f)
                        path.lineTo(16f, 12f)
                        path.lineTo(20f, 17f)
                        canvas.drawPath(path, paint)
                        if (icon == IconAsset.ADD_IMAGE) {
                            paint.style = Paint.Style.FILL
                            canvas.drawCircle(18f, 6f, 5f, paint)
                            paint.color = Color.WHITE
                            paint.strokeWidth = 1.5f
                            paint.style = Paint.Style.STROKE
                            canvas.drawLine(15.5f, 6f, 20.5f, 6f, paint)
                            canvas.drawLine(18f, 3.5f, 18f, 8.5f, paint)
                        }
                    }

                    IconAsset.GIFT -> {
                        canvas.drawRect(3f, 9f, 21f, 20f, paint)
                        canvas.drawRect(2f, 6f, 22f, 10f, paint)
                        canvas.drawLine(12f, 6f, 12f, 20f, paint)
                        path.moveTo(12f, 6f)
                        path.cubicTo(10f, 1f, 5f, 2f, 6f, 5f)
                        path.cubicTo(7f, 7f, 10f, 6f, 12f, 6f)
                        path.cubicTo(14f, 1f, 19f, 2f, 18f, 5f)
                        path.cubicTo(17f, 7f, 14f, 6f, 12f, 6f)
                        canvas.drawPath(path, paint)
                    }

                    IconAsset.STAR -> {
                        repeat(10) { index ->
                            val angle = Math.toRadians((-90.0 + index * 36.0))
                            val radius = if (index % 2 == 0) 9f else 4f
                            val px = 12f + kotlin.math.cos(angle).toFloat() * radius
                            val py = 12f + kotlin.math.sin(angle).toFloat() * radius
                            if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
                        }
                        path.close()
                        canvas.drawPath(path, paint)
                    }

                    IconAsset.HOME -> {
                        path.moveTo(3f, 11f)
                        path.lineTo(12f, 3f)
                        path.lineTo(21f, 11f)
                        path.lineTo(19f, 11f)
                        path.lineTo(19f, 21f)
                        path.lineTo(5f, 21f)
                        path.lineTo(5f, 11f)
                        path.close()
                        canvas.drawPath(path, paint)
                    }

                    IconAsset.MUSIC -> {
                        canvas.drawLine(10f, 5f, 19f, 3f, paint)
                        canvas.drawLine(10f, 5f, 10f, 17f, paint)
                        canvas.drawLine(19f, 3f, 19f, 14f, paint)
                        canvas.drawOval(4f, 15f, 10f, 20f, paint)
                        canvas.drawOval(13f, 12f, 19f, 17f, paint)
                    }

                    IconAsset.HELP -> {
                        canvas.drawCircle(12f, 12f, 9f, paint)
                        path.moveTo(9f, 9f)
                        path.cubicTo(9f, 5f, 16f, 5f, 16f, 9f)
                        path.cubicTo(16f, 12f, 12f, 12f, 12f, 15f)
                        canvas.drawPath(path, paint)
                        paint.style = Paint.Style.FILL
                        canvas.drawCircle(12f, 19f, 1f, paint)
                    }

                    IconAsset.SUBSCRIPTION -> {
                        canvas.drawRoundRect(3f, 5f, 21f, 19f, 2f, 2f, paint)
                        canvas.drawLine(3f, 9f, 21f, 9f, paint)
                        canvas.drawLine(7f, 14f, 12f, 14f, paint)
                    }

                    IconAsset.REFRESH -> {
                        path.moveTo(19f, 8f)
                        path.cubicTo(16f, 3f, 8f, 3f, 5f, 8f)
                        path.cubicTo(1f, 14f, 6f, 21f, 12f, 21f)
                        path.cubicTo(17f, 21f, 20f, 18f, 21f, 14f)
                        canvas.drawPath(path, paint)
                        path.reset()
                        path.moveTo(19f, 3f)
                        path.lineTo(19f, 8f)
                        path.lineTo(14f, 8f)
                        canvas.drawPath(path, paint)
                    }

                    IconAsset.SHARE -> {
                        canvas.drawLine(8f, 11f, 16f, 7f, paint)
                        canvas.drawLine(8f, 13f, 16f, 17f, paint)
                        canvas.drawCircle(5f, 12f, 3f, paint)
                        canvas.drawCircle(19f, 5f, 3f, paint)
                        canvas.drawCircle(19f, 19f, 3f, paint)
                    }

                    IconAsset.FEEDBACK -> {
                        canvas.drawRoundRect(3f, 4f, 21f, 18f, 3f, 3f, paint)
                        path.moveTo(8f, 18f)
                        path.lineTo(7f, 22f)
                        path.lineTo(13f, 18f)
                        canvas.drawPath(path, paint)
                        canvas.drawLine(7f, 9f, 17f, 9f, paint)
                        canvas.drawLine(7f, 13f, 14f, 13f, paint)
                    }

                    IconAsset.SHIELD -> {
                        path.moveTo(12f, 3f)
                        path.lineTo(20f, 6f)
                        path.lineTo(19f, 14f)
                        path.cubicTo(18f, 19f, 14f, 21f, 12f, 22f)
                        path.cubicTo(10f, 21f, 6f, 19f, 5f, 14f)
                        path.lineTo(4f, 6f)
                        path.close()
                        canvas.drawPath(path, paint)
                    }

                    IconAsset.DOCUMENT -> {
                        canvas.drawRoundRect(5f, 3f, 19f, 21f, 1.5f, 1.5f, paint)
                        canvas.drawLine(8f, 8f, 16f, 8f, paint)
                        canvas.drawLine(8f, 12f, 16f, 12f, paint)
                        canvas.drawLine(8f, 16f, 14f, 16f, paint)
                    }

                    IconAsset.SKETCH -> {
                        paint.style = Paint.Style.FILL
                        path.moveTo(12f, 3f)
                        path.cubicTo(5.8f, 3f, 2.5f, 7f, 2.5f, 12f)
                        path.cubicTo(2.5f, 17f, 6.1f, 20.8f, 10.4f, 20.8f)
                        path.cubicTo(12.7f, 20.8f, 13.4f, 19.2f, 12.1f, 17.8f)
                        path.cubicTo(10.9f, 16.5f, 11.8f, 14.8f, 14f, 14.8f)
                        path.lineTo(16.4f, 14.8f)
                        path.cubicTo(19.8f, 14.8f, 21.5f, 12.5f, 21.5f, 9.7f)
                        path.cubicTo(21.5f, 5.8f, 17.6f, 3f, 12f, 3f)
                        canvas.drawPath(path, paint)
                        paint.color = Color.WHITE
                        canvas.drawCircle(7.2f, 10.2f, 1.3f, paint)
                        canvas.drawCircle(10.4f, 6.8f, 1.3f, paint)
                        canvas.drawCircle(15f, 7.1f, 1.3f, paint)
                        canvas.drawCircle(17.2f, 11f, 1.3f, paint)
                    }

                    IconAsset.LESSON -> {
                        paint.style = Paint.Style.FILL
                        path.moveTo(2f, 8f)
                        path.lineTo(12f, 3f)
                        path.lineTo(22f, 8f)
                        path.lineTo(12f, 13f)
                        path.close()
                        canvas.drawPath(path, paint)
                        canvas.drawRect(7f, 12f, 17f, 17.5f, paint)
                        canvas.drawRect(20f, 8f, 21.5f, 16f, paint)
                        canvas.drawCircle(20.75f, 17.5f, 1.5f, paint)
                    }

                    IconAsset.CLOCK -> {
                        canvas.drawCircle(12f, 12f, 9f, paint)
                        canvas.drawLine(12f, 7f, 12f, 12f, paint)
                        canvas.drawLine(12f, 12f, 16f, 14f, paint)
                    }
                }
                paint.style = Paint.Style.FILL
                paint.strokeCap = Paint.Cap.BUTT
            }
        }
    }

    @DrawableRes
    private fun topicDrawable(index: Int): Int = when (index % 4) {
        0 -> R.drawable.topic_chibi
        1 -> R.drawable.topic_pixel
        2 -> R.drawable.topic_anime
        else -> R.drawable.topic_cartoon
    }

    private fun whiteBackground(canvas: Canvas) = fill(canvas, 0f, 0f, 360f, 800f, Color.WHITE)

    private fun fill(
        canvas: Canvas,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        @ColorInt color: Int
    ) {
        resetPaintEffects()
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.shader = null
        canvas.drawRect(x, y, x + width, y + height, paint)
    }

    private fun roundRect(
        canvas: Canvas,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radius: Float,
        @ColorInt color: Int
    ) {
        resetPaintEffects()
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.shader = null
        canvas.drawRoundRect(x, y, x + width, y + height, radius, radius, paint)
    }

    private fun strokeRoundRect(
        canvas: Canvas,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radius: Float,
        color: Int,
        stroke: Float
    ) {
        resetPaintEffects()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = stroke
        paint.color = color
        paint.shader = null
        canvas.drawRoundRect(x, y, x + width, y + height, radius, radius, paint)
        paint.style = Paint.Style.FILL
    }

    private fun card(
        canvas: Canvas,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radius: Float,
        border: Int,
        stroke: Float,
        fill: Int = Color.WHITE,
    ) {
        roundRect(canvas, x, y, width, height, radius, fill)
        if (stroke > 0f) strokeRoundRect(canvas, x, y, width, height, radius, border, stroke)
    }

    private fun circle(canvas: Canvas, x: Float, y: Float, radius: Float, color: Int) {
        resetPaintEffects()
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.shader = null
        canvas.drawCircle(x, y, radius, paint)
    }

    private fun line(
        canvas: Canvas,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        color: Int,
        stroke: Float
    ) {
        resetPaintEffects()
        paint.color = color
        paint.strokeWidth = stroke
        paint.style = Paint.Style.STROKE
        canvas.drawLine(x1, y1, x2, y2, paint)
        paint.style = Paint.Style.FILL
    }

    private fun text(
        canvas: Canvas,
        value: String,
        x: Float,
        baseline: Float,
        size: Float,
        color: Int,
        bold: Boolean = false,
        align: Paint.Align = Paint.Align.LEFT,
    ) {
        resetPaintEffects()
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.textSize = size
        paint.textAlign = align
        paint.typeface = if (bold) ROUNDED_BOLD else ROUNDED_REGULAR
        canvas.drawText(value, x, baseline, paint)
    }

    private fun ellipsizedText(
        canvas: Canvas,
        value: String,
        centerX: Float,
        baseline: Float,
        size: Float,
        color: Int,
        maxWidth: Float,
        bold: Boolean,
    ) {
        paint.textSize = size
        paint.typeface = if (bold) ROUNDED_BOLD else ROUNDED_REGULAR
        val display = if (paint.measureText(value) <= maxWidth) {
            value
        } else {
            val suffix = "…"
            val allowed = (maxWidth - paint.measureText(suffix)).coerceAtLeast(0f)
            val count = paint.breakText(value, true, allowed, null).coerceAtLeast(0)
            value.take(count).trimEnd() + suffix
        }
        text(canvas, display, centerX, baseline, size, color, bold, Paint.Align.CENTER)
    }

    private fun multiline(
        canvas: Canvas,
        value: String,
        x: Float,
        baseline: Float,
        size: Float,
        color: Int,
        align: Paint.Align,
        lineHeight: Float,
        bold: Boolean = false,
    ) {
        value.split('\n').forEachIndexed { index, line ->
            text(canvas, line, x, baseline + index * lineHeight, size, color, bold, align)
        }
    }

    private fun wrappedText(
        canvas: Canvas,
        value: String,
        x: Float,
        baseline: Float,
        maxWidth: Float,
        size: Float,
        color: Int,
        lineHeight: Float,
        bold: Boolean = false,
    ) {
        paint.textSize = size
        paint.typeface = if (bold) ROUNDED_BOLD else ROUNDED_REGULAR
        var lineIndex = 0
        value.split('\n').forEach { paragraph ->
            if (paragraph.isBlank()) {
                lineIndex++
                return@forEach
            }
            var remaining = paragraph.trim()
            while (remaining.isNotEmpty()) {
                var count = paint.breakText(remaining, true, maxWidth, null).coerceAtLeast(1)
                if (count < remaining.length) {
                    val wordBoundary = remaining.lastIndexOf(' ', count - 1)
                    if (wordBoundary > 0) count = wordBoundary
                }
                val line = remaining.take(count).trimEnd()
                text(canvas, line, x, baseline + lineIndex * lineHeight, size, color, bold)
                lineIndex++
                remaining = remaining.drop(count).trimStart()
            }
        }
    }

    private fun image(
        canvas: Canvas,
        @DrawableRes drawable: Int,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        corner: Float
    ) {
        val bitmap = bitmaps[drawable] ?: return
        drawBitmap(canvas, bitmap, x, y, width, height, corner)
    }

    private fun imageSourceCrop(
        canvas: Canvas,
        @DrawableRes drawable: Int,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        sourceBottomFraction: Float,
    ) {
        val bitmap = bitmaps[drawable] ?: return
        bitmapSource.set(
            0,
            0,
            bitmap.width,
            (bitmap.height * sourceBottomFraction.coerceIn(0.1f, 1f)).toInt()
        )
        bitmapDestination.set(x, y, x + width, y + height)
        paint.alpha = 255
        paint.colorFilter = null
        canvas.drawBitmap(bitmap, bitmapSource, bitmapDestination, paint)
    }

    private fun image(
        canvas: Canvas,
        @DrawableRes drawable: Int,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        corner: Float,
        alpha: Float,
    ) {
        val bitmap = bitmaps[drawable] ?: return
        drawBitmap(canvas, bitmap, x, y, width, height, corner, alpha)
    }

    private fun image(
        canvas: Canvas,
        image: ContentImage,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        corner: Float
    ) {
        val bitmap = image.url?.let(contentBitmaps::get)
            ?: image.localKey?.let(::localAssetDrawable)?.let(bitmaps::get)
            ?: return
        drawBitmap(canvas, bitmap, x, y, width, height, corner)
    }

    private fun image(
        canvas: Canvas,
        uri: String,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        corner: Float
    ) {
        val bitmap = contentBitmaps[uri] ?: return
        drawBitmap(canvas, bitmap, x, y, width, height, corner)
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
        val sourceRatio = bitmap.width.toFloat() / bitmap.height
        val targetRatio = width / height
        if (sourceRatio > targetRatio) {
            val sourceWidth = (bitmap.height * targetRatio).toInt()
            val left = (bitmap.width - sourceWidth) / 2
            bitmapSource.set(left, 0, left + sourceWidth, bitmap.height)
        } else {
            val sourceHeight = (bitmap.width / targetRatio).toInt()
            val top = (bitmap.height - sourceHeight) / 2
            bitmapSource.set(0, top, bitmap.width, top + sourceHeight)
        }
        bitmapDestination.set(x, y, x + width, y + height)
        paint.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
        paint.colorFilter = if (removeLightBackground) removeLightBackgroundFilter else null
        if (corner > 0f) {
            clipRect.set(bitmapDestination)
            clipPath.reset()
            clipPath.addRoundRect(clipRect, corner, corner, Path.Direction.CW)
            canvas.withClip(clipPath) {
                drawBitmap(bitmap, bitmapSource, bitmapDestination, paint)
            }
        } else {
            canvas.drawBitmap(bitmap, bitmapSource, bitmapDestination, paint)
        }
        paint.alpha = 255
        paint.colorFilter = null
    }

    private fun resetPaintEffects() {
        paint.alpha = 255
        paint.colorFilter = null
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
        "learn_banner" -> R.drawable.learn_banner
        "drawing_trace_overlay" -> R.drawable.drawing_trace_overlay
        else -> R.drawable.topic_chibi
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val transform = layoutTransform()
        val logicalX = (event.x - transform.offsetX) / transform.scale
        val logicalY = (event.y - transform.offsetY) / transform.scale
        val isOpacityControl =
            state.screen == ArDrawScreen.DRAWING_OPACITY && logicalY in 670f..726f
        val canScrollContent = state.screen.isContentScrollable() && maxContentScroll() > 0f &&
                (contentGestureActive || logicalY in contentViewportTop()..contentViewportBottom())
        val canTransform = state.screen.isDrawingScreen() &&
                state.overlayVisible &&
                !state.overlayLocked &&
                logicalY in 104f..678f
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = logicalX
                lastTouchY = logicalY
                lastPinchDistance = 0f
                transformingOverlay = false
                localTransformActive = false
                localOverlayOffsetX = state.overlayOffsetX
                localOverlayOffsetY = state.overlayOffsetY
                localOverlayZoom = state.zoom
                localOpacityActive = isOpacityControl
                localOpacity = state.opacity
                contentGestureActive = false
                tapGestureCancelled = false
                if (localOpacityActive) {
                    localOpacity = opacityForX(logicalX)
                    tapGestureCancelled = true
                    refreshOverlayPreview(opacityOnly = true)
                }
                downTouchX = event.x
                downTouchY = event.y
                velocityTracker?.recycle()
                velocityTracker = if (canScrollContent) VelocityTracker.obtain()
                    .also { it.addMovement(event) } else null
                if (canScrollContent && !contentScroller.isFinished) contentScroller.abortAnimation()
            }

            MotionEvent.ACTION_POINTER_DOWN -> if (canTransform && event.pointerCount >= 2) {
                lastPinchDistance = pointerDistance(event)
                transformingOverlay = true
                localTransformActive = true
                tapGestureCancelled = true
            }

            MotionEvent.ACTION_MOVE -> {
                if (kotlin.math.hypot(event.x - downTouchX, event.y - downTouchY) > touchSlop) {
                    tapGestureCancelled = true
                }
                if (localOpacityActive) {
                    localOpacity = opacityForX(logicalX)
                    refreshOverlayPreview(opacityOnly = true)
                } else if (canScrollContent && event.pointerCount == 1) {
                    velocityTracker?.addMovement(event)
                    if (!contentGestureActive && kotlin.math.abs(event.y - downTouchY) > touchSlop) {
                        contentGestureActive = true
                    }
                    if (contentGestureActive) {
                        val deltaY = logicalY - lastTouchY
                        contentScrollOffset =
                            (contentScrollOffset - deltaY).coerceIn(0f, maxContentScroll())
                        lastTouchY = logicalY
                        invalidateContentViewport()
                    }
                } else if (canTransform) {
                    if (event.pointerCount >= 2) {
                        val distance = pointerDistance(event)
                        if (lastPinchDistance > 0f && distance > 0f) {
                            localOverlayZoom =
                                (localOverlayZoom * (distance / lastPinchDistance)).coerceIn(
                                    0.5f,
                                    3f
                                )
                        }
                        lastPinchDistance = distance
                        transformingOverlay = true
                        localTransformActive = true
                        refreshOverlayPreview()
                    } else {
                        val deltaX = logicalX - lastTouchX
                        val deltaY = logicalY - lastTouchY
                        if (kotlin.math.abs(deltaX) > 0.15f || kotlin.math.abs(deltaY) > 0.15f) {
                            localOverlayOffsetX =
                                (localOverlayOffsetX + deltaX).coerceIn(-150f, 150f)
                            localOverlayOffsetY =
                                (localOverlayOffsetY + deltaY).coerceIn(-220f, 220f)
                            transformingOverlay = true
                            localTransformActive = true
                            refreshOverlayPreview()
                        }
                        lastTouchX = logicalX
                        lastTouchY = logicalY
                    }
                }
            }

            MotionEvent.ACTION_POINTER_UP -> if (canTransform) {
                val remainingIndex = if (event.actionIndex == 0) 1 else 0
                if (remainingIndex < event.pointerCount) {
                    lastTouchX = (event.getX(remainingIndex) - transform.offsetX) / transform.scale
                    lastTouchY = (event.getY(remainingIndex) - transform.offsetY) / transform.scale
                }
                lastPinchDistance = 0f
            }

            MotionEvent.ACTION_UP -> {
                velocityTracker?.addMovement(event)
                if (localOpacityActive) {
                    localOpacity = opacityForX(logicalX)
                    state = state.copy(opacity = localOpacity)
                    onAction(ArDrawAction.ChangeOpacity(localOpacity))
                    refreshOverlayPreview(opacityOnly = true)
                } else if (contentGestureActive) {
                    velocityTracker?.computeCurrentVelocity(1000)
                    val logicalVelocity = -(velocityTracker?.yVelocity ?: 0f) / transform.scale
                    contentScroller.fling(
                        0,
                        contentScrollOffset.toInt(),
                        0,
                        logicalVelocity.toInt(),
                        0,
                        0,
                        0,
                        maxContentScroll().toInt(),
                    )
                    invalidateContentViewport()
                } else if (!transformingOverlay && !tapGestureCancelled) {
                    performClick()
                    if (logicalX in 0f..LOGICAL_WIDTH && logicalY in 0f..LOGICAL_HEIGHT) {
                        dispatchTouch(logicalX / LOGICAL_WIDTH, logicalY / LOGICAL_HEIGHT)
                    }
                }
                if (transformingOverlay && localTransformActive) {
                    state = state.copy(
                        overlayOffsetX = localOverlayOffsetX,
                        overlayOffsetY = localOverlayOffsetY,
                        zoom = localOverlayZoom,
                    )
                    onAction(
                        ArDrawAction.SetOverlayTransform(
                            offsetX = localOverlayOffsetX,
                            offsetY = localOverlayOffsetY,
                            zoom = localOverlayZoom,
                        ),
                    )
                }
                transformingOverlay = false
                localTransformActive = false
                localOpacityActive = false
                contentGestureActive = false
                tapGestureCancelled = false
                lastPinchDistance = 0f
                velocityTracker?.recycle()
                velocityTracker = null
            }

            MotionEvent.ACTION_CANCEL -> {
                transformingOverlay = false
                localTransformActive = false
                localOpacityActive = false
                onOverlayPreview(state.toOverlayPreview())
                contentGestureActive = false
                tapGestureCancelled = false
                lastPinchDistance = 0f
                velocityTracker?.recycle()
                velocityTracker = null
            }
        }
        return true
    }

    override fun dispatchHoverEvent(event: MotionEvent): Boolean =
        accessibilityHelper.dispatchHoverEvent(event) || super.dispatchHoverEvent(event)

    override fun computeScroll() {
        if (contentScroller.computeScrollOffset()) {
            contentScrollOffset = contentScroller.currY.toFloat().coerceIn(0f, maxContentScroll())
            invalidateContentViewport()
        }
    }

    private fun pointerDistance(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        return kotlin.math.hypot(event.getX(0) - event.getX(1), event.getY(0) - event.getY(1))
    }

    private fun opacityForX(logicalX: Float): Float = ((logicalX - 56f) / 248f).coerceIn(0.1f, 1f)

    private fun refreshOverlayPreview(opacityOnly: Boolean = false) {
        onOverlayPreview(
            OverlayPreview(
                offsetX = if (localTransformActive) localOverlayOffsetX else state.overlayOffsetX,
                offsetY = if (localTransformActive) localOverlayOffsetY else state.overlayOffsetY,
                zoom = if (localTransformActive) localOverlayZoom else state.zoom,
                opacity = if (localOpacityActive) localOpacity else state.opacity,
            ),
        )
        if (cameraOverlayExternal) {
            if (opacityOnly) invalidateLogicalRegion(678f, 727f)
        } else {
            invalidateDrawingViewport()
        }
    }

    private fun HomeUiState.toOverlayPreview() = OverlayPreview(
        offsetX = overlayOffsetX,
        offsetY = overlayOffsetY,
        zoom = zoom,
        opacity = opacity,
    )

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDetachedFromWindow() {
        imageScope.cancel()
        bitmaps.values.forEach(Bitmap::recycle)
        bitmaps.clear()
        bitmapCacheBytes = 0L
        contentBitmaps.values.forEach(Bitmap::recycle)
        contentBitmaps.clear()
        contentBitmapCacheBytes = 0L
        super.onDetachedFromWindow()
    }

    private fun cacheDrawable(resource: Int, bitmap: Bitmap) {
        val replaced = bitmaps.put(resource, bitmap)
        if (replaced === bitmap) return
        if (replaced !== bitmap) {
            bitmapCacheBytes -= replaced?.allocationByteCount?.toLong() ?: 0L
            replaced?.recycle()
        }
        bitmapCacheBytes += bitmap.allocationByteCount.toLong()
    }

    private fun trimDrawableCache() {
        while (bitmapCacheBytes > MAX_DRAWABLE_CACHE_BYTES) {
            val victimKey =
                bitmaps.entries.firstOrNull { it.key !in activeDrawableResources }?.key ?: return
            val victim = bitmaps.remove(victimKey) ?: continue
            bitmapCacheBytes -= victim.allocationByteCount.toLong()
            victim.recycle()
        }
    }

    private fun preloadDrawableResources(newState: HomeUiState) {
        val required = startupArtworkResources.toMutableSet()
        fun addImage(image: ContentImage?) {
            image?.localKey?.let(::localAssetDrawable)?.let(required::add)
        }

        fun addArtworkImages(artworks: List<Artwork>) {
            artworks.forEach { artwork ->
                addImage(artwork.image)
                addImage(artwork.traceImage)
            }
        }

        when (newState.screen) {
            ArDrawScreen.ONBOARDING_PROJECTOR -> required += R.drawable.onboarding_projector_art
            ArDrawScreen.ONBOARDING_LIGHTBOX -> required += R.drawable.onboarding_lightbox_art
            ArDrawScreen.ONBOARDING_LESSONS -> required += R.drawable.onboarding_lessons_art
            ArDrawScreen.ONBOARDING_TOPICS -> newState.catalog?.topics.orEmpty()
                .forEach { addImage(it.image) }

            ArDrawScreen.ONBOARDING_LOADING -> newState.catalog?.topics.orEmpty()
                .firstOrNull { it.id in newState.selectedTopicIds }
                ?.let { addImage(it.image) }

            ArDrawScreen.ONBOARDING_PAYWALL -> required += R.drawable.onboarding_paywall_art
            ArDrawScreen.HOME -> newState.catalog?.topics.orEmpty().forEach { addImage(it.image) }
            ArDrawScreen.HOME_SOURCE_MODAL -> Unit
            ArDrawScreen.SEARCH -> newState.catalog?.trendingSearches.orEmpty()
                .forEach { addImage(it.image) }

            ArDrawScreen.SEARCH_RESULTS -> addArtworkImages(newState.visibleArtworks)
            ArDrawScreen.GALLERY, ArDrawScreen.FILTER -> addArtworkImages(newState.visibleGalleryArtworks)
            ArDrawScreen.SETTINGS, ArDrawScreen.SETTINGS_DETAIL -> Unit
            ArDrawScreen.LEARN_PATH -> {
                required += R.drawable.figma_learn_banner
                newState.learningPathLessons.forEach { addImage(it.image) }
            }

            ArDrawScreen.LEARN_CATEGORIES -> {
                required += R.drawable.figma_learn_banner
                newState.catalog?.categories.orEmpty().forEach { addImage(it.image) }
            }

            ArDrawScreen.LEARN_LEVEL_DETAIL -> newState.catalog?.lessons.orEmpty()
                .filter { it.id == newState.selectedLessonId }
                .forEach { addImage(it.image) }

            ArDrawScreen.LEARN_CATEGORY_DETAIL -> {
                val categoryId = newState.selectedCategoryId
                newState.catalog?.lessons.orEmpty().filter { it.categoryId == categoryId }
                    .forEach { addImage(it.image) }
            }

            ArDrawScreen.PROFILE_FAVORITE_EMPTY -> required += R.drawable.figma_profile_empty_favorite
            ArDrawScreen.PROFILE_ALBUM_EMPTY -> required += R.drawable.figma_profile_empty_album
            ArDrawScreen.PROFILE_FAVORITE -> addArtworkImages(
                newState.catalog?.artworks.orEmpty()
                    .filter { it.id in newState.favoriteArtworkIds },
            )

            ArDrawScreen.PROFILE_ALBUM -> Unit
            ArDrawScreen.TUTORIAL_CAMERA, ArDrawScreen.TUTORIAL_SCREEN -> {
                addImage(newState.selectedReferenceImage)
                if (newState.selectedReferenceImage == null) required += R.drawable.topic_chibi
            }

            ArDrawScreen.DRAWING_CANVAS, ArDrawScreen.DRAWING_CAMERA, ArDrawScreen.DRAWING_OPACITY -> {
                if (newState.drawingWithCamera && !newState.cameraPermissionGranted) {
                    required += R.drawable.drawing_camera_background
                }
                addImage(newState.selectedReferenceImage)
                addImage(newState.selectedTraceImage)
                if (newState.selectedReferenceImage == null && newState.pickedImageUri == null) {
                    required += R.drawable.drawing_trace_overlay
                }
                required += drawingToolResources
            }

            ArDrawScreen.DRAWING_COMPLETE -> if (newState.capturedImageUri == null) {
                required += R.drawable.complete_art
            }
        }

        activeDrawableResources = required
        trimDrawableCache()
        val missing = required.filter { it !in bitmaps && loadingDrawableResources.add(it) }
        if (missing.isEmpty()) return
        imageScope.launch {
            val decoded = missing.mapNotNull { resource ->
                runCatching {
                    resource to BitmapFactory.decodeResource(resources, resource)
                        .also(Bitmap::prepareToDraw)
                }.getOrNull()
            }
            withContext(Dispatchers.Main) {
                missing.forEach(loadingDrawableResources::remove)
                decoded.forEach { (resource, bitmap) -> cacheDrawable(resource, bitmap) }
                trimDrawableCache()
                invalidate()
            }
        }
    }

    private fun preloadContentImages(newState: HomeUiState) {
        val remoteUrls = mutableSetOf<String>()
        val contentUris = mutableSetOf<String>()
        fun addImage(image: ContentImage?) {
            image?.url?.let(remoteUrls::add)
        }

        fun addArtworkImages(artworks: List<Artwork>) {
            artworks.forEach { artwork ->
                addImage(artwork.image)
                addImage(artwork.traceImage)
            }
        }

        when (newState.screen) {
            ArDrawScreen.ONBOARDING_TOPICS, ArDrawScreen.HOME ->
                newState.catalog?.topics.orEmpty().forEach { addImage(it.image) }

            ArDrawScreen.ONBOARDING_LOADING -> newState.catalog?.topics.orEmpty()
                .firstOrNull { it.id in newState.selectedTopicIds }
                ?.let { addImage(it.image) }

            ArDrawScreen.SEARCH -> newState.catalog?.trendingSearches.orEmpty()
                .forEach { addImage(it.image) }

            ArDrawScreen.SEARCH_RESULTS -> addArtworkImages(newState.visibleArtworks)
            ArDrawScreen.GALLERY, ArDrawScreen.FILTER -> addArtworkImages(newState.visibleGalleryArtworks)
            ArDrawScreen.LEARN_PATH -> newState.learningPathLessons.forEach { addImage(it.image) }

            ArDrawScreen.LEARN_CATEGORIES -> newState.catalog?.categories.orEmpty()
                .forEach { addImage(it.image) }

            ArDrawScreen.LEARN_LEVEL_DETAIL -> newState.catalog?.lessons.orEmpty()
                .filter { it.id == newState.selectedLessonId }
                .forEach { addImage(it.image) }

            ArDrawScreen.LEARN_CATEGORY_DETAIL -> newState.catalog?.lessons.orEmpty()
                .filter { it.categoryId == newState.selectedCategoryId }
                .forEach { addImage(it.image) }

            ArDrawScreen.PROFILE_FAVORITE -> addArtworkImages(
                newState.catalog?.artworks.orEmpty()
                    .filter { it.id in newState.favoriteArtworkIds },
            )

            ArDrawScreen.PROFILE_ALBUM -> newState.drawings.mapNotNullTo(contentUris) { it.mediaUri }
            ArDrawScreen.TUTORIAL_CAMERA, ArDrawScreen.TUTORIAL_SCREEN,
            ArDrawScreen.DRAWING_CANVAS, ArDrawScreen.DRAWING_CAMERA, ArDrawScreen.DRAWING_OPACITY,
                -> {
                addImage(newState.selectedReferenceImage)
                addImage(newState.selectedTraceImage)
                newState.pickedImageUri?.let(contentUris::add)
            }

            ArDrawScreen.DRAWING_COMPLETE -> newState.capturedImageUri?.let(contentUris::add)
            else -> Unit
        }

        activeContentImageKeys = remoteUrls + contentUris
        trimContentBitmapCache()
        remoteUrls.forEach(::loadRemoteImage)
        contentUris.forEach(::loadContentUri)
    }

    private fun cacheContentBitmap(key: String, decoded: Bitmap) {
        val bitmap = decoded.constrainedTo(MAX_CONTENT_BITMAP_DIMENSION)
        val replaced = contentBitmaps.put(key, bitmap)
        if (replaced === bitmap) return
        contentBitmapCacheBytes -= replaced?.allocationByteCount?.toLong() ?: 0L
        replaced?.recycle()
        contentBitmapCacheBytes += bitmap.allocationByteCount.toLong()
    }

    private fun trimContentBitmapCache() {
        while (contentBitmapCacheBytes > MAX_CONTENT_BITMAP_CACHE_BYTES) {
            val victimKey =
                contentBitmaps.entries.firstOrNull { it.key !in activeContentImageKeys }?.key
                    ?: return
            val victim = contentBitmaps.remove(victimKey) ?: continue
            contentBitmapCacheBytes -= victim.allocationByteCount.toLong()
            victim.recycle()
        }
    }

    private fun Bitmap.constrainedTo(maxDimension: Int): Bitmap {
        val largest = maxOf(width, height)
        if (largest <= maxDimension) return this
        val scale = maxDimension.toFloat() / largest
        val resized = scale(
            (width * scale).toInt().coerceAtLeast(1),
            (height * scale).toInt().coerceAtLeast(1),
        )
        if (resized !== this) recycle()
        return resized
    }

    private fun loadRemoteImage(url: String) {
        if (contentBitmaps.containsKey(url) || !loadingImages.add(url)) return
        imageScope.launch {
            val bitmap =
                runCatching { URL(url).openStream().use(BitmapFactory::decodeStream) }.getOrNull()
            withContext(Dispatchers.Main) {
                loadingImages.remove(url)
                if (bitmap != null) cacheContentBitmap(url, bitmap)
                trimContentBitmapCache()
                invalidate()
            }
        }
    }

    private fun loadContentUri(uri: String) {
        if (contentBitmaps.containsKey(uri) || !loadingImages.add(uri)) return
        imageScope.launch {
            val bitmap = runCatching {
                context.contentResolver.openInputStream(uri.toUri())
                    ?.use(BitmapFactory::decodeStream)
            }.getOrNull()
            withContext(Dispatchers.Main) {
                loadingImages.remove(uri)
                if (bitmap != null) cacheContentBitmap(uri, bitmap)
                trimContentBitmapCache()
                invalidate()
            }
        }
    }

    private fun accessibilityTargets(): List<ArDrawAccessibilityTarget> {
        val result = mutableListOf<ArDrawAccessibilityTarget>()
        var nextId = state.screen.ordinal * ACCESSIBILITY_ID_STRIDE

        fun target(
            label: String,
            left: Float,
            top: Float,
            right: Float,
            bottom: Float,
            selected: Boolean = false,
            checkable: Boolean = false,
            checked: Boolean = false,
            className: String = "android.widget.Button",
            stateDescription: String? = null,
            clipTop: Float = 0f,
            clipBottom: Float = LOGICAL_HEIGHT,
            action: (() -> Unit)? = null,
        ) {
            val clippedTop = kotlin.math.max(top, clipTop)
            val clippedBottom = kotlin.math.min(bottom, clipBottom)
            if (right <= left || clippedBottom <= clippedTop) return
            val bounds = RectF(left, clippedTop, right, clippedBottom)
            result += ArDrawAccessibilityTarget(
                id = nextId++,
                label = label,
                bounds = bounds,
                className = className,
                selected = selected,
                checkable = checkable,
                checked = checked,
                stateDescription = stateDescription,
                onClick = action ?: {
                    dispatchTouch(
                        bounds.centerX() / LOGICAL_WIDTH,
                        bounds.centerY() / LOGICAL_HEIGHT,
                    )
                },
            )
        }

        fun bottomNavigation() {
            val selected = when (state.screen) {
                ArDrawScreen.HOME -> BottomDestination.HOME
                ArDrawScreen.LEARN_PATH, ArDrawScreen.LEARN_CATEGORIES -> BottomDestination.LEARN
                ArDrawScreen.PROFILE_FAVORITE_EMPTY, ArDrawScreen.PROFILE_ALBUM_EMPTY,
                ArDrawScreen.PROFILE_FAVORITE, ArDrawScreen.PROFILE_ALBUM,
                    -> BottomDestination.PROFILE

                else -> BottomDestination.SETTINGS
            }
            listOf(
                BottomDestination.HOME to "Home",
                BottomDestination.LEARN to "Learn",
                BottomDestination.PROFILE to "Profile",
                BottomDestination.SETTINGS to "Settings",
            ).forEachIndexed { index, item ->
                target(
                    label = item.second,
                    left = index * 90f,
                    top = 740f,
                    right = (index + 1) * 90f,
                    bottom = 800f,
                    selected = selected == item.first,
                    action = { onAction(ArDrawAction.OpenBottomDestination(item.first)) },
                )
            }
        }

        when (state.screen) {
            ArDrawScreen.ONBOARDING_PROJECTOR,
            ArDrawScreen.ONBOARDING_LIGHTBOX,
            ArDrawScreen.ONBOARDING_LESSONS,
                -> target("Continue", 16f, 672f, 344f, 780f)

            ArDrawScreen.ONBOARDING_TOPICS -> {
                state.catalog?.topics.orEmpty().take(9).forEachIndexed { index, topic ->
                    val column = index % 3
                    val row = index / 3
                    target(
                        label = topic.title,
                        left = column * 120f + 4f,
                        top = 120f + row * 168f,
                        right = column * 120f + 116f,
                        bottom = 272f + row * 168f,
                        checkable = true,
                        checked = topic.id in state.selectedTopicIds,
                        action = { onAction(ArDrawAction.ToggleTopic(index)) },
                    )
                }
                target("Continue", 16f, 712f, 344f, 792f)
            }

            ArDrawScreen.ONBOARDING_LOADING -> Unit

            ArDrawScreen.ONBOARDING_PAYWALL -> {
                state.catalog?.plans.orEmpty().take(3).forEachIndexed { index, plan ->
                    target(
                        label = "${plan.title}, ${plan.price}",
                        left = 16f,
                        top = 468f + index * 74f,
                        right = 344f,
                        bottom = 530f + index * 74f,
                        checkable = true,
                        checked = state.selectedPlanId == plan.id,
                        action = { onAction(ArDrawAction.SelectPlan(plan.id)) },
                    )
                }
                target("Continue", 16f, 686f, 344f, 746f)
            }

            ArDrawScreen.HOME -> {
                val offset = contentScrollOffset
                target("Search", 16f, 104f - offset, 344f, 168f - offset, clipBottom = 740f)
                target("Add an image", 8f, 208f - offset, 120f, 292f - offset, clipBottom = 740f)
                target("AI emoji mix", 120f, 208f - offset, 240f, 292f - offset, clipBottom = 740f)
                target(
                    "Browse the web",
                    240f,
                    208f - offset,
                    352f,
                    292f - offset,
                    clipBottom = 740f
                )
                state.catalog?.topics.orEmpty().take(HOME_TOPIC_LIMIT)
                    .forEachIndexed { index, topic ->
                    val column = index % 2
                    val row = index / 2
                    target(
                        label = topic.title,
                        left = if (column == 0) 8f else 184f,
                        top = 325f + row * 184f - offset,
                        right = if (column == 0) 176f else 352f,
                        bottom = 497f + row * 184f - offset,
                        clipTop = 96f,
                        clipBottom = 740f,
                        action = { onAction(ArDrawAction.OpenGallery(topic.id)) },
                    )
                }
                bottomNavigation()
            }

            ArDrawScreen.HOME_SOURCE_MODAL -> {
                target("Close", 300f, 240f, 360f, 312f, action = { onAction(ArDrawAction.Back) })
                target(
                    "Gallery",
                    16f,
                    336f,
                    180f,
                    424f,
                    checkable = true,
                    checked = state.selectedDeviceSource == DeviceImageSource.GALLERY,
                    action = { onAction(ArDrawAction.SelectDeviceSource(DeviceImageSource.GALLERY)) },
                )
                target(
                    "Camera",
                    180f,
                    336f,
                    344f,
                    424f,
                    checkable = true,
                    checked = state.selectedDeviceSource == DeviceImageSource.CAMERA,
                    action = { onAction(ArDrawAction.SelectDeviceSource(DeviceImageSource.CAMERA)) },
                )
                target("Choose source", 16f, 424f, 344f, 504f)
            }

            ArDrawScreen.SEARCH -> {
                target("Back", 0f, 48f, 72f, 104f, action = { onAction(ArDrawAction.Back) })
                state.catalog?.trendingSearches.orEmpty().forEachIndexed { index, item ->
                    target(
                        label = item.title,
                        left = 16f,
                        top = 176f + index * 45f,
                        right = 344f,
                        bottom = 221f + index * 45f,
                        action = {
                            onAction(ArDrawAction.SearchQueryChanged(item.title))
                            onAction(ArDrawAction.SubmitSearch)
                        },
                    )
                }
            }

            ArDrawScreen.SEARCH_RESULTS -> {
                target("Back", 0f, 48f, 72f, 104f, action = { onAction(ArDrawAction.Back) })
                state.visibleArtworks.forEachIndexed { index, artwork ->
                    val column = index % 2
                    val row = index / 2
                    target(
                        artwork.title,
                        if (column == 0) 8f else 184f,
                        160f + row * 184f - contentScrollOffset,
                        if (column == 0) 176f else 352f,
                        332f + row * 184f - contentScrollOffset,
                        clipTop = 142f,
                        action = { onAction(ArDrawAction.SelectArtwork(artwork.id)) },
                    )
                }
            }

            ArDrawScreen.GALLERY -> {
                target("Back", 0f, 48f, 72f, 104f, action = { onAction(ArDrawAction.Back) })
                target(
                    "Filters",
                    288f,
                    64f,
                    360f,
                    136f,
                    action = { onAction(ArDrawAction.OpenFilter) })
                GALLERY_QUICK_FILTERS.forEachIndexed { index, (filter, label) ->
                    val left = 16f + GALLERY_FILTER_X_OFFSETS[index]
                    target(
                        label,
                        left,
                        96f,
                        left + GALLERY_FILTER_WIDTHS[index],
                        140f,
                        selected = state.selectedGalleryFilter == filter,
                        action = { onAction(ArDrawAction.SelectGalleryFilter(filter)) },
                    )
                }
                state.visibleGalleryArtworks.forEachIndexed { index, artwork ->
                    val column = index % 2
                    val row = index / 2
                    val left = if (column == 0) 16f else 188f
                    val top = 142f + row * 170f - contentScrollOffset
                    target(
                        label = if (artwork.id in state.favoriteArtworkIds) {
                            "Remove ${artwork.title} from favorites"
                        } else {
                            "Add ${artwork.title} to favorites"
                        },
                        left = left + 108f,
                        top = top,
                        right = left + 156f,
                        bottom = top + 56f,
                        checkable = true,
                        checked = artwork.id in state.favoriteArtworkIds,
                        clipTop = 132f,
                        action = { onAction(ArDrawAction.ToggleFavorite(artwork.id)) },
                    )
                    target(
                        artwork.title,
                        left,
                        top,
                        left + 156f,
                        top + 152f,
                        clipTop = 132f,
                        action = { onAction(ArDrawAction.SelectArtwork(artwork.id)) },
                    )
                }
            }

            ArDrawScreen.FILTER -> {
                target(
                    "Close filters",
                    0f,
                    0f,
                    360f,
                    478f,
                    action = { onAction(ArDrawAction.Back) })
                listOf("Easy", "Medium", "Hard").forEachIndexed { index, value ->
                    target(
                        value,
                        index * 120f,
                        560f,
                        (index + 1) * 120f,
                        615f,
                        checkable = true,
                        checked = state.selectedDifficulty == value,
                        action = { onAction(ArDrawAction.SelectDifficulty(value)) },
                    )
                }
                target(
                    "Line sketch",
                    8f,
                    632f,
                    180f,
                    684f,
                    checkable = true,
                    checked = state.selectedDrawingStyle == ArtworkStyle.LINE_SKETCH,
                    action = { onAction(ArDrawAction.SelectDrawingStyle(ArtworkStyle.LINE_SKETCH)) },
                )
                target(
                    "Color",
                    180f,
                    632f,
                    352f,
                    684f,
                    checkable = true,
                    checked = state.selectedDrawingStyle == ArtworkStyle.COLOR,
                    action = { onAction(ArDrawAction.SelectDrawingStyle(ArtworkStyle.COLOR)) },
                )
                target(
                    "Apply filters",
                    16f,
                    682f,
                    344f,
                    735f,
                    action = { onAction(ArDrawAction.ApplyFilter) })
                target(
                    "Reset filters",
                    16f,
                    736f,
                    344f,
                    792f,
                    action = { onAction(ArDrawAction.ClearFilters) })
            }

            ArDrawScreen.SETTINGS -> {
                target(
                    "Subscription",
                    16f,
                    136f,
                    344f,
                    264f,
                    action = { onAction(ArDrawAction.OpenSetting("subscription")) })
                state.catalog?.settings.orEmpty().forEachIndexed { index, item ->
                    target(
                        item.title,
                        8f,
                        290f + index * 40f,
                        352f,
                        330f + index * 40f,
                        checkable = item.id == "music",
                        checked = item.id == "music" && state.musicEnabled,
                        action = { onAction(ArDrawAction.OpenSetting(item.id)) },
                    )
                }
                bottomNavigation()
            }

            ArDrawScreen.SETTINGS_DETAIL -> {
                target("Back", 0f, 48f, 72f, 112f, action = { onAction(ArDrawAction.Back) })
                target(
                    "Back to Settings",
                    16f,
                    720f,
                    344f,
                    792f,
                    action = { onAction(ArDrawAction.Back) },
                )
            }

            ArDrawScreen.LEARN_PATH, ArDrawScreen.LEARN_CATEGORIES -> {
                target(
                    "Search",
                    16f,
                    104f,
                    344f,
                    184f,
                    action = { onAction(ArDrawAction.OpenSearch) })
                target(
                    "Path",
                    0f,
                    378f,
                    180f,
                    426f,
                    selected = state.screen == ArDrawScreen.LEARN_PATH,
                    action = { onAction(ArDrawAction.OpenLearnPath) },
                )
                target(
                    "Categories",
                    180f,
                    378f,
                    360f,
                    426f,
                    selected = state.screen == ArDrawScreen.LEARN_CATEGORIES,
                    action = { onAction(ArDrawAction.OpenLearnCategories) },
                )
                if (state.screen == ArDrawScreen.LEARN_PATH) {
                    state.learningPathLessons.forEachIndexed { index, lesson ->
                        target(
                            "${lesson.title}, ${lesson.minutes} minutes",
                            16f,
                            436f + index * 70f - contentScrollOffset,
                            344f,
                            498f + index * 70f - contentScrollOffset,
                            clipTop = 426f,
                            clipBottom = 740f,
                            action = { onAction(ArDrawAction.OpenLearnDetail(lesson.id)) },
                        )
                    }
                } else {
                    state.catalog?.categories.orEmpty().forEachIndexed { index, category ->
                        target(
                            "${category.title}, ${category.lessonCount} lessons, ${category.difficulty}",
                            16f,
                            436f + index * 82f - contentScrollOffset,
                            344f,
                            510f + index * 82f - contentScrollOffset,
                            clipTop = 426f,
                            clipBottom = 740f,
                            action = { onAction(ArDrawAction.OpenLearnDetail(category.id)) },
                        )
                    }
                }
                bottomNavigation()
            }

            ArDrawScreen.LEARN_LEVEL_DETAIL -> {
                target("Back", 0f, 48f, 72f, 112f, action = { onAction(ArDrawAction.Back) })
                target(
                    "Start drawing lesson",
                    16f,
                    104f,
                    344f,
                    190f,
                    action = { onAction(ArDrawAction.OpenTutorial) })
            }

            ArDrawScreen.LEARN_CATEGORY_DETAIL -> {
                target("Back", 0f, 48f, 72f, 112f, action = { onAction(ArDrawAction.Back) })
                val categoryId = state.selectedCategoryId
                state.catalog?.lessons.orEmpty()
                    .filter { it.categoryId == categoryId }
                    .forEachIndexed { index, lesson ->
                        target(
                            "${lesson.title}, ${lesson.minutes} minutes",
                            16f,
                            216f + index * 100f,
                            344f,
                            302f + index * 100f,
                            action = { onAction(ArDrawAction.OpenLessonTutorial(lesson.id)) },
                        )
                    }
            }

            ArDrawScreen.PROFILE_FAVORITE_EMPTY, ArDrawScreen.PROFILE_ALBUM_EMPTY,
            ArDrawScreen.PROFILE_FAVORITE, ArDrawScreen.PROFILE_ALBUM,
                -> {
                val favoriteSelected = state.screen in setOf(
                    ArDrawScreen.PROFILE_FAVORITE_EMPTY,
                    ArDrawScreen.PROFILE_FAVORITE,
                )
                target(
                    "Favorites",
                    0f,
                    300f,
                    180f,
                    358f,
                    selected = favoriteSelected,
                    action = { onAction(ArDrawAction.OpenProfileFavorite) })
                target(
                    "Album",
                    180f,
                    300f,
                    360f,
                    358f,
                    selected = !favoriteSelected,
                    action = { onAction(ArDrawAction.OpenProfileAlbum) })
                if (state.screen == ArDrawScreen.PROFILE_FAVORITE) {
                    state.catalog?.artworks.orEmpty().filter { it.id in state.favoriteArtworkIds }
                        .forEachIndexed { index, artwork ->
                            val column = index % 2
                            val row = index / 2
                            target(
                                artwork.title,
                                if (column == 0) 8f else 184f,
                                366f + row * 184f - contentScrollOffset,
                                if (column == 0) 176f else 352f,
                                538f + row * 184f - contentScrollOffset,
                                clipTop = 358f,
                                clipBottom = 740f,
                                action = { onAction(ArDrawAction.SelectArtwork(artwork.id)) },
                            )
                        }
                } else if (state.screen == ArDrawScreen.PROFILE_ALBUM) {
                    state.drawings.forEachIndexed { index, drawing ->
                        val column = index % 2
                        val row = index / 2
                        target(
                            drawing.title,
                            if (column == 0) 16f else 188f,
                            366f + row * 184f - contentScrollOffset,
                            if (column == 0) 172f else 344f,
                            538f + row * 184f - contentScrollOffset,
                            clipTop = 358f,
                            clipBottom = 740f,
                            action = { onAction(ArDrawAction.OpenDrawing(drawing.id)) },
                        )
                    }
                }
                bottomNavigation()
            }

            ArDrawScreen.TUTORIAL_CAMERA, ArDrawScreen.TUTORIAL_SCREEN -> {
                val cameraSelected = state.screen == ArDrawScreen.TUTORIAL_CAMERA
                target("Back", 0f, 48f, 72f, 112f, action = { onAction(ArDrawAction.Back) })
                target(
                    "Draw with camera",
                    if (cameraSelected) 16f else 0f,
                    104f,
                    if (cameraSelected) 244f else 62f,
                    570f,
                    selected = cameraSelected,
                    action = { onAction(ArDrawAction.SelectTutorialMode(true)) })
                target(
                    "Draw with screen",
                    if (cameraSelected) 256f else 74f,
                    104f,
                    if (cameraSelected) 360f else 302f,
                    570f,
                    selected = !cameraSelected,
                    action = { onAction(ArDrawAction.SelectTutorialMode(false)) })
                target(
                    "Start drawing",
                    16f,
                    672f,
                    344f,
                    792f,
                    action = { onAction(ArDrawAction.StartDrawing) })
            }

            ArDrawScreen.DRAWING_CANVAS, ArDrawScreen.DRAWING_CAMERA, ArDrawScreen.DRAWING_OPACITY -> {
                target("Back", 0f, 40f, 112f, 104f, action = { onAction(ArDrawAction.Back) })
                target(
                    "Done",
                    248f,
                    40f,
                    360f,
                    104f,
                    action = { onAction(ArDrawAction.CompleteDrawing) })

                if (state.screen == ArDrawScreen.DRAWING_OPACITY) {
                    target(
                        label = "Opacity",
                        left = 40f,
                        top = 668f,
                        right = 320f,
                        bottom = 726f,
                        className = "android.widget.SeekBar",
                        stateDescription = "${(state.opacity * 100).toInt()} percent",
                        action = {
                            onAction(
                                ArDrawAction.ChangeOpacity(
                                    (state.opacity + 0.1f).coerceAtMost(
                                        1f
                                    )
                                )
                            )
                        },
                    )
                }

                if (state.screen == ArDrawScreen.DRAWING_CAMERA) {
                    when (state.cameraPanel) {
                        DrawingCameraPanel.ZOOM -> {
                            target(
                                "0.5 times zoom",
                                CAMERA_ZOOM_TOUCH_LEFT,
                                632f,
                                CAMERA_ZOOM_TOUCH_SPLIT,
                                680f,
                                selected = state.cameraZoom == 0.5f,
                                action = { onAction(ArDrawAction.SelectCameraZoom(0.5f)) })
                            target(
                                "1.5 times zoom",
                                CAMERA_ZOOM_TOUCH_SPLIT,
                                632f,
                                CAMERA_ZOOM_TOUCH_RIGHT,
                                680f,
                                selected = state.cameraZoom == 1.5f,
                                action = { onAction(ArDrawAction.SelectCameraZoom(1.5f)) })
                        }

                        DrawingCameraPanel.RATIO -> {
                            val ratios = listOf(
                                DrawingCameraRatio.FULL to "Full",
                                DrawingCameraRatio.RATIO_16_9 to "16 by 9",
                                DrawingCameraRatio.RATIO_4_3 to "4 by 3",
                                DrawingCameraRatio.SQUARE to "Square",
                            )
                            val ratioBounds = arrayOf(
                                54f to 116f,
                                116f to 178f,
                                178f to 240f,
                                240f to 302f,
                            )
                            ratios.forEachIndexed { index, item ->
                                target(
                                    item.second,
                                    ratioBounds[index].first,
                                    632f,
                                    ratioBounds[index].second,
                                    680f,
                                    selected = state.cameraRatio == item.first,
                                    action = {
                                        onAction(ArDrawAction.SelectCameraRatio(item.first))
                                    },
                                )
                            }
                        }

                        DrawingCameraPanel.CAPTURE -> target(
                            "Take photo",
                            136f,
                            588f,
                            224f,
                            652f,
                            action = { onAction(ArDrawAction.CaptureDrawing) })

                        DrawingCameraPanel.RECORD -> target(
                            if (state.isRecording) "Stop recording" else "Start recording",
                            136f,
                            588f,
                            224f,
                            652f,
                            action = { onAction(ArDrawAction.ToggleRecording) })

                        DrawingCameraPanel.FLASH, DrawingCameraPanel.NONE -> Unit
                    }
                    listOf(
                        DrawingCameraPanel.ZOOM to "Zoom",
                        DrawingCameraPanel.FLASH to if (state.flashEnabled) "Turn flash off" else "Turn flash on",
                        DrawingCameraPanel.CAPTURE to "Photo",
                        DrawingCameraPanel.RECORD to "Video",
                        DrawingCameraPanel.RATIO to "Aspect ratio",
                    ).forEachIndexed { index, item ->
                        target(
                            item.second,
                            index * 72f,
                            680f,
                            (index + 1) * 72f,
                            726f,
                            selected = state.cameraPanel == item.first,
                            action = { onAction(ArDrawAction.OpenCameraPanel(item.first)) })
                    }
                }

                if (state.screen == ArDrawScreen.DRAWING_CANVAS) {
                    when (state.canvasPanel) {
                        DrawingCanvasPanel.CROP -> {
                            val cropRatios = listOf(
                                DrawingCropRatio.RESET to "Reset crop",
                                DrawingCropRatio.SQUARE to "Square crop",
                                DrawingCropRatio.PORTRAIT to "Portrait crop",
                                DrawingCropRatio.LANDSCAPE to "Landscape crop",
                            )
                            val cropBounds = arrayOf(
                                77f to 122f,
                                122f to 158f,
                                158f to 198f,
                                198f to 246f,
                            )
                            cropRatios.forEachIndexed { index, item ->
                                target(
                                    item.second,
                                    cropBounds[index].first,
                                    632f,
                                    cropBounds[index].second,
                                    680f,
                                    selected = state.cropRatio == item.first,
                                    action = {
                                        onAction(ArDrawAction.SelectCropRatio(item.first))
                                    },
                                )
                            }
                        }

                        DrawingCanvasPanel.GRID -> {
                            val gridBounds = arrayOf(
                                204f to 259f,
                                259f to 302f,
                                302f to 350f,
                            )
                            listOf(3, 4, 5).forEachIndexed { index, size ->
                                target(
                                    "$size by $size grid",
                                    gridBounds[index].first,
                                    632f,
                                    gridBounds[index].second,
                                    680f,
                                    selected = state.gridSize == size,
                                    action = { onAction(ArDrawAction.SelectGridSize(size)) },
                                )
                            }
                        }

                        DrawingCanvasPanel.NONE -> Unit
                    }
                    listOf(
                        "Lock overlay" to { onAction(ArDrawAction.ToggleLock) },
                        "Flip overlay" to { onAction(ArDrawAction.ToggleFlip) },
                        "Remove background" to { onAction(ArDrawAction.ToggleRemoveImage) },
                        "Crop" to { onAction(ArDrawAction.OpenCanvasPanel(DrawingCanvasPanel.CROP)) },
                        "Grid" to { onAction(ArDrawAction.OpenCanvasPanel(DrawingCanvasPanel.GRID)) },
                    ).forEachIndexed { index, item ->
                        target(
                            item.first,
                            index * 72f,
                            680f,
                            (index + 1) * 72f,
                            726f,
                            action = item.second
                        )
                    }
                }

                target(
                    "Opacity",
                    0f,
                    726f,
                    90f,
                    800f,
                    selected = state.screen == ArDrawScreen.DRAWING_OPACITY,
                    action = { onAction(ArDrawAction.SelectDrawingTool(ArDrawScreen.DRAWING_OPACITY)) })
                target(
                    "Canvas",
                    90f,
                    726f,
                    180f,
                    800f,
                    selected = state.screen == ArDrawScreen.DRAWING_CANVAS,
                    action = { onAction(ArDrawAction.SelectDrawingTool(ArDrawScreen.DRAWING_CANVAS)) })
                if (state.drawingWithCamera) {
                    target(
                        "Camera",
                        180f,
                        726f,
                        270f,
                        800f,
                        selected = state.screen == ArDrawScreen.DRAWING_CAMERA,
                        action = { onAction(ArDrawAction.SelectDrawingTool(ArDrawScreen.DRAWING_CAMERA)) })
                }
                target(
                    if (state.overlayVisible) "Hide overlay" else "Show overlay",
                    270f,
                    726f,
                    360f,
                    800f,
                    checkable = true,
                    checked = state.overlayVisible,
                    action = { onAction(ArDrawAction.ToggleOverlay) })
            }

            ArDrawScreen.DRAWING_COMPLETE -> {
                target("Back", 0f, 40f, 160f, 112f, action = { onAction(ArDrawAction.Back) })
                target(
                    "Home",
                    248f,
                    40f,
                    360f,
                    112f,
                    action = { onAction(ArDrawAction.OpenBottomDestination(BottomDestination.HOME)) })
                target(
                    if (state.capturedImageUri == null) "Take a photo" else "Share drawing",
                    16f,
                    656f,
                    344f,
                    728f,
                    action = {
                        if (state.capturedImageUri == null) onAction(ArDrawAction.CaptureDrawing) else onAction(
                            ArDrawAction.ShareDrawing
                        )
                    })
                target(
                    "It’s not finished yet",
                    16f,
                    728f,
                    344f,
                    800f,
                    action = { onAction(ArDrawAction.RetakeDrawing) })
            }
        }
        return result
    }

    private fun dispatchTouch(x: Float, y: Float) {
        val screen = state.screen
        if (screen.hasBottomNavigation() && y > BOTTOM_NAV_TOP) {
            onAction(
                ArDrawAction.OpenBottomDestination(
                    when {
                        x < 0.25f -> BottomDestination.HOME
                        x < 0.50f -> BottomDestination.LEARN
                        x < 0.75f -> BottomDestination.PROFILE
                        else -> BottomDestination.SETTINGS
                    },
                ),
            )
            return
        }
        when (screen) {
            ArDrawScreen.ONBOARDING_PROJECTOR,
            ArDrawScreen.ONBOARDING_LIGHTBOX,
            ArDrawScreen.ONBOARDING_LESSONS,
                -> if (y > 0.84f) onAction(ArDrawAction.Continue)

            ArDrawScreen.ONBOARDING_PAYWALL -> when {
                y in 468f / 800f..678f / 800f -> {
                    val relativeY = y * 800f - 468f
                    if (relativeY % 74f > 62f) return
                    val index = (relativeY / 74f).toInt().coerceIn(0, 2)
                    state.catalog?.plans?.getOrNull(index)
                        ?.let { onAction(ArDrawAction.SelectPlan(it.id)) }
                }

                y in 686f / 800f..746f / 800f -> onAction(ArDrawAction.Continue)
            }

            ArDrawScreen.ONBOARDING_TOPICS -> if (y > 0.89f) {
                onAction(ArDrawAction.Continue)
            } else if (y in 0.15f..0.86f) {
                val col = (x * 3).toInt().coerceIn(0, 2)
                val row = ((y - 0.15f) / 0.21f).toInt().coerceIn(0, 2)
                onAction(ArDrawAction.ToggleTopic(row * 3 + col))
            }

            ArDrawScreen.ONBOARDING_LOADING -> Unit
            ArDrawScreen.HOME -> when {
                y * LOGICAL_HEIGHT + contentScrollOffset in 104f..168f -> onAction(ArDrawAction.OpenSearch)
                y * LOGICAL_HEIGHT + contentScrollOffset in 208f..292f && x < 0.34f ->
                    onAction(ArDrawAction.OpenSourceModal)

                y * LOGICAL_HEIGHT + contentScrollOffset in 208f..292f && x < 0.67f ->
                    onAction(ArDrawAction.OpenAiEmojiMix)

                y * LOGICAL_HEIGHT + contentScrollOffset in 208f..292f ->
                    onAction(ArDrawAction.OpenWebBrowser)

                y * LOGICAL_HEIGHT + contentScrollOffset >= 315f -> {
                    val adjustedY = y + contentScrollOffset / LOGICAL_HEIGHT
                    val index = gridIndex(x, adjustedY, top = 0.40625f, rowHeight = 0.23f)
                    val topicId = state.catalog?.topics?.getOrNull(index)?.id
                    onAction(ArDrawAction.OpenGallery(topicId))
                }
            }

            ArDrawScreen.HOME_SOURCE_MODAL -> when {
                y in 0.30f..0.39f && x > 0.82f -> onAction(ArDrawAction.Back)
                y in 0.42f..0.53f && x < 0.5f -> onAction(
                    ArDrawAction.SelectDeviceSource(
                        DeviceImageSource.GALLERY
                    )
                )

                y in 0.42f..0.53f -> onAction(ArDrawAction.SelectDeviceSource(DeviceImageSource.CAMERA))
                y in 0.53f..0.62f -> onAction(ArDrawAction.ConfirmSource)
                y < 0.31f || y > 0.62f -> onAction(ArDrawAction.Back)
            }

            ArDrawScreen.SEARCH -> when {
                y < 0.12f && x < 0.25f -> onAction(ArDrawAction.Back)
                y in 0.11f..0.19f -> Unit
                y in 0.21f..0.46f -> {
                    val index = ((y * LOGICAL_HEIGHT - 176f) / 45f).toInt().coerceAtLeast(0)
                    state.catalog?.trendingSearches?.getOrNull(index)?.let { item ->
                        onAction(ArDrawAction.SearchQueryChanged(item.title))
                        onAction(ArDrawAction.SubmitSearch)
                    }
                }
            }

            ArDrawScreen.SEARCH_RESULTS -> if (y < 0.12f && x < 0.25f) {
                onAction(ArDrawAction.Back)
            } else if (y > 0.18f) {
                val adjustedY = y + contentScrollOffset / LOGICAL_HEIGHT
                state.visibleArtworks.getOrNull(gridIndex(x, adjustedY, 0.20f, 0.23f))
                    ?.let { onAction(ArDrawAction.SelectArtwork(it.id)) }
            }

            ArDrawScreen.GALLERY -> when {
                y < 0.12f && x < 0.22f -> onAction(ArDrawAction.Back)
                y < 0.12f && x > 0.78f -> onAction(ArDrawAction.OpenFilter)
                y in 0.12f..0.17f -> {
                    val logicalX = x * LOGICAL_WIDTH
                    GALLERY_QUICK_FILTERS.indices.firstOrNull { index ->
                        val left = 16f + GALLERY_FILTER_X_OFFSETS[index]
                        logicalX in left..left + GALLERY_FILTER_WIDTHS[index]
                    }?.let { index ->
                        onAction(
                            ArDrawAction.SelectGalleryFilter(
                                GALLERY_QUICK_FILTERS[index].first
                            ),
                        )
                    }
                }

                y > 0.17f -> {
                    val adjustedY = y + contentScrollOffset / LOGICAL_HEIGHT
                    val index = gridIndex(x, adjustedY, 142f / 800f, 170f / 800f)
                    state.visibleGalleryArtworks.getOrNull(index)?.let { artwork ->
                        val cardX = if (index % 2 == 0) 0.044f else 0.522f
                        if (
                            x > cardX + 108f / 360f &&
                            (adjustedY - 142f / 800f) % (170f / 800f) < 56f / 800f
                        ) {
                            onAction(ArDrawAction.ToggleFavorite(artwork.id))
                        } else {
                            onAction(ArDrawAction.SelectArtwork(artwork.id))
                        }
                    }
                }
            }

            ArDrawScreen.FILTER -> when {
                y < 478f / 800f -> onAction(ArDrawAction.Back)
                y in 560f / 800f..615f / 800f -> onAction(
                    ArDrawAction.SelectDifficulty(
                        when {
                            x < 0.33f -> "Easy"
                            x < 0.67f -> "Medium"
                            else -> "Hard"
                        },
                    ),
                )

                y in 632f / 800f..684f / 800f -> onAction(
                    ArDrawAction.SelectDrawingStyle(
                        if (x < 0.5f) ArtworkStyle.LINE_SKETCH else ArtworkStyle.COLOR,
                    ),
                )

                y in 736f / 800f..792f / 800f -> onAction(ArDrawAction.ClearFilters)
                y in 682f / 800f..735f / 800f -> onAction(ArDrawAction.ApplyFilter)
            }

            ArDrawScreen.SETTINGS -> when {
                y in 0.17f..0.33f -> onAction(ArDrawAction.OpenSetting("subscription"))
                y in 0.36f..BOTTOM_NAV_TOP -> {
                    val index = ((y * 800f - 290f) / 40f).toInt()
                    state.catalog?.settings?.getOrNull(index)
                        ?.let { onAction(ArDrawAction.OpenSetting(it.id)) }
                }
            }

            ArDrawScreen.SETTINGS_DETAIL -> if (y < 0.14f || y > 0.90f) onAction(ArDrawAction.Back)
            ArDrawScreen.LEARN_PATH -> when {
                y in 0.13f..0.23f -> onAction(ArDrawAction.OpenSearch)
                y in 0.4725f..0.525f && x > 0.5f -> onAction(ArDrawAction.OpenLearnCategories)
                y in 0.535f..BOTTOM_NAV_TOP -> {
                    val index =
                        ((y * 800f + contentScrollOffset - 436f) / 70f).toInt().coerceAtLeast(0)
                    state.learningPathLessons.getOrNull(index)
                        ?.let { onAction(ArDrawAction.OpenLearnDetail(it.id)) }
                }
            }

            ArDrawScreen.LEARN_CATEGORIES -> when {
                y in 0.13f..0.23f -> onAction(ArDrawAction.OpenSearch)
                y in 0.4725f..0.525f && x < 0.5f -> onAction(ArDrawAction.OpenLearnPath)
                y in 0.535f..BOTTOM_NAV_TOP -> {
                    val index =
                        ((y * 800f + contentScrollOffset - 436f) / 82f).toInt().coerceAtLeast(0)
                    state.catalog?.categories?.getOrNull(index)
                        ?.let { onAction(ArDrawAction.OpenLearnDetail(it.id)) }
                }
            }

            ArDrawScreen.LEARN_LEVEL_DETAIL -> when {
                y < 0.12f -> onAction(ArDrawAction.Back)
                y in 104f / 800f..190f / 800f -> onAction(ArDrawAction.OpenTutorial)
            }

            ArDrawScreen.LEARN_CATEGORY_DETAIL -> when {
                y < 0.12f -> onAction(ArDrawAction.Back)
                y >= 216f / 800f -> {
                    val relativeY = y * 800f - 216f
                    if (relativeY % 100f > 86f) return
                    val index = (relativeY / 100f).toInt().coerceAtLeast(0)
                    val categoryId = state.selectedCategoryId
                    state.catalog?.lessons.orEmpty()
                        .filter { it.categoryId == categoryId }
                        .getOrNull(index)
                        ?.let { onAction(ArDrawAction.OpenLessonTutorial(it.id)) }
                }
            }

            ArDrawScreen.PROFILE_FAVORITE,
            ArDrawScreen.PROFILE_FAVORITE_EMPTY,
            ArDrawScreen.PROFILE_ALBUM,
            ArDrawScreen.PROFILE_ALBUM_EMPTY,
                -> when {
                y in 0.375f..0.438f && x < 0.5f -> onAction(ArDrawAction.OpenProfileFavorite)
                y in 0.375f..0.438f -> onAction(ArDrawAction.OpenProfileAlbum)
                y in 0.447f..BOTTOM_NAV_TOP && screen in setOf(
                    ArDrawScreen.PROFILE_FAVORITE,
                    ArDrawScreen.PROFILE_FAVORITE_EMPTY,
                ) -> state.catalog?.artworks.orEmpty()
                    .filter { it.id in state.favoriteArtworkIds }
                    .getOrNull(
                        gridIndex(
                            x,
                            y + contentScrollOffset / LOGICAL_HEIGHT,
                            0.4575f,
                            0.23f
                        )
                    )
                    ?.let { onAction(ArDrawAction.SelectArtwork(it.id)) }

                y in 0.447f..BOTTOM_NAV_TOP && screen in setOf(
                    ArDrawScreen.PROFILE_ALBUM,
                    ArDrawScreen.PROFILE_ALBUM_EMPTY,
                ) -> state.drawings.getOrNull(
                    gridIndex(
                        x,
                        y + contentScrollOffset / LOGICAL_HEIGHT,
                        0.4575f,
                        0.23f,
                    ),
                )?.let { onAction(ArDrawAction.OpenDrawing(it.id)) }
            }

            ArDrawScreen.TUTORIAL_CAMERA,
            ArDrawScreen.TUTORIAL_SCREEN,
                -> when {
                y < 0.13f -> onAction(ArDrawAction.Back)
                y in 0.13f..0.72f && screen == ArDrawScreen.TUTORIAL_CAMERA && x < 0.69f ->
                    onAction(ArDrawAction.SelectTutorialMode(true))

                y in 0.13f..0.72f && screen == ArDrawScreen.TUTORIAL_CAMERA ->
                    onAction(ArDrawAction.SelectTutorialMode(false))

                y in 0.13f..0.72f && x < 0.2f ->
                    onAction(ArDrawAction.SelectTutorialMode(true))

                y in 0.13f..0.72f -> onAction(ArDrawAction.SelectTutorialMode(false))
                y > 0.84f -> onAction(ArDrawAction.StartDrawing)
            }

            ArDrawScreen.DRAWING_CANVAS,
            ArDrawScreen.DRAWING_CAMERA,
            ArDrawScreen.DRAWING_OPACITY,
                -> when {
                y < 0.13f && x < 0.35f -> onAction(ArDrawAction.Back)
                y < 0.13f && x > 0.65f -> onAction(ArDrawAction.CompleteDrawing)
                screen == ArDrawScreen.DRAWING_OPACITY && y in 0.835f..0.9075f ->
                    onAction(
                        ArDrawAction.ChangeOpacity(
                            ((x - 56f / 360f) / (248f / 360f)).coerceIn(
                                0.1f,
                                1f
                            )
                        )
                    )

                screen == ArDrawScreen.DRAWING_CAMERA && y in 0.735f..0.815f && x in 0.38f..0.62f &&
                        state.cameraPanel in setOf(
                    DrawingCameraPanel.CAPTURE,
                    DrawingCameraPanel.RECORD
                ) ->
                    when (state.cameraPanel) {
                        DrawingCameraPanel.CAPTURE -> onAction(ArDrawAction.CaptureDrawing)
                        DrawingCameraPanel.RECORD -> onAction(ArDrawAction.ToggleRecording)
                        else -> Unit
                    }

                screen == ArDrawScreen.DRAWING_CAMERA && y in 0.79f..0.85f -> when (state.cameraPanel) {
                    DrawingCameraPanel.ZOOM -> when {
                        x in CAMERA_ZOOM_TOUCH_LEFT / LOGICAL_WIDTH..
                                CAMERA_ZOOM_TOUCH_SPLIT / LOGICAL_WIDTH ->
                            onAction(ArDrawAction.SelectCameraZoom(0.5f))

                        x in CAMERA_ZOOM_TOUCH_SPLIT / LOGICAL_WIDTH..
                                CAMERA_ZOOM_TOUCH_RIGHT / LOGICAL_WIDTH ->
                            onAction(ArDrawAction.SelectCameraZoom(1.5f))

                        else -> Unit
                    }

                    DrawingCameraPanel.RATIO -> when {
                        x < 0.322f -> onAction(ArDrawAction.SelectCameraRatio(DrawingCameraRatio.FULL))
                        x < 0.494f -> onAction(ArDrawAction.SelectCameraRatio(DrawingCameraRatio.RATIO_16_9))
                        x < 0.667f -> onAction(ArDrawAction.SelectCameraRatio(DrawingCameraRatio.RATIO_4_3))
                        else -> onAction(ArDrawAction.SelectCameraRatio(DrawingCameraRatio.SQUARE))
                    }

                    else -> Unit
                }

                screen == ArDrawScreen.DRAWING_CAMERA && y in 0.85f..0.9075f -> when {
                    x < 0.215f -> onAction(ArDrawAction.OpenCameraPanel(DrawingCameraPanel.ZOOM))
                    x < 0.40f -> onAction(ArDrawAction.OpenCameraPanel(DrawingCameraPanel.FLASH))
                    x < 0.595f -> onAction(ArDrawAction.OpenCameraPanel(DrawingCameraPanel.CAPTURE))
                    x < 0.79f -> onAction(ArDrawAction.OpenCameraPanel(DrawingCameraPanel.RECORD))
                    else -> onAction(ArDrawAction.OpenCameraPanel(DrawingCameraPanel.RATIO))
                }

                screen == ArDrawScreen.DRAWING_CANVAS && y in 0.79f..0.85f -> when (state.canvasPanel) {
                    DrawingCanvasPanel.CROP -> when {
                        x < 0.34f -> onAction(ArDrawAction.SelectCropRatio(DrawingCropRatio.RESET))
                        x < 0.44f -> onAction(ArDrawAction.SelectCropRatio(DrawingCropRatio.SQUARE))
                        x < 0.55f -> onAction(ArDrawAction.SelectCropRatio(DrawingCropRatio.PORTRAIT))
                        else -> onAction(ArDrawAction.SelectCropRatio(DrawingCropRatio.LANDSCAPE))
                    }

                    DrawingCanvasPanel.GRID -> when {
                        x < 0.72f -> onAction(ArDrawAction.SelectGridSize(3))
                        x < 0.84f -> onAction(ArDrawAction.SelectGridSize(4))
                        else -> onAction(ArDrawAction.SelectGridSize(5))
                    }

                    DrawingCanvasPanel.NONE -> Unit
                }

                screen == ArDrawScreen.DRAWING_CANVAS && y in 0.85f..0.9075f -> when {
                    x < 0.195f -> onAction(ArDrawAction.ToggleLock)
                    x < 0.387f -> onAction(ArDrawAction.ToggleFlip)
                    x < 0.612f -> onAction(ArDrawAction.ToggleRemoveImage)
                    x < 0.808f -> onAction(ArDrawAction.OpenCanvasPanel(DrawingCanvasPanel.CROP))
                    else -> onAction(ArDrawAction.OpenCanvasPanel(DrawingCanvasPanel.GRID))
                }

                y > 0.9075f && x < 0.25f -> onAction(ArDrawAction.SelectDrawingTool(ArDrawScreen.DRAWING_OPACITY))
                y > 0.9075f && x < 0.50f -> onAction(ArDrawAction.SelectDrawingTool(ArDrawScreen.DRAWING_CANVAS))
                y > 0.9075f && x < 0.75f && state.drawingWithCamera ->
                    onAction(ArDrawAction.SelectDrawingTool(ArDrawScreen.DRAWING_CAMERA))

                y > 0.9075f -> onAction(ArDrawAction.ToggleOverlay)
            }

            ArDrawScreen.DRAWING_COMPLETE -> when {
                y < 0.14f && x < 0.7f -> onAction(ArDrawAction.Back)
                y < 0.14f -> onAction(ArDrawAction.OpenBottomDestination(BottomDestination.HOME))
                y > 0.91f -> onAction(ArDrawAction.RetakeDrawing)
                y > 0.82f -> if (state.capturedImageUri == null) {
                    onAction(ArDrawAction.CaptureDrawing)
                } else {
                    onAction(ArDrawAction.ShareDrawing)
                }
            }
        }
    }

    private fun gridIndex(x: Float, y: Float, top: Float, rowHeight: Float): Int {
        val column = if (x < 0.5f) 0 else 1
        val row = ((y - top) / rowHeight).toInt().coerceAtLeast(0)
        return row * 2 + column
    }

    private fun ArDrawScreen.isContentScrollable(): Boolean = this in CONTENT_SCROLL_SCREENS

    private fun isContentItemVisible(top: Float, itemHeight: Float): Boolean {
        if (!state.screen.isContentScrollable()) return true
        val visibleTop = contentViewportTop() + contentScrollOffset
        val visibleBottom = contentViewportBottom() + contentScrollOffset
        return top + itemHeight >= visibleTop && top <= visibleBottom
    }

    private fun invalidateContentViewport() {
        accessibilityHelper.invalidateRoot()
        invalidateLogicalRegion(contentViewportTop(), contentViewportBottom())
    }

    private fun invalidateDrawingViewport() {
        invalidateLogicalRegion(103f, 727f)
    }

    private fun invalidateLogicalRegion(top: Float, bottom: Float) {
        val transform = layoutTransform()
        val physicalTop = (transform.offsetY + top * transform.scale).toInt().coerceAtLeast(0)
        val physicalBottom =
            (transform.offsetY + bottom * transform.scale).toInt().coerceAtMost(height)
        postInvalidateOnAnimation(0, physicalTop, width, physicalBottom)
    }

    private fun contentViewportTop(): Float = when (state.screen) {
        ArDrawScreen.SEARCH_RESULTS -> 142f
        ArDrawScreen.GALLERY -> 132f
        ArDrawScreen.LEARN_PATH, ArDrawScreen.LEARN_CATEGORIES -> 420f
        ArDrawScreen.PROFILE_FAVORITE, ArDrawScreen.PROFILE_ALBUM -> 358f
        else -> 0f
    }

    private fun contentViewportBottom(): Float = when (state.screen) {
        ArDrawScreen.HOME,
        ArDrawScreen.LEARN_PATH, ArDrawScreen.LEARN_CATEGORIES,
        ArDrawScreen.PROFILE_FAVORITE, ArDrawScreen.PROFILE_ALBUM,
            -> 742f

        else -> 800f
    }

    private fun maxContentScroll(): Float {
        val contentBottom = when (state.screen) {
            ArDrawScreen.HOME -> {
                val count = state.catalog?.topics.orEmpty().take(HOME_TOPIC_LIMIT).size
                val rows = (count + 1) / 2
                if (rows == 0) {
                    contentViewportBottom()
                } else {
                    325f + (rows - 1) * 184f + 172f
                }
            }
            ArDrawScreen.SEARCH_RESULTS -> {
                val rows = (state.visibleArtworks.size + 1) / 2
                if (rows == 0) contentViewportBottom() else 160f + (rows - 1) * 184f + 172f
            }

            ArDrawScreen.GALLERY -> {
                val rows = (state.visibleGalleryArtworks.size + 1) / 2
                if (rows == 0) {
                    contentViewportBottom()
                } else {
                    142f + (rows - 1) * 170f + 152f
                }
            }

            ArDrawScreen.LEARN_PATH -> {
                val count = state.learningPathLessons.size
                if (count == 0) contentViewportBottom() else 436f + (count - 1) * 70f + 62f
            }

            ArDrawScreen.LEARN_CATEGORIES -> {
                val count = state.catalog?.categories.orEmpty().size
                if (count == 0) contentViewportBottom() else 436f + (count - 1) * 82f + 74f
            }

            ArDrawScreen.PROFILE_FAVORITE -> {
                val count = state.favoriteArtworkIds.size
                val rows = (count + 1) / 2
                if (rows == 0) contentViewportBottom() else 366f + (rows - 1) * 184f + 172f
            }

            ArDrawScreen.PROFILE_ALBUM -> {
                val rows = (state.drawings.size + 1) / 2
                if (rows == 0) contentViewportBottom() else 366f + (rows - 1) * 184f + 172f
            }

            else -> contentViewportBottom()
        }
        return (contentBottom - contentViewportBottom() + 10f).coerceAtLeast(0f)
    }

    private fun ArDrawScreen.hasBottomNavigation(): Boolean = this in BOTTOM_NAV_SCREENS

    private fun ArDrawScreen.isDrawingScreen(): Boolean = this in DRAWING_SCREENS

    private data class OnboardingPage(
        val title: String,
        val subtitle: String,
        @param:DrawableRes val artwork: Int,
        val featureTitle: String,
        val featureBody: String,
        val accent: Int,
        val dot: Int,
    )

    private enum class IconAsset {
        FILTER,
        CHEVRON_RIGHT,
        HEART,
        HEART_FILLED,
        OPACITY,
        CANVAS,
        CAMERA,
        HIDE,
        BACK,
        CLOSE,
        SEARCH,
        GALLERY,
        GIFT,
        STAR,
        HOME,
        MUSIC,
        HELP,
        SUBSCRIPTION,
        REFRESH,
        SHARE,
        FEEDBACK,
        SHIELD,
        DOCUMENT,
        SKETCH,
        LESSON,
        CLOCK,
        ADD_IMAGE,
    }

    private enum class ToolAsset { OPACITY, CANVAS, CAMERA, HIDE }

    private companion object {
        const val LOGICAL_WIDTH = 360f
        const val LOGICAL_HEIGHT = 800f
        const val HEADER_RIGHT_BLEED = 1f
        const val CAMERA_ZOOM_HALF_X = 8f
        const val CAMERA_ZOOM_ONE_AND_HALF_X = 50f
        const val CAMERA_ZOOM_OPTION_WIDTH = 34f
        const val CAMERA_ZOOM_TOUCH_LEFT = 2f
        const val CAMERA_ZOOM_TOUCH_SPLIT = 47f
        const val CAMERA_ZOOM_TOUCH_RIGHT = 92f
        const val HOME_TOPIC_LIMIT = 6
        const val BOTTOM_NAV_TOP = 0.925f
        const val ACCESSIBILITY_ID_STRIDE = 1_000
        const val MAX_DRAWABLE_CACHE_BYTES = 24L * 1024L * 1024L
        const val MAX_CONTENT_BITMAP_CACHE_BYTES = 32L * 1024L * 1024L
        const val MAX_CONTENT_BITMAP_DIMENSION = 2_048
        val ROUNDED_REGULAR: Typeface = Typeface.create("sans-serif-rounded", Typeface.NORMAL)
        val ROUNDED_BOLD: Typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
        val NAVY = Color.rgb(28, 40, 61)
        val MUTED = Color.rgb(104, 123, 151)
        val PURPLE = Color.rgb(147, 94, 237)
        val PINK = Color.rgb(247, 73, 137)
        val GREEN = Color.rgb(44, 184, 115)
        val BLUE = Color.rgb(44, 151, 238)
        val ORANGE = Color.rgb(255, 142, 46)
        val YELLOW = Color.rgb(255, 206, 47)
        val BORDER = Color.rgb(220, 227, 237)
        val LIGHT_GRAY = Color.rgb(232, 237, 244)
        val CONTENT_SCROLL_SCREENS = setOf(
            ArDrawScreen.HOME,
            ArDrawScreen.SEARCH_RESULTS,
            ArDrawScreen.GALLERY,
            ArDrawScreen.LEARN_PATH,
            ArDrawScreen.LEARN_CATEGORIES,
            ArDrawScreen.PROFILE_FAVORITE,
            ArDrawScreen.PROFILE_ALBUM,
        )
        val BOTTOM_NAV_SCREENS = setOf(
            ArDrawScreen.HOME,
            ArDrawScreen.SETTINGS,
            ArDrawScreen.LEARN_PATH,
            ArDrawScreen.LEARN_CATEGORIES,
            ArDrawScreen.PROFILE_FAVORITE_EMPTY,
            ArDrawScreen.PROFILE_ALBUM_EMPTY,
            ArDrawScreen.PROFILE_FAVORITE,
            ArDrawScreen.PROFILE_ALBUM,
        )
        val DRAWING_SCREENS = setOf(
            ArDrawScreen.DRAWING_CANVAS,
            ArDrawScreen.DRAWING_CAMERA,
            ArDrawScreen.DRAWING_OPACITY,
        )
        val drawingToolResources = setOf(
            R.drawable.icon_tool_opacity_selected,
            R.drawable.icon_tool_opacity_unselected,
            R.drawable.icon_tool_canvas_selected,
            R.drawable.icon_tool_canvas_unselected,
            R.drawable.icon_tool_camera_selected,
            R.drawable.icon_tool_camera_unselected,
            R.drawable.icon_tool_hide,
            R.drawable.icon_eye_off,
            R.drawable.icon_eye,
        )
        val GALLERY_QUICK_FILTERS = arrayOf(
            GalleryFilter.SAVED to "Save",
            GalleryFilter.ALL to "All",
            GalleryFilter.JUJUTSU_KAISEN to "Jujutsu Kaisen",
            GalleryFilter.ONE_PIECE to "One Piece",
            GalleryFilter.DORAEMON to "Doraemon",
        )
        val GALLERY_FILTER_WIDTHS = floatArrayOf(44f, 44f, 92f, 82f, 82f)
        val GALLERY_FILTER_X_OFFSETS = floatArrayOf(0f, 52f, 104f, 204f, 294f)
        val bottomNavigationItems = listOf(
            Triple(BottomDestination.HOME, R.drawable.icon_nav_home, "Home"),
            Triple(BottomDestination.LEARN, R.drawable.icon_nav_learn, "Learn"),
            Triple(BottomDestination.PROFILE, R.drawable.icon_nav_profile, "Profile"),
            Triple(BottomDestination.SETTINGS, R.drawable.icon_nav_settings, "Setting"),
        )
        val startupArtworkResources = intArrayOf(
            R.drawable.figma_app_icon,
            R.drawable.figma_premium_crown,
            R.drawable.figma_source_gallery,
            R.drawable.figma_source_ai,
            R.drawable.figma_source_web,
            R.drawable.figma_topic_chibi,
            R.drawable.figma_topic_pixel,
            R.drawable.figma_topic_anime,
            R.drawable.figma_topic_cartoon,
            R.drawable.figma_topic_world_cup,
            R.drawable.figma_topic_lego,
            R.drawable.icon_nav_home,
            R.drawable.icon_nav_learn,
            R.drawable.icon_nav_profile,
            R.drawable.icon_nav_settings,
        )
    }
}
