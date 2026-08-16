package com.a02.draw.onboarding.ads

import android.os.Bundle
import androidx.navigation.fragment.findNavController
import com.a02.draw.R
import com.a02.draw.ads.startup.StartupAdPlacement
import com.a02.draw.ads.startup.StartupAdsCoordinator
import com.a02.draw.core.ui.base.BaseFragment
import com.a02.draw.databinding.ScreenOnboardingNativeFullBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class OnboardingNativeFullFragment : BaseFragment<ScreenOnboardingNativeFullBinding>(
    ScreenOnboardingNativeFullBinding::inflate,
) {
    @Inject
    lateinit var startupAdsCoordinator: StartupAdsCoordinator

    private var hasContinued = false

    override fun setupViews(savedInstanceState: Bundle?) {
        binding.onboardingFullAd.onFullScreenExit = { continueOnce() }
        startupAdsCoordinator.attachNative(
            host = binding.onboardingFullAd,
            placement = StartupAdPlacement.ONBOARDING_FULL,
            lifecycleOwner = viewLifecycleOwner,
            onUnavailable = ::continueOnce,
        )
    }

    override fun observeData() = Unit

    private fun continueOnce() {
        if (hasContinued || !isAdded) return
        hasContinued = true
        val navController = findNavController()
        if (navController.currentDestination?.id == R.id.onboardingNativeFullFragment) {
            navController.navigate(R.id.lessonsFragment)
        }
    }
}
