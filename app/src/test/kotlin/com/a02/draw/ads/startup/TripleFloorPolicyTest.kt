package com.a02.draw.ads.startup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripleFloorPolicyTest {
    @Test
    fun `startup native preload prioritizes language then intro screens`() {
        assertEquals(
            listOf(
                StartupAdPlacement.LANGUAGE,
                StartupAdPlacement.ONBOARDING_LIGHTBOX,
                StartupAdPlacement.ONBOARDING_FULL,
                StartupAdPlacement.ONBOARDING_LESSONS,
            ),
            startupNativePreloadOrder,
        )
    }

    @Test
    fun `requests use required stagger offsets`() {
        assertEquals(0L, TripleFloorTiming.floorDelay(AdFloor.TWO_FLOOR))
        assertEquals(500L, TripleFloorTiming.floorDelay(AdFloor.MEDIUM_FLOOR))
        assertEquals(1_000L, TripleFloorTiming.floorDelay(AdFloor.ALL_PRICES))
    }

    @Test
    fun `inventory identity includes placement and floor`() {
        val keys = setOf(
            AdInventoryKey(StartupAdPlacement.LANGUAGE, AdFloor.TWO_FLOOR),
            AdInventoryKey(StartupAdPlacement.LANGUAGE, AdFloor.MEDIUM_FLOOR),
            AdInventoryKey(StartupAdPlacement.ONBOARDING_LIGHTBOX, AdFloor.TWO_FLOOR),
        )

        assertEquals(3, keys.size)
    }

    @Test
    fun `no fill and invalid request permanently stop a floor`() {
        val policy = TripleFloorPolicy()
        val noFill = AdInventoryKey(StartupAdPlacement.LANGUAGE, AdFloor.TWO_FLOOR)
        val invalid = AdInventoryKey(StartupAdPlacement.SPLASH, AdFloor.MEDIUM_FLOOR)
        policy.markLoading(noFill)
        policy.markLoading(invalid)

        assertFalse(policy.onFailure(noFill, AdLoadFailure.NO_FILL))
        assertFalse(policy.onFailure(invalid, AdLoadFailure.INVALID_REQUEST))
        assertTrue(policy.isTerminal(noFill))
        assertTrue(policy.isTerminal(invalid))
    }

    @Test
    fun `splash high floor retries at most ten times`() {
        val policy = TripleFloorPolicy(splashTwoFloorRetryLimit = 10)
        val key = AdInventoryKey(StartupAdPlacement.SPLASH, AdFloor.TWO_FLOOR)

        repeat(10) { index ->
            assertTrue(policy.markLoading(key))
            assertTrue("retry ${index + 1}", policy.onFailure(key, AdLoadFailure.INTERNAL_ERROR))
        }
        assertTrue(policy.markLoading(key))
        assertFalse(policy.onFailure(key, AdLoadFailure.TIMEOUT))
        assertEquals(11, policy.retryCount(key))
        assertTrue(policy.isTerminal(key))
    }

    @Test
    fun `language high floor has unlimited retry and resets to idle after success consumption`() {
        val policy = TripleFloorPolicy()
        val key = AdInventoryKey(StartupAdPlacement.LANGUAGE, AdFloor.TWO_FLOOR)

        repeat(25) {
            assertTrue(policy.markLoading(key))
            assertTrue(policy.onFailure(key, AdLoadFailure.INTERNAL_ERROR))
        }
        assertTrue(policy.markLoading(key))
        policy.markReady(key)
        policy.markConsumed(key)

        assertEquals(FloorRequestState.IDLE, policy.state(key))
        assertTrue(policy.markLoading(key))
    }

    @Test
    fun `stale loading completion cannot start a second single flight`() {
        val policy = TripleFloorPolicy()
        val key = AdInventoryKey(StartupAdPlacement.ONBOARDING_FULL, AdFloor.ALL_PRICES)

        assertTrue(policy.markLoading(key))
        assertFalse(policy.markLoading(key))
        policy.markReady(key)
        assertFalse(policy.markLoading(key))
    }
}
