package com.a02.draw

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.annotation.IdRes
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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

        onView(withId(com.a02.draw.feature.home.R.id.home_scroll))
            .check(matches(isDisplayed()))

        onView(withId(com.a02.draw.feature.home.R.id.nav_learn)).perform(click())
        waitUntilDisplayed(com.a02.draw.feature.home.R.id.category_list)
    }

    @Test
    fun onboardingIsASeparateXmlActivity() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageName = instrumentation.targetContext.packageName
        val command = "am start -W -n $packageName/${OnboardingActivity::class.java.name}"
        ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(command),
        ).bufferedReader().use { it.readText() }
        instrumentation.waitForIdleSync()

        onView(withId(R.id.continue_button)).check(matches(isDisplayed()))
    }

    private fun waitUntilDisplayed(@IdRes viewId: Int, timeoutMillis: Long = 10_000) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis
        var lastFailure: Throwable? = null
        while (SystemClock.elapsedRealtime() < deadline) {
            try {
                onView(withId(viewId)).check(matches(isDisplayed()))
                return
            } catch (failure: Throwable) {
                lastFailure = failure
                SystemClock.sleep(100)
            }
        }
        throw AssertionError("View $viewId was not displayed within $timeoutMillis ms", lastFailure)
    }
}
