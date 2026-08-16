package com.a02.draw.ads.startup

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import com.a02.draw.connectivity.InternetAccessMonitor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class StartupAdsEnvironment @Inject constructor(
    @ApplicationContext context: Context,
    internetAccessMonitor: InternetAccessMonitor,
) : Application.ActivityLifecycleCallbacks {
    private val application = context.applicationContext as Application
    private val _isForeground = MutableStateFlow(false)
    private var resumedActivityCount = 0

    val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()
    val hasValidatedNetwork: StateFlow<Boolean> = internetAccessMonitor.hasInternetAccess

    init {
        application.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityResumed(activity: Activity) {
        resumedActivityCount += 1
        _isForeground.value = true
    }

    override fun onActivityPaused(activity: Activity) {
        resumedActivityCount = (resumedActivityCount - 1).coerceAtLeast(0)
        _isForeground.value = resumedActivityCount > 0
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
