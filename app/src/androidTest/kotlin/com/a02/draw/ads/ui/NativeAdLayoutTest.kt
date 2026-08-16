package com.a02.draw.ads.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.a02.draw.MainActivity
import com.a02.draw.R
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAdView
import com.kiro.sdk.ads.KiroNativeAdView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt
import com.a02.draw.feature.home.R as HomeR

@RunWith(AndroidJUnit4::class)
class NativeAdLayoutTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val inflater: LayoutInflater = LayoutInflater.from(context)

    @Test
    fun mediumLayoutHasKiroAssetsAndKeepsFigmaHeightWhenAssetsAreMissing() {
        val root = inflateAndMeasure(R.layout.layout_native_ad_medium, 375, 196)

        assertKiroAssetContract(root)
        assertEquals(dp(196), root.measuredHeight)
        assertEquals(dp(160), root.findViewById<View>(R.id.ad_media_container).measuredWidth)
        assertHeightIsStableWhenAssetsAreMissing(root, 375, 196)
    }

    @Test
    fun largeLayoutHasKiroAssetsAndKeepsFigmaHeightWhenAssetsAreMissing() {
        val root = inflateAndMeasure(R.layout.layout_native_ad_large, 375, 270)

        assertKiroAssetContract(root)
        assertEquals(dp(270), root.measuredHeight)
        assertEquals(dp(142), root.findViewById<View>(R.id.ad_media_container).measuredHeight)
        assertHeightIsStableWhenAssetsAreMissing(root, 375, 270)
    }

    @Test
    fun fullLayoutUsesFlexibleMediaAndFixedBottomPanel() {
        val root = inflateAndMeasure(R.layout.layout_native_ad_full, 375, 812)

        assertKiroAssetContract(root)
        val panel = root.findViewById<View>(R.id.ad_full_panel)
        val media = root.findViewById<View>(R.id.ad_media_container)
        assertEquals(dp(136), panel.measuredHeight)
        assertEquals(panel.top, media.bottom)
        assertEquals(root.measuredHeight, panel.bottom)
        assertHeightIsStableWhenAssetsAreMissing(root, 375, 812)
    }

    @Test
    fun fullHostProvidesAccessibleCloseOutsideNativeAdView() {
        val host = inflater.inflate(R.layout.view_native_ad_full, null, false) as NativeAdHostView
        val close = host.findViewById<View>(R.id.native_ad_close)
        host.visibility = View.VISIBLE
        close.visibility = View.VISIBLE
        measure(host, 375, 812)
        val nativeAdView = findDescendant(host, NativeAdView::class.java)

        assertNotNull(close)
        assertEquals(dp(48), close.measuredWidth)
        assertEquals(dp(48), close.measuredHeight)
        assertTrue(close.contentDescription.isNotBlank())
        assertFalse(isDescendantOf(close, nativeAdView))
    }

    @Test
    fun skeletonsAreExcludedFromAccessibility() {
        listOf(
            R.layout.view_native_ad_medium,
            R.layout.view_native_ad_large,
            R.layout.view_native_ad_full,
        ).forEach { layoutResId ->
            val host = inflater.inflate(layoutResId, null, false) as NativeAdHostView
            val skeleton = findDescendant(host, NativeAdSkeletonView::class.java)

            assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, skeleton.importantForAccessibility)
        }
    }

    @Test
    fun startupNativeScreensKeepContinueButtonAboveTheLargeHostAt320dp() {
        val themedInflater =
            LayoutInflater.from(ContextThemeWrapper(context, R.style.Theme_A02Draw))
        listOf(
            R.layout.activity_language to R.id.language_ad,
            R.layout.screen_onboarding_lightbox to R.id.onboarding_ad,
            R.layout.screen_onboarding_lessons to R.id.onboarding_ad,
        ).forEach { (layoutResId, hostId) ->
            val root = themedInflater.inflate(layoutResId, null, false)
            val host = root.findViewById<NativeAdHostView>(hostId)
            host.showLoadingForTesting()
            measure(root, 320, 740)
            val continueButton = root.findViewById<View>(
                if (layoutResId == R.layout.activity_language) {
                    R.id.language_continue
                } else {
                    R.id.continue_button
                },
            )

            assertEquals(dp(270), host.measuredHeight)
            assertTrue(
                "Native host width is not full width in $layoutResId",
                kotlin.math.abs(root.measuredWidth - host.width) <= 1,
            )
            assertTrue(
                "Native host start is inset in $layoutResId",
                kotlin.math.abs(host.left) <= 1
            )
            assertTrue(
                "Native host end is inset in $layoutResId",
                kotlin.math.abs(root.measuredWidth - host.right) <= 1,
            )
            assertEquals(
                "Native host is not bottom flush in $layoutResId",
                root.measuredHeight,
                host.bottom
            )
            assertEquals(
                "Continue gap is not 24dp in $layoutResId",
                dp(24),
                host.top - continueButton.bottom,
            )
            assertTrue(
                "Continue overlaps native host in $layoutResId",
                continueButton.bottom <= host.top
            )
            assertTrue("Continue is clipped in $layoutResId", continueButton.top >= 0)
        }
    }

    @Test
    fun bottomNativeContainersAreFullWidthBottomFlushAndKeepButtonGap() {
        val themedInflater =
            LayoutInflater.from(ContextThemeWrapper(context, R.style.Theme_A02Draw))

        data class ScreenCase(
            val layoutResId: Int,
            val buttonId: Int?,
            val format: NativeAdFormat,
            val visibleGroupId: Int? = null,
        )

        val cases = listOf(
            ScreenCase(
                R.layout.screen_onboarding_topics,
                R.id.continue_button,
                NativeAdFormat.LARGE
            ),
            ScreenCase(HomeR.layout.screen_emoji_mix_home, null, NativeAdFormat.MEDIUM),
            ScreenCase(
                HomeR.layout.screen_emoji_mix_picker,
                HomeR.id.create_button,
                NativeAdFormat.MEDIUM
            ),
            ScreenCase(
                HomeR.layout.screen_emoji_mix_result,
                HomeR.id.new_button,
                NativeAdFormat.MEDIUM,
                HomeR.id.result_group,
            ),
            ScreenCase(
                HomeR.layout.screen_emoji_mix_result,
                HomeR.id.retry_button,
                NativeAdFormat.MEDIUM,
                HomeR.id.error_group,
            ),
            ScreenCase(
                HomeR.layout.screen_settings_detail,
                HomeR.id.back_to_settings,
                NativeAdFormat.MEDIUM
            ),
        )

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        cases.forEach { (layoutResId, buttonId, format, visibleGroupId) ->
            instrumentation.runOnMainSync {
                val root = themedInflater.inflate(layoutResId, null, false) as ViewGroup
                if (layoutResId == HomeR.layout.screen_emoji_mix_result) {
                    root.findViewById<View>(HomeR.id.progress).visibility = View.GONE
                    root.findViewById<View>(HomeR.id.result_group).visibility = View.GONE
                    root.findViewById<View>(HomeR.id.error_group).visibility = View.GONE
                    visibleGroupId?.let { root.findViewById<View>(it).visibility = View.VISIBLE }
                }
                val containerId = if (layoutResId == R.layout.screen_onboarding_topics) {
                    R.id.native_ad_container
                } else {
                    HomeR.id.native_ad_container
                }
                val container = root.findViewById<FrameLayout>(containerId).apply {
                    visibility = View.VISIBLE
                }
                val host = NativeAdHostView(container.context, requestedFormat = format).apply {
                    showLoadingForTesting()
                }
                container.addView(
                    host,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
                measure(root, 320, 740)
                assertNativeHierarchyFillsHost(host)
                assertTrue(
                    "Container width is not full width in $layoutResId",
                    kotlin.math.abs(root.measuredWidth - container.width) <= 1,
                )
                assertTrue(
                    "Container start is inset in $layoutResId",
                    kotlin.math.abs(container.left) <= 1
                )
                assertTrue(
                    "Container end is inset in $layoutResId",
                    kotlin.math.abs(root.measuredWidth - container.right) <= 1,
                )
                assertEquals(
                    "Container is not bottom flush in $layoutResId",
                    root.measuredHeight,
                    container.bottom
                )
                buttonId?.let { id ->
                    val button = root.findViewById<View>(id)
                    val buttonBounds = android.graphics.Rect(0, 0, button.width, button.height)
                    root.offsetDescendantRectToMyCoords(button, buttonBounds)
                    assertEquals(
                        "Button gap is not 24dp in $layoutResId",
                        dp(24),
                        container.top - buttonBounds.bottom,
                    )
                }
            }
        }
    }

    @Test
    fun everyInAppNativePlacementAndItsSdkContentFillTheScreenWidth() {
        val themedInflater =
            LayoutInflater.from(ContextThemeWrapper(context, R.style.Theme_A02Draw))
        val cases = listOf(
            HomeR.layout.screen_main_home,
            HomeR.layout.screen_gallery,
            HomeR.layout.screen_search,
            HomeR.layout.screen_learn,
            HomeR.layout.screen_learn_detail,
            HomeR.layout.screen_settings,
            HomeR.layout.screen_settings_detail,
            HomeR.layout.screen_profile,
            HomeR.layout.screen_tutorial,
            HomeR.layout.screen_emoji_mix_home,
            HomeR.layout.screen_emoji_mix_picker,
            HomeR.layout.screen_emoji_mix_result,
        )

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            cases.forEach { layoutResId ->
                val root = themedInflater.inflate(layoutResId, null, false) as ViewGroup
                val container = root.findViewById<FrameLayout>(HomeR.id.native_ad_container).apply {
                    visibility = View.VISIBLE
                }
                val host = NativeAdHostView(
                    container.context,
                    requestedFormat = NativeAdFormat.MEDIUM,
                ).apply {
                    showLoadingForTesting()
                }
                container.addView(
                    host,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )

                measure(root, 320, 740)

                assertEquals("Container start is inset in $layoutResId", 0, container.left)
                assertEquals(
                    "Container width is clipped in $layoutResId",
                    root.measuredWidth,
                    container.measuredWidth,
                )
                assertEquals("Host width is clipped in $layoutResId", container.width, host.width)
                assertNativeHierarchyFillsHost(host)
            }
        }
    }

    @Test
    fun skeletonStopsWhenDetachedAndVisualEvidenceCanBeCaptured() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val evidenceDirectory = File(
            instrumentation.targetContext.getExternalFilesDir(null),
            "native-ad-qa",
        ).apply { mkdirs() }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            listOf(
                NativeAdFormat.MEDIUM to R.layout.view_native_ad_medium,
                NativeAdFormat.LARGE to R.layout.view_native_ad_large,
                NativeAdFormat.FULL to R.layout.view_native_ad_full,
            ).forEach { (format, layoutResId) ->
                lateinit var host: NativeAdHostView
                lateinit var skeleton: NativeAdSkeletonView
                scenario.onActivity { activity ->
                    host = activity.layoutInflater.inflate(
                        layoutResId,
                        null,
                        false
                    ) as NativeAdHostView
                    skeleton = findDescendant(host, NativeAdSkeletonView::class.java)
                    activity.setContentView(host)
                    host.showLoadingForTesting()
                }
                instrumentation.waitForIdleSync()
                assertEquals(ValueAnimator.areAnimatorsEnabled(), skeleton.isShimmerRunning)
                if (format == NativeAdFormat.FULL) {
                    assertTrue(host.paddingTop > 0)
                    assertTrue(host.paddingBottom > 0)
                }
                captureScreenshot(
                    instrumentation,
                    File(evidenceDirectory, "${format.name.lowercase()}-loading.png")
                )

                scenario.onActivity {
                    populatePreviewAssets(host)
                    host.showLoadedForTesting()
                }
                SystemClock.sleep(200)
                instrumentation.waitForIdleSync()
                captureScreenshot(
                    instrumentation,
                    File(evidenceDirectory, "${format.name.lowercase()}-loaded.png")
                )

                scenario.onActivity {
                    host.findViewById<View>(R.id.ad_app_icon).visibility = View.GONE
                    host.findViewById<View>(R.id.ad_body).visibility = View.INVISIBLE
                    host.findViewById<View>(R.id.ad_call_to_action).visibility = View.INVISIBLE
                }
                instrumentation.waitForIdleSync()
                captureScreenshot(
                    instrumentation,
                    File(evidenceDirectory, "${format.name.lowercase()}-missing-assets.png")
                )

                scenario.onActivity { (host.parent as? ViewGroup)?.removeView(host) }
                instrumentation.waitForIdleSync()
                assertFalse(skeleton.isShimmerRunning)
            }
        }
    }

    private fun assertKiroAssetContract(root: View) {
        assertTrue(root is NativeAdView)
        assertTrue(root.findViewById<View>(R.id.ad_headline) is TextView)
        assertTrue(root.findViewById<View>(R.id.ad_body) is TextView)
        assertTrue(root.findViewById<View>(R.id.ad_call_to_action) is Button)
        assertTrue(root.findViewById<View>(R.id.ad_app_icon) is ImageView)
        assertTrue(root.findViewById<View>(R.id.ad_media) is MediaView)
        assertNotNull(root.findViewById<View>(R.id.ad_attribution))
    }

    private fun populatePreviewAssets(host: NativeAdHostView) {
        host.findViewById<TextView>(R.id.ad_headline).text =
            "BetterSleep: Sleep tracker with sleep better"
        host.findViewById<TextView>(R.id.ad_body).text =
            "White Noise is proven to make you sleep better. Sleep sounds help you relax."
        host.findViewById<Button>(R.id.ad_call_to_action).text = "Install"
        host.findViewById<ImageView>(R.id.ad_app_icon).apply {
            background = ColorDrawable(context.getColor(R.color.native_ad_primary))
            visibility = View.VISIBLE
        }
    }

    private fun captureScreenshot(
        instrumentation: android.app.Instrumentation,
        destination: File,
    ) {
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        FileOutputStream(destination).use { output ->
            assertTrue(screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
        }
        assertTrue(destination.length() > 0L)
    }

    private fun assertHeightIsStableWhenAssetsAreMissing(
        root: View,
        widthDp: Int,
        heightDp: Int,
    ) {
        val expectedHeight = root.measuredHeight
        listOf(
            R.id.ad_headline to View.INVISIBLE,
            R.id.ad_body to View.INVISIBLE,
            R.id.ad_call_to_action to View.INVISIBLE,
            R.id.ad_app_icon to View.GONE,
            R.id.ad_media to View.GONE,
        ).forEach { (viewId, missingVisibility) ->
            val asset = root.findViewById<View>(viewId)
            val originalVisibility = asset.visibility
            asset.visibility = missingVisibility
            measure(root, widthDp, heightDp)
            assertEquals("Asset $viewId changed root height", expectedHeight, root.measuredHeight)
            assertChildrenStayInsideRoot(root)
            asset.visibility = originalVisibility
        }
    }

    private fun assertChildrenStayInsideRoot(root: View) {
        fun check(view: View) {
            if (view.visibility != View.GONE) {
                assertTrue("${view.javaClass.simpleName} starts before root", view.left >= 0)
                assertTrue(
                    "${view.javaClass.simpleName} extends past root",
                    view.right <= root.measuredWidth
                )
                assertTrue("${view.javaClass.simpleName} starts above root", view.top >= 0)
                assertTrue(
                    "${view.javaClass.simpleName} extends below root",
                    view.bottom <= root.measuredHeight
                )
            }
            if (view is ViewGroup) {
                repeat(view.childCount) { index -> check(view.getChildAt(index)) }
            }
        }
        check(root)
    }

    private fun assertNativeHierarchyFillsHost(host: NativeAdHostView) {
        val kiroView = findDescendant(host, KiroNativeAdView::class.java)
        val googleView = kiroView.googleNativeAdView

        assertEquals("Kiro view starts inside the host", 0, kiroView.left)
        assertEquals("Kiro view is narrower than the host", host.width, kiroView.width)
        assertEquals("NativeAdView starts inside the Kiro view", 0, googleView.left)
        assertEquals(
            "NativeAdView is narrower than the Kiro view",
            kiroView.width,
            googleView.width
        )
    }

    private fun inflateAndMeasure(layoutResId: Int, widthDp: Int, heightDp: Int): View =
        inflater.inflate(layoutResId, null, false).also { measure(it, widthDp, heightDp) }

    private fun measure(view: View, widthDp: Int, heightDp: Int) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(dp(widthDp), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(dp(heightDp), View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    private fun <T : View> findDescendant(root: ViewGroup, type: Class<T>): T {
        repeat(root.childCount) { index ->
            val child = root.getChildAt(index)
            if (type.isInstance(child)) return type.cast(child)!!
            if (child is ViewGroup) {
                findDescendantOrNull(child, type)?.let { return it }
            }
        }
        throw AssertionError("No ${type.simpleName} found under ${root.javaClass.simpleName}")
    }

    private fun <T : View> findDescendantOrNull(root: ViewGroup, type: Class<T>): T? {
        repeat(root.childCount) { index ->
            val child = root.getChildAt(index)
            if (type.isInstance(child)) return type.cast(child)
            if (child is ViewGroup) {
                findDescendantOrNull(child, type)?.let { return it }
            }
        }
        return null
    }

    private fun isDescendantOf(view: View, possibleAncestor: ViewGroup): Boolean {
        var parent = view.parent
        while (parent is View) {
            if (parent === possibleAncestor) return true
            parent = parent.parent
        }
        return false
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).roundToInt()
}
