package com.a02.draw.feature.home.screen.filter

import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.a02.draw.core.ui.base.BaseFragment
import com.a02.draw.core.ui.extensions.applyStatusBarPadding
import com.a02.draw.domain.model.ArtworkStyle
import com.a02.draw.feature.home.R
import com.a02.draw.feature.home.databinding.ScreenGalleryBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FilterFragment : BaseFragment<ScreenGalleryBinding>(ScreenGalleryBinding::inflate) {
    private val viewModel: FilterViewModel by viewModels()

    override fun setupViews(savedInstanceState: Bundle?) {
        binding.toolbar.applyStatusBarPadding()
        binding.filterOverlay.isVisible = true
        binding.filterOverlay.setOnClickListener { viewModel.onAction(FilterAction.Back) }
        binding.filterSheet.setOnClickListener { }
        binding.easyFilter.setOnClickListener { viewModel.onAction(FilterAction.ToggleDifficulty("Easy")) }
        binding.mediumFilter.setOnClickListener { viewModel.onAction(FilterAction.ToggleDifficulty("Medium")) }
        binding.hardFilter.setOnClickListener { viewModel.onAction(FilterAction.ToggleDifficulty("Hard")) }
        binding.lineStyleFilter.setOnClickListener {
            viewModel.onAction(FilterAction.ToggleStyle(ArtworkStyle.LINE_SKETCH))
        }
        binding.colorStyleFilter.setOnClickListener {
            viewModel.onAction(FilterAction.ToggleStyle(ArtworkStyle.COLOR))
        }
        binding.resetFilter.setOnClickListener { viewModel.onAction(FilterAction.Reset) }
        binding.applyFilter.setOnClickListener { viewModel.onAction(FilterAction.Apply) }
        binding.backButton.setOnClickListener { viewModel.onAction(FilterAction.Back) }
    }

    override fun observeData() {
        collectWhenStarted {
            launch {
                viewModel.state.collect { state ->
                    binding.easyFilter.renderSelected(state.difficulty.equals("Easy", true))
                    binding.mediumFilter.renderSelected(state.difficulty.equals("Medium", true))
                    binding.hardFilter.renderSelected(state.difficulty.equals("Hard", true))
                    binding.lineStyleFilter.renderSelected(state.style == ArtworkStyle.LINE_SKETCH)
                    binding.colorStyleFilter.renderSelected(state.style == ArtworkStyle.COLOR)
                }
            }
            launch { viewModel.effects.collect { findNavController().navigateUp() } }
        }
    }

    private fun android.widget.TextView.renderSelected(selected: Boolean) {
        isSelected = selected
        setTextColor(
            ContextCompat.getColor(
                context,
                if (selected) R.color.home_purple else R.color.home_navy
            ),
        )
    }
}
