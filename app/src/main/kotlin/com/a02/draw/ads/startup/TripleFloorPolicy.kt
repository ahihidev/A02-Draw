package com.a02.draw.ads.startup

internal enum class FloorRequestState {
    IDLE,
    LOADING,
    READY,
    STOPPED,
}

internal class TripleFloorPolicy(
    private val splashTwoFloorRetryLimit: Int = 10,
) {
    private val states = mutableMapOf<AdInventoryKey, FloorRequestState>()
    private val retryCounts = mutableMapOf<AdInventoryKey, Int>()

    fun state(key: AdInventoryKey): FloorRequestState = states[key] ?: FloorRequestState.IDLE

    fun markLoading(key: AdInventoryKey): Boolean {
        if (state(key) != FloorRequestState.IDLE) return false
        states[key] = FloorRequestState.LOADING
        return true
    }

    fun markReady(key: AdInventoryKey) {
        states[key] = FloorRequestState.READY
    }

    fun markConsumed(key: AdInventoryKey) {
        states[key] = FloorRequestState.IDLE
        if (key.placement == StartupAdPlacement.SPLASH && key.floor == AdFloor.TWO_FLOOR) {
            retryCounts[key] = 0
        }
    }

    fun markExpired(key: AdInventoryKey) {
        if (state(key) == FloorRequestState.READY) states[key] = FloorRequestState.IDLE
    }

    fun onFailure(key: AdInventoryKey, failure: AdLoadFailure): Boolean {
        when (failure) {
            AdLoadFailure.NO_FILL,
            AdLoadFailure.INVALID_REQUEST,
                -> {
                states[key] = FloorRequestState.STOPPED
                return false
            }

            else -> states[key] = FloorRequestState.IDLE
        }

        if (key.floor != AdFloor.TWO_FLOOR) {
            if (key.floor == AdFloor.MEDIUM_FLOOR) states[key] = FloorRequestState.STOPPED
            return false
        }
        return when (key.placement) {
            StartupAdPlacement.SPLASH -> {
                val nextCount = retryCounts.getOrDefault(key, 0) + 1
                retryCounts[key] = nextCount
                val canRetry = nextCount <= splashTwoFloorRetryLimit
                if (!canRetry) states[key] = FloorRequestState.STOPPED
                canRetry
            }

            StartupAdPlacement.LANGUAGE -> true
            else -> false
        }
    }

    fun isTerminal(key: AdInventoryKey): Boolean = state(key) == FloorRequestState.STOPPED

    fun retryCount(key: AdInventoryKey): Int = retryCounts.getOrDefault(key, 0)
}

internal object TripleFloorTiming {
    const val TWO_FLOOR_DELAY_MILLIS = 0L
    const val MEDIUM_FLOOR_DELAY_MILLIS = 500L
    const val ALL_PRICES_DELAY_MILLIS = 1_000L
    const val INTERNAL_RETRY_DELAY_MILLIS = 3_000L
    const val RELOAD_DELAY_MILLIS = 3_000L
    const val NATIVE_REFRESH_MILLIS = 15_000L
    const val NATIVE_FIRST_RENDER_TIMEOUT_MILLIS = 8_000L
    const val SPLASH_DEADLINE_MILLIS = 12_000L
    const val STANDBY_MAX_AGE_MILLIS = 55L * 60L * 1_000L

    fun floorDelay(floor: AdFloor): Long = when (floor) {
        AdFloor.TWO_FLOOR -> TWO_FLOOR_DELAY_MILLIS
        AdFloor.MEDIUM_FLOOR -> MEDIUM_FLOOR_DELAY_MILLIS
        AdFloor.ALL_PRICES -> ALL_PRICES_DELAY_MILLIS
    }
}
