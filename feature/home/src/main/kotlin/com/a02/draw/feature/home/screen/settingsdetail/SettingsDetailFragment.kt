package com.a02.draw.feature.home.screen.settingsdetail

import android.os.Bundle
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.a02.draw.core.ui.base.BaseFragment
import com.a02.draw.core.ui.extensions.applyStatusBarPadding
import com.a02.draw.core.ui.extensions.setDebouncedClickListener
import com.a02.draw.feature.home.databinding.ScreenSettingsDetailBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingsDetailFragment :
    BaseFragment<ScreenSettingsDetailBinding>(ScreenSettingsDetailBinding::inflate) {
    private val viewModel: SettingsDetailViewModel by viewModels()

    override fun setupViews(savedInstanceState: Bundle?) {
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
                    binding.body.setText(state.body)
                }
            }
            launch { viewModel.effects.collect { findNavController().navigateUp() } }
        }
    }
}
