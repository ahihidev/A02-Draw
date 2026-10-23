package com.a02.draw.tracking

import android.os.Bundle
import com.kiro.sdk.KiroSdk
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Delegating application analytics and event tracker that routes all analytics
 * events and screen views directly through [KiroSdk.tracker].
 */
@Singleton
class AppTracker @Inject constructor() {
    fun logEvent(name: String, params: Bundle? = null) {
        KiroSdk.tracker.logEvent(name, params)
    }

    fun logScreenView(screenName: String, screenClass: String? = null) {
        KiroSdk.tracker.logScreenView(screenName, screenClass)
    }

    fun setUserProperty(name: String, value: String) {
        KiroSdk.tracker.setUserProperty(name, value)
    }

    fun setUserId(userId: String) {
        KiroSdk.tracker.setUserId(userId)
    }
}
