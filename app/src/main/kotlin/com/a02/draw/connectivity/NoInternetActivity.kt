package com.a02.draw.connectivity

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import com.a02.draw.core.ui.base.BaseActivity
import com.a02.draw.databinding.ActivityNoInternetBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class NoInternetActivity : BaseActivity<ActivityNoInternetBinding>(
    ActivityNoInternetBinding::inflate,
) {
    @Inject
    internal lateinit var internetAccessMonitor: InternetAccessMonitor

    override fun setupViews(savedInstanceState: Bundle?) {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = Unit
            },
        )
        binding.retryButton.setOnClickListener { internetAccessMonitor.retryNow() }
        binding.networkSettingsButton.setOnClickListener { openNetworkSettings() }
    }

    override fun observeData() {
        collectWhenStarted {
            internetAccessMonitor.state.collect { state ->
                if (state == InternetAccessState.ONLINE) {
                    finish()
                    return@collect
                }
                val isChecking = state == InternetAccessState.CHECKING
                binding.checkingProgress.isVisible = isChecking
                binding.checkingStatus.isVisible = isChecking
                binding.retryButton.isEnabled = !isChecking
            }
        }
    }

    private fun openNetworkSettings() {
        val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Settings.Panel.ACTION_INTERNET_CONNECTIVITY
        } else {
            Settings.ACTION_WIRELESS_SETTINGS
        }
        startActivity(Intent(action))
    }
}
