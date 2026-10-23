package com.a02.draw.ads.app

import android.os.Bundle
import android.os.SystemClock
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.a02.draw.MainActivity
import com.a02.draw.R
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RewardedFlowUiTest {
    @Test
    fun emojiRewardPromptCanReopenImmediatelyAfterDeclineAndBackDismissal() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val navHost = activity.supportFragmentManager
                    .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
                navHost.navController.navigate(
                    R.id.emojiMixPickerFragment,
                    Bundle().apply { putString("mode", "MIX_3") },
                )
            }
            SystemClock.sleep(800L)

            onView(withText("😀")).perform(click())
            val promptIsVisible = try {
                onView(withId(R.id.rewardTitle)).check(matches(isDisplayed()))
                true
            } catch (_: NoMatchingViewException) {
                false
            }
            if (!promptIsVisible) {
                onView(withText(com.a02.draw.feature.home.R.string.reward_ad_unavailable))
                    .check(matches(isDisplayed()))
                return@use
            }
            onView(withText(R.string.reward_unlock_emoji_message)).check(matches(isDisplayed()))
            onView(withId(R.id.notNowButton)).perform(click())

            onView(withText("😀")).perform(click())
            onView(withId(R.id.rewardTitle)).check(matches(isDisplayed()))

            pressBack()
            onView(withId(R.id.rewardTitle)).check(doesNotExist())

            onView(withText("😀")).perform(click())
            onView(withId(R.id.rewardTitle)).check(matches(isDisplayed()))
        }
    }
}
