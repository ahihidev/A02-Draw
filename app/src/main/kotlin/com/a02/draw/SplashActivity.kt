package com.a02.draw

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import com.a02.draw.core.ui.base.BaseActivity
import com.a02.draw.databinding.ActivitySplashBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
@SuppressLint("CustomSplashScreen")
class SplashActivity : BaseActivity<ActivitySplashBinding>(ActivitySplashBinding::inflate) {
    private val viewModel: StartupViewModel by viewModels()
    private var hasOpenedDestination = false
    private var minimumDurationElapsed = false
    private var destination: StartupDestination? = null

    private val finishMinimumDuration = Runnable {
        minimumDurationElapsed = true
        openDestinationIfReady()
    }

    override fun setupViews(savedInstanceState: Bundle?) {
        binding.root.postDelayed(finishMinimumDuration, SPLASH_DURATION_MS)
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
        binding.root.removeCallbacks(finishMinimumDuration)
        super.onDestroy()
    }

    private fun openDestinationIfReady() {
        val target = destination ?: return
        if (!minimumDurationElapsed || hasOpenedDestination || isFinishing || isDestroyed) return

        hasOpenedDestination = true
        val destinationClass = when (target) {
            StartupDestination.ONBOARDING -> OnboardingActivity::class.java
            StartupDestination.MAIN -> MainActivity::class.java
        }
        startActivity(Intent(this, destinationClass))
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private companion object {
        const val SPLASH_DURATION_MS = 900L
    }
}
