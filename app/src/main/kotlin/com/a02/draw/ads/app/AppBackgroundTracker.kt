package com.a02.draw.ads.app

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppBackgroundTracker @Inject constructor(
    @ApplicationContext context: Context,
) : Application.ActivityLifecycleCallbacks {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val resumedActivities = mutableSetOf<Activity>()
    private var backgroundStartedAt: Long? = null
    private var pendingBackgroundDuration = 0L
    private var hasConsumedCurrentReturn = true
    private var suppressDepartureUntilMillis = Long.MIN_VALUE
    private var currentBackgroundSuppressed = false
    private var lastPausedAt = 0L
    private val confirmBackground = Runnable {
        synchronized(this) {
            if (resumedActivities.isEmpty() && backgroundStartedAt == null) {
                backgroundStartedAt = lastPausedAt
                currentBackgroundSuppressed =
                    lastPausedAt <= suppressDepartureUntilMillis
                suppressDepartureUntilMillis = Long.MIN_VALUE
            }
        }
    }

    init {
        (context.applicationContext as Application).registerActivityLifecycleCallbacks(this)
    }

    fun ensureRegistered() = Unit

    @Synchronized
    fun consumeReturnAfter(minimumBackgroundMillis: Long): Boolean {
        if (hasConsumedCurrentReturn || pendingBackgroundDuration < minimumBackgroundMillis) {
            return false
        }
        hasConsumedCurrentReturn = true
        return true
    }

    @Synchronized
    fun suppressNextReturn() {
        suppressDepartureUntilMillis =
            SystemClock.elapsedRealtime() + EXTERNAL_FLOW_LAUNCH_WINDOW_MILLIS
        hasConsumedCurrentReturn = true
    }

    @Synchronized
    override fun onActivityResumed(activity: Activity) {
        mainHandler.removeCallbacks(confirmBackground)
        resumedActivities += activity
        if (resumedActivities.size == 1) {
            backgroundStartedAt?.let { startedAt ->
                pendingBackgroundDuration = SystemClock.elapsedRealtime() - startedAt
                hasConsumedCurrentReturn = currentBackgroundSuppressed
                currentBackgroundSuppressed = false
            }
            backgroundStartedAt = null
        }
    }

    @Synchronized
    override fun onActivityPaused(activity: Activity) {
        resumedActivities -= activity
        lastPausedAt = SystemClock.elapsedRealtime()
        mainHandler.removeCallbacks(confirmBackground)
        mainHandler.postDelayed(confirmBackground, BACKGROUND_CONFIRM_DELAY_MILLIS)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit

    private companion object {
        const val EXTERNAL_FLOW_LAUNCH_WINDOW_MILLIS = 5_000L
        const val BACKGROUND_CONFIRM_DELAY_MILLIS = 750L
    }
}
