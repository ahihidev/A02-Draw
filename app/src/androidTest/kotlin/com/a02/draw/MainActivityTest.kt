package com.a02.draw

import android.os.ParcelFileDescriptor
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @Test
    fun launchShowsHomeDestination() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageName = instrumentation.targetContext.packageName
        val command = "am start -W -n $packageName/${MainActivity::class.java.name}"
        ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(command),
        ).bufferedReader().use { it.readText() }
        instrumentation.waitForIdleSync()

        onView(withId(com.a02.draw.feature.home.R.id.ar_draw_view))
            .check(matches(isDisplayed()))
    }

    @Test
    fun canvasExposesLabeledVirtualControls() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageName = instrumentation.targetContext.packageName
        val command = "am start -W -n $packageName/${MainActivity::class.java.name}"
        ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(command),
        ).bufferedReader().use { it.readText() }
        instrumentation.waitForIdleSync()

        val root = instrumentation.uiAutomation.rootInActiveWindow
        val host = root.findAccessibilityNodeInfosByViewId("$packageName:id/ar_draw_view").first()
        val controls = mutableListOf<android.view.accessibility.AccessibilityNodeInfo>()
        for (index in 0 until host.childCount) {
            val child = host.getChild(index)
            if (child != null) controls.add(child)
        }

        assertTrue("The canvas must expose virtual controls", controls.isNotEmpty())
        assertTrue(
            "Every virtual control must have a label",
            controls.all { !it.contentDescription.isNullOrBlank() },
        )
        assertTrue(
            "Virtual controls must be actionable",
            controls.any { it.isClickable && it.isFocusable },
        )
    }
}
