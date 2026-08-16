package com.a02.draw.onboarding.lightbox

import android.os.Bundle
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.a02.draw.R
import com.a02.draw.ads.startup.StartupAdPlacement
import com.a02.draw.ads.startup.StartupAdsCoordinator
import com.a02.draw.core.ui.base.BaseFragment
import com.a02.draw.core.ui.extensions.setDebouncedClickListener
import com.a02.draw.databinding.ScreenOnboardingLightboxBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class LightboxFragment : BaseFragment<ScreenOnboardingLightboxBinding>(
    ScreenOnboardingLightboxBinding::inflate,
) {
    @Inject
    lateinit var startupAdsCoordinator: StartupAdsCoordinator

    private val viewModel: LightboxViewModel by viewModels()

    override fun setupViews(savedInstanceState: Bundle?) {
        startupAdsCoordinator.attachNative(
            binding.onboardingAd,
            StartupAdPlacement.ONBOARDING_LIGHTBOX,
            viewLifecycleOwner,
        )
        binding.continueButton.setDebouncedClickListener {
            viewModel.onAction(LightboxAction.Continue)
        }
    }

    override fun observeData() {
        collectWhenStarted {
            viewModel.effects.collect {
                findNavController().navigate(R.id.onboardingNativeFullFragment)
            }
        }
    }
}
