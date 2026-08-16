package com.a02.draw.ads.startup

import org.junit.Assert.assertEquals
import org.junit.Test

class StartupNativeInventoryPolicyTest {
    @Test
    fun `language two floor is preferred for every startup placement`() {
        val languageHigh = key(StartupAdPlacement.LANGUAGE, AdFloor.TWO_FLOOR)
        val localHigh = key(StartupAdPlacement.ONBOARDING_FULL, AdFloor.TWO_FLOOR)
        val inventory = mapOf(
            localHigh to NativeInventoryOrigin.PLACEMENT,
            languageHigh to NativeInventoryOrigin.LANGUAGE_SPARE,
        )

        assertEquals(
            listOf(languageHigh, localHigh),
            StartupNativeInventoryPolicy.startupCandidates(
                StartupAdPlacement.ONBOARDING_FULL,
                inventory,
            ),
        )
    }

    @Test
    fun `splash inventory is used before current placement inventory`() {
        val splashMedium = key(StartupAdPlacement.ONBOARDING_LIGHTBOX, AdFloor.MEDIUM_FLOOR)
        val localHigh = key(StartupAdPlacement.ONBOARDING_LESSONS, AdFloor.TWO_FLOOR)
        val inventory = mapOf(
            localHigh to NativeInventoryOrigin.PLACEMENT,
            splashMedium to NativeInventoryOrigin.SPLASH_PRELOAD,
        )

        assertEquals(
            listOf(splashMedium, localHigh),
            StartupNativeInventoryPolicy.startupCandidates(
                StartupAdPlacement.ONBOARDING_LESSONS,
                inventory,
            ),
        )
    }

    @Test
    fun `main screens can only borrow language spare and splash inventory`() {
        val languageHigh = key(StartupAdPlacement.LANGUAGE, AdFloor.TWO_FLOOR)
        val splashAll = key(StartupAdPlacement.ONBOARDING_FULL, AdFloor.ALL_PRICES)
        val placementOnly = key(StartupAdPlacement.ONBOARDING_LESSONS, AdFloor.TWO_FLOOR)
        val inventory = mapOf(
            placementOnly to NativeInventoryOrigin.PLACEMENT,
            splashAll to NativeInventoryOrigin.SPLASH_PRELOAD,
            languageHigh to NativeInventoryOrigin.LANGUAGE_SPARE,
        )

        assertEquals(
            listOf(languageHigh, splashAll),
            StartupNativeInventoryPolicy.sharedCandidates(inventory),
        )
    }

    @Test
    fun `native refresh interval is fifteen seconds`() {
        assertEquals(15_000L, TripleFloorTiming.NATIVE_REFRESH_MILLIS)
    }

    private fun key(
        placement: StartupAdPlacement,
        floor: AdFloor,
    ) = AdInventoryKey(placement, floor)
}
