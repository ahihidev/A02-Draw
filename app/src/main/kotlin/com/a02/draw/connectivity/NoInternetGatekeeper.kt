package com.a02.draw.connectivity

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.a02.draw.core.ui.base.BaseActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class NoInternetGatekeeper @Inject constructor(
    @ApplicationContext context: Context,
    private val internetAccessMonitor: InternetAccessMonitor,
) : Application.ActivityLifecycleCallbacks {
    private val application = context.applicationContext as Application
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val isRegistered = AtomicBoolean(false)
    private var resumedAppActivity = WeakReference<Activity>(null)
    private var gateActivity = WeakReference<NoInternetActivity>(null)
    private var startedAppActivityCount = 0
    private var isLaunchingGate = false

    fun ensureRegistered() {
        if (!isRegistered.compareAndSet(false, true)) return
        application.registerActivityLifecycleCallbacks(this)
        scope.launch {
            internetAccessMonitor.state.collect { state ->
                when (state) {
                    InternetAccessState.OFFLINE -> showGateIfPossible()
                    InternetAccessState.ONLINE -> dismissGate()
                    InternetAccessState.CHECKING -> Unit
                }
            }
        }
    }

    override fun onActivityStarted(activity: Activity) {
        if (activity !is BaseActivity<*>) return
        startedAppActivityCount += 1
        internetAccessMonitor.setAppForeground(true)
    }

    override fun onActivityResumed(activity: Activity) {
        if (activity !is BaseActivity<*>) return
        if (activity is NoInternetActivity) {
            gateActivity = WeakReference(activity)
            isLaunchingGate = false
        } else {
            resumedAppActivity = WeakReference(activity)
        }
        if (internetAccessMonitor.state.value == InternetAccessState.OFFLINE) {
            showGateIfPossible()
        }
    }

    override fun onActivityStopped(activity: Activity) {
        if (activity !is BaseActivity<*>) return
        startedAppActivityCount = (startedAppActivityCount - 1).coerceAtLeast(0)
        internetAccessMonitor.setAppForeground(startedAppActivityCount > 0)
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (gateActivity.get() === activity) {
            gateActivity.clear()
            isLaunchingGate = false
        }
        if (resumedAppActivity.get() === activity) resumedAppActivity.clear()
    }

    private fun showGateIfPossible() {
        if (gateActivity.get()?.isFinishing == false || isLaunchingGate) return
        val activity = resumedAppActivity.get()?.takeUnless {
            it.isFinishing || it.isDestroyed
        } ?: return
        isLaunchingGate = true
        runCatching {
            activity.startActivity(
                Intent(activity, NoInternetActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        }.onFailure { error ->
            isLaunchingGate = false
            Log.w(TAG, "Could not open the no-internet gate.", error)
        }
    }

    private fun dismissGate() {
        isLaunchingGate = false
        gateActivity.get()?.takeUnless(Activity::isFinishing)?.finish()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    private companion object {
        const val TAG = "NoInternetGate"
    }
}
