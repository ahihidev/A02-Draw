package com.a02.draw.ads.startup

import androidx.annotation.StringRes
import com.a02.draw.R

enum class AdFloor {
    TWO_FLOOR,
    MEDIUM_FLOOR,
    ALL_PRICES,
}

enum class StartupAdPlacement {
    SPLASH,
    LANGUAGE,
    ONBOARDING_LIGHTBOX,
    ONBOARDING_FULL,
    ONBOARDING_LESSONS,
}

enum class StartupAdFormat {
    INTERSTITIAL,
    NATIVE,
}

internal val startupNativePreloadOrder = listOf(
    StartupAdPlacement.LANGUAGE,
    StartupAdPlacement.ONBOARDING_LIGHTBOX,
    StartupAdPlacement.ONBOARDING_FULL,
    StartupAdPlacement.ONBOARDING_LESSONS,
)

enum class AdLoadFailure {
    NETWORK_ERROR,
    NO_FILL,
    INVALID_REQUEST,
    INTERNAL_ERROR,
    TIMEOUT,
    UNKNOWN,
}

data class TripleFloorAdSpec(
    val placement: StartupAdPlacement,
    val format: StartupAdFormat,
    @param:StringRes val twoFloorId: Int,
    @param:StringRes val mediumFloorId: Int,
    @param:StringRes val allPricesId: Int,
) {
    @StringRes
    fun idFor(floor: AdFloor): Int = when (floor) {
        AdFloor.TWO_FLOOR -> twoFloorId
        AdFloor.MEDIUM_FLOOR -> mediumFloorId
        AdFloor.ALL_PRICES -> allPricesId
    }
}

internal val startupAdSpecs = listOf(
    TripleFloorAdSpec(
        StartupAdPlacement.SPLASH,
        StartupAdFormat.INTERSTITIAL,
        R.string.inter_splash_2f,
        R.string.inter_splash_mf,
        R.string.inter_splash_all,
    ),
    TripleFloorAdSpec(
        StartupAdPlacement.LANGUAGE,
        StartupAdFormat.NATIVE,
        R.string.native_language_2f,
        R.string.native_language_mf,
        R.string.native_language_all,
    ),
    TripleFloorAdSpec(
        StartupAdPlacement.ONBOARDING_LIGHTBOX,
        StartupAdFormat.NATIVE,
        R.string.native_onboarding_2_2f,
        R.string.native_onboarding_2_mf,
        R.string.native_onboarding_2_all,
    ),
    TripleFloorAdSpec(
        StartupAdPlacement.ONBOARDING_FULL,
        StartupAdFormat.NATIVE,
        R.string.native_onboarding_3_full_2f,
        R.string.native_onboarding_3_full_mf,
        R.string.native_onboarding_3_full_all,
    ),
    TripleFloorAdSpec(
        StartupAdPlacement.ONBOARDING_LESSONS,
        StartupAdFormat.NATIVE,
        R.string.native_onboarding_4_2f,
        R.string.native_onboarding_4_mf,
        R.string.native_onboarding_4_all,
    ),
)

data class NativeAdLease(
    val placement: StartupAdPlacement,
    val floor: AdFloor,
    val loadedAtElapsedMillis: Long,
)

internal data class AdInventoryKey(
    val placement: StartupAdPlacement,
    val floor: AdFloor,
)
