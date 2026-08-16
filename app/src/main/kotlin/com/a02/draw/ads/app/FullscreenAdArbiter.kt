package com.a02.draw.ads.app

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FullscreenAdArbiter @Inject constructor() {
    private var isShowing = false
    private var eligibleActionCount = 0
    private var lastFullScreenAtMillis = Long.MIN_VALUE

    @Synchronized
    fun recordNavigation(isEligible: Boolean) {
        if (!isEligible) return
        eligibleActionCount += 1
    }

    @Synchronized
    fun tryAcquireNavigation(nowMillis: Long): Boolean {
        if (eligibleActionCount < REQUIRED_NAVIGATION_ACTIONS) return false
        return tryAcquire(nowMillis, FULL_SCREEN_COOLDOWN_MILLIS)
    }

    @Synchronized
    fun tryAcquire(nowMillis: Long, minimumIntervalMillis: Long): Boolean {
        if (isShowing) return false
        if (lastFullScreenAtMillis != Long.MIN_VALUE &&
            nowMillis - lastFullScreenAtMillis < minimumIntervalMillis
        ) {
            return false
        }
        isShowing = true
        lastFullScreenAtMillis = nowMillis
        return true
    }

    @Synchronized
    fun release(resetNavigationCount: Boolean = true) {
        isShowing = false
        if (resetNavigationCount) eligibleActionCount = 0
    }

    @Synchronized
    fun cancelAcquire() {
        isShowing = false
    }

    @Synchronized
    fun recordFullScreenShown(nowMillis: Long) {
        lastFullScreenAtMillis = nowMillis
        eligibleActionCount = 0
    }

    internal fun actionCount(): Int = eligibleActionCount

    companion object {
        const val REQUIRED_NAVIGATION_ACTIONS = 2
        const val FULL_SCREEN_COOLDOWN_MILLIS = 30_000L
    }
}
