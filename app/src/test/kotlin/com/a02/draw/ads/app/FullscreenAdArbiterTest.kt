package com.a02.draw.ads.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FullscreenAdArbiterTest {
    @Test
    fun `navigation interstitial requires two eligible actions`() {
        val arbiter = FullscreenAdArbiter()

        arbiter.recordNavigation(isEligible = true)
        assertFalse(arbiter.tryAcquireNavigation(nowMillis = 60_000L))
        arbiter.recordNavigation(isEligible = false)
        assertEquals(1, arbiter.actionCount())
        arbiter.recordNavigation(isEligible = true)

        assertTrue(arbiter.tryAcquireNavigation(nowMillis = 60_000L))
    }

    @Test
    fun `navigation interstitial respects full screen cooldown`() {
        val arbiter = FullscreenAdArbiter()
        arbiter.recordFullScreenShown(nowMillis = 100_000L)
        repeat(2) { arbiter.recordNavigation(isEligible = true) }

        assertFalse(arbiter.tryAcquireNavigation(nowMillis = 129_999L))
        assertTrue(arbiter.tryAcquireNavigation(nowMillis = 130_000L))
    }

    @Test
    fun `release resets action count and mutex`() {
        val arbiter = FullscreenAdArbiter()
        repeat(2) { arbiter.recordNavigation(isEligible = true) }
        assertTrue(arbiter.tryAcquireNavigation(nowMillis = 30_000L))

        arbiter.release()

        assertEquals(0, arbiter.actionCount())
        arbiter.recordNavigation(isEligible = true)
        assertFalse(arbiter.tryAcquireNavigation(nowMillis = 60_000L))
    }
}
