package com.a02.draw

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import com.a02.draw.ads.app.AppBackgroundTracker
import com.a02.draw.core.ui.ads.AppAdsController
import com.a02.draw.core.ui.base.BaseActivity
import com.a02.draw.databinding.ActivityMainBinding
import com.a02.draw.play.PlayEngagementCoordinator
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : BaseActivity<ActivityMainBinding>(ActivityMainBinding::inflate) {
    @Inject
    lateinit var appAdsController: AppAdsController
    @Inject
    lateinit var backgroundTracker: AppBackgroundTracker

    @Inject
    lateinit var playEngagementCoordinator: PlayEngagementCoordinator

    private var welcomeBackDialog: AlertDialog? = null
    private var updateReadySnackbar: Snackbar? = null
    private val updateLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result -> playEngagementCoordinator.onUpdateFlowResult(result.resultCode) }
    private val showPlayPrompt = Runnable {
        if (!canShowPlayPrompt()) return@Runnable
        playEngagementCoordinator.tryShowAutomaticPrompt(
            activity = this,
            updateLauncher = updateLauncher,
            canLaunch = ::canShowPlayPrompt,
            beforeLaunch = appAdsController::suppressNextBackgroundInterstitial,
        )
    }

    override fun includeTopSystemBarPadding(): Boolean = false

    override fun setupViews(savedInstanceState: Bundle?) {
        appAdsController.preloadMainAds()
        playEngagementCoordinator.recordMainSession()
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
        lifecycleScope.launch {
            playEngagementCoordinator.isFlexibleUpdateDownloaded.collect(::renderUpdateReady)
        }
    }

    override fun onPostResume() {
        super.onPostResume()
        binding.root.post {
            binding.root.removeCallbacks(showPlayPrompt)
            if (!showWelcomeBackIfNeeded()) {
                binding.root.postDelayed(showPlayPrompt, PLAY_PROMPT_DELAY_MILLIS)
            }
        }
    }

    override fun onDestroy() {
        binding.root.removeCallbacks(showPlayPrompt)
        welcomeBackDialog?.dismiss()
        welcomeBackDialog = null
        updateReadySnackbar?.dismiss()
        updateReadySnackbar = null
        super.onDestroy()
    }

    private fun showWelcomeBackIfNeeded(): Boolean {
        if (
            appAdsController.isPremium.value ||
            welcomeBackDialog?.isShowing == true ||
            !backgroundTracker.consumeReturnAfter(BACKGROUND_THRESHOLD_MILLIS) ||
            !isWelcomeBackDestinationEligible()
        ) {
            return false
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
        return true
    }

    private fun renderUpdateReady(isReady: Boolean) {
        if (!isReady) {
            updateReadySnackbar?.dismiss()
            updateReadySnackbar = null
            return
        }
        if (updateReadySnackbar?.isShown == true || isFinishing || isDestroyed) return
        updateReadySnackbar = Snackbar.make(
            binding.root,
            R.string.update_ready_message,
            Snackbar.LENGTH_INDEFINITE,
        ).setAction(R.string.update_restart) {
            playEngagementCoordinator.completeFlexibleUpdate()
        }.also(Snackbar::show)
    }

    private fun canShowPlayPrompt(): Boolean {
        if (
            isFinishing ||
            isDestroyed ||
            !hasWindowFocus() ||
            !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ||
            welcomeBackDialog?.isShowing == true
        ) {
            return false
        }
        val navController = mainNavController() ?: return false
        return navController.currentDestination?.id == navController.graph.startDestinationId
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
        const val PLAY_PROMPT_DELAY_MILLIS = 1_500L
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
