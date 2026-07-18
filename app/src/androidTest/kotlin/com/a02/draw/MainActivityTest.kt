package com.a02.draw

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.os.ParcelFileDescriptor
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

        onView(withId(com.a02.draw.feature.home.R.id.home_root))
            .check(matches(isDisplayed()))
    }
}
