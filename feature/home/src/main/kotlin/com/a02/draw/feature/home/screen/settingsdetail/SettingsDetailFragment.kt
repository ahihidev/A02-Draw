package com.a02.draw.feature.home.screen.settingsdetail

import android.os.Bundle
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.a02.draw.core.ui.ads.AppAdPlacement
import com.a02.draw.core.ui.ads.AppAdsController
import com.a02.draw.core.ui.ads.AppNativeAdFormat
import com.a02.draw.core.ui.base.BaseFragment
import com.a02.draw.core.ui.extensions.applyStatusBarPadding
import com.a02.draw.core.ui.extensions.setDebouncedClickListener
import com.a02.draw.feature.home.common.ads.runAdNavigation
import com.a02.draw.feature.home.databinding.ScreenSettingsDetailBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SettingsDetailFragment :
    BaseFragment<ScreenSettingsDetailBinding>(ScreenSettingsDetailBinding::inflate) {
    @Inject
    lateinit var appAdsController: AppAdsController
    private val viewModel: SettingsDetailViewModel by viewModels()
    private val fromPremium: Boolean get() = arguments?.getBoolean("fromPremium") == true

    override fun setupViews(savedInstanceState: Bundle?) {
        if (!fromPremium) {
            appAdsController.attachNative(
                binding.nativeAdContainer,
                AppAdPlacement.SETTING,
                AppNativeAdFormat.MEDIUM,
                viewLifecycleOwner,
            )
        }
        binding.toolbar.applyStatusBarPadding()
        binding.backButton.setDebouncedClickListener { viewModel.onAction(SettingsDetailAction.Back) }
        binding.backToSettings.setDebouncedClickListener { viewModel.onAction(SettingsDetailAction.Back) }
    }

    override fun observeData() {
        collectWhenStarted {
            launch {
                viewModel.state.collect { state ->
                    binding.title.setText(state.title)
                    binding.heading.setText(state.heading)
                    binding.body.text = buildString {
                        append(getString(state.body))
                        state.bodyAppendix?.let { appendix ->
                            append("\n\n")
                            append(getString(appendix))
                        }
                    }
                }
            }
            launch {
                viewModel.effects.collect {
                    if (fromPremium) {
                        findNavController().navigateUp()
                    } else {
                        runAdNavigation(appAdsController) { findNavController().navigateUp() }
                    }
                }
            }
        }
    }
}
