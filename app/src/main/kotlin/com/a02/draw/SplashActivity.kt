package com.a02.draw

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import com.a02.draw.ads.startup.StartupAdsCoordinator
import com.a02.draw.core.ui.base.BaseActivity
import com.a02.draw.databinding.ActivitySplashBinding
import com.kiro.sdk.KiroSdk
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
@SuppressLint("CustomSplashScreen")
class SplashActivity : BaseActivity<ActivitySplashBinding>(ActivitySplashBinding::inflate) {
    @Inject
    lateinit var startupAdsCoordinator: StartupAdsCoordinator

    private val viewModel: StartupViewModel by viewModels()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var hasOpenedDestination = false
    private var minimumDurationElapsed = false
    private var hasStartedSplashInterstitial = false
    private var hasCompletedSplashInterstitial = false
    private var destination: StartupDestination? = null

    private val finishConsentWait = Runnable {
        if (!hasStartedSplashInterstitial && !hasCompletedSplashInterstitial) {
            Log.w(TAG, "Consent callback timed out; continuing without blocking Splash.")
            startSplashInterstitial()
        }
    }

    private val finishSplashFlow = Runnable {
        if (destination == null) {
            Log.w(TAG, "Startup entitlement resolution timed out; using non-premium flow.")
            destination = StartupDestination.LANGUAGE
        }
        if (!hasCompletedSplashInterstitial) {
            Log.w(TAG, "Splash ads flow timed out; continuing without an interstitial.")
            completeSplashInterstitial()
        }
        openDestinationIfReady()
    }

    private val finishMinimumDuration = Runnable {
        minimumDurationElapsed = true
        openDestinationIfReady()
    }

    private val checkDestinationAfterResume = Runnable(::openDestinationIfReady)

    override fun setupViews(savedInstanceState: Bundle?) {
        mainHandler.postDelayed(finishMinimumDuration, SPLASH_DURATION_MS)
        mainHandler.postDelayed(finishConsentWait, CONSENT_TIMEOUT_MS)
        mainHandler.postDelayed(finishSplashFlow, SPLASH_FLOW_TIMEOUT_MS)
        gatherConsent()
    }

    override fun observeData() {
        collectWhenStarted {
            launch {
                viewModel.destination.collect { resolvedDestination ->
                    destination = resolvedDestination
                    openDestinationIfReady()
                }
            }
        }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(finishMinimumDuration)
        mainHandler.removeCallbacks(finishConsentWait)
        mainHandler.removeCallbacks(finishSplashFlow)
        mainHandler.removeCallbacks(checkDestinationAfterResume)
        super.onDestroy()
    }

    override fun onPostResume() {
        super.onPostResume()
        // Lifecycle reaches RESUMED after the framework finishes dispatching resume callbacks.
        // Posting avoids losing navigation when a consent/ad/No Internet callback completed
        // while this Activity was covered by another full-screen Activity.
        mainHandler.post(checkDestinationAfterResume)
    }

    private fun openDestinationIfReady() {
        val target = destination ?: return
        if (
            !minimumDurationElapsed ||
            !hasCompletedSplashInterstitial ||
            hasOpenedDestination ||
            !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ||
            isFinishing ||
            isDestroyed
        ) {
            return
        }

        hasOpenedDestination = true
        val destinationClass = when (target) {
            StartupDestination.LANGUAGE -> LanguageActivity::class.java
            StartupDestination.ONBOARDING -> OnboardingActivity::class.java
            StartupDestination.MAIN -> MainActivity::class.java
        }
        startActivity(Intent(this, destinationClass))
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private fun gatherConsent() {
        KiroSdk.consent.gatherConsent(activity = this) { error ->
            mainHandler.removeCallbacks(finishConsentWait)
            if (error != null) {
                Log.w(TAG, "Consent gathering failed.", error)
            }
            startSplashInterstitial()
        }
    }

    private fun startSplashInterstitial() {
        if (hasStartedSplashInterstitial || hasCompletedSplashInterstitial) return
        hasStartedSplashInterstitial = true
        startupAdsCoordinator.showSplashInterstitial(this, ::completeSplashInterstitial)
    }

    private fun completeSplashInterstitial() {
        if (hasCompletedSplashInterstitial) return
        hasCompletedSplashInterstitial = true
        openDestinationIfReady()
    }

    private companion object {
        const val TAG = "SplashActivity"
        const val SPLASH_DURATION_MS = 900L
        const val CONSENT_TIMEOUT_MS = 4_000L
        const val SPLASH_FLOW_TIMEOUT_MS = 15_000L
    }
}
