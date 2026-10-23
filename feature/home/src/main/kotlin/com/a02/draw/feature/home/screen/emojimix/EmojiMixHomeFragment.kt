package com.a02.draw.feature.home.screen.emojimix

import android.os.Bundle
import androidx.navigation.fragment.findNavController
import com.a02.draw.core.ui.ads.AppAdPlacement
import com.a02.draw.core.ui.ads.AppAdsController
import com.a02.draw.core.ui.ads.AppNativeAdFormat
import com.a02.draw.core.ui.base.BaseFragment
import com.a02.draw.core.ui.extensions.applyStatusBarPadding
import com.a02.draw.core.ui.extensions.setDebouncedClickListener
import com.a02.draw.feature.home.R
import com.a02.draw.feature.home.common.ads.runAdNavigation
import com.a02.draw.feature.home.databinding.ScreenEmojiMixHomeBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class EmojiMixHomeFragment :
    BaseFragment<ScreenEmojiMixHomeBinding>(ScreenEmojiMixHomeBinding::inflate) {
    @Inject
    lateinit var appAdsController: AppAdsController

    override fun setupViews(savedInstanceState: Bundle?) {
        appAdsController.attachNative(
            binding.nativeAdContainer,
            AppAdPlacement.APP_GENERIC,
            AppNativeAdFormat.MEDIUM,
            viewLifecycleOwner,
        )
        binding.root.applyStatusBarPadding()
        binding.backButton.setDebouncedClickListener {
            runAdNavigation(appAdsController) { findNavController().navigateUp() }
        }
        binding.mixTwoCard.setDebouncedClickListener { open(EmojiMixMode.MIX_2) }
        binding.mixThreeCard.setDebouncedClickListener { open(EmojiMixMode.MIX_3) }
    }

    private fun open(mode: EmojiMixMode) {
        runAdNavigation(appAdsController) {
            findNavController().navigate(
                R.id.emojiMixPickerFragment,
                Bundle().apply { putString(ARG_MODE, mode.name) },
            )
        }
    }

    companion object {
        const val ARG_MODE = "mode"
    }
}
