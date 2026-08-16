package com.a02.draw

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import com.a02.draw.ads.app.AppBackgroundTracker
import com.a02.draw.core.ui.ads.AppAdsController
import com.a02.draw.core.ui.base.BaseActivity
import com.a02.draw.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : BaseActivity<ActivityMainBinding>(ActivityMainBinding::inflate) {
    @Inject
    lateinit var appAdsController: AppAdsController
    @Inject
    lateinit var backgroundTracker: AppBackgroundTracker

    private var welcomeBackDialog: AlertDialog? = null

    override fun includeTopSystemBarPadding(): Boolean = false

    override fun setupViews(savedInstanceState: Bundle?) {
        appAdsController.preloadMainAds()
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val navController = mainNavController() ?: return
                    if (navController.currentDestination?.id == navController.graph.startDestinationId) {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                        return
                    }
                    val isEligible =
                        navController.currentDestination?.id !in excludedWelcomeBackDestinations
                    appAdsController.runNavigationInterstitial(
                        this@MainActivity,
                        isEligible,
                    ) {
                        navController.navigateUp()
                    }
                }
            },
        )
    }

    override fun observeData() {
        lifecycleScope.launch {
            appAdsController.isPremium.collect { premium ->
                if (premium) welcomeBackDialog?.dismiss()
            }
        }
    }

    override fun onPostResume() {
        super.onPostResume()
        binding.root.post(::showWelcomeBackIfNeeded)
    }

    override fun onDestroy() {
        welcomeBackDialog?.dismiss()
        welcomeBackDialog = null
        super.onDestroy()
    }

    private fun showWelcomeBackIfNeeded() {
        if (
            appAdsController.isPremium.value ||
            welcomeBackDialog?.isShowing == true ||
            !backgroundTracker.consumeReturnAfter(BACKGROUND_THRESHOLD_MILLIS) ||
            !isWelcomeBackDestinationEligible()
        ) {
            return
        }
        welcomeBackDialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.welcome_back_title)
            .setMessage(R.string.welcome_back_message)
            .setNegativeButton(R.string.welcome_back_close, null)
            .setPositiveButton(R.string.welcome_back_continue) { _, _ ->
                appAdsController.runBackgroundInterstitial(this) { Unit }
            }
            .setOnDismissListener { welcomeBackDialog = null }
            .show()
    }

    private fun isWelcomeBackDestinationEligible(): Boolean {
        val destinationId = mainNavController()?.currentDestination?.id ?: return false
        return destinationId !in excludedWelcomeBackDestinations
    }

    private fun mainNavController() =
        (supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment)
            ?.navController

    private companion object {
        const val BACKGROUND_THRESHOLD_MILLIS = 30_000L
        val excludedWelcomeBackDestinations = setOf(
            R.id.premiumFragment,
            R.id.settingsDetailFragment,
            R.id.webBrowserFragment,
            R.id.tutorialCameraFragment,
            R.id.tutorialScreenFragment,
            R.id.drawingCanvasFragment,
            R.id.drawingOpacityFragment,
            R.id.drawingCompleteFragment,
        )
    }
}
