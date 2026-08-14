package com.a02.draw.feature.home.screen.profilealbum

import android.os.Bundle
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.a02.draw.core.ui.base.BaseFragment
import com.a02.draw.core.ui.extensions.setAdaptiveGridLayoutManager
import com.a02.draw.core.ui.extensions.setDebouncedClickListener
import com.a02.draw.feature.home.R
import com.a02.draw.feature.home.common.component.ProfileDrawingAdapter
import com.a02.draw.feature.home.common.image.HomeImageLoader
import com.a02.draw.feature.home.common.model.BottomDestination
import com.a02.draw.feature.home.common.navigation.bindBottomNavigation
import com.a02.draw.feature.home.common.navigation.bindMainTabHeader
import com.a02.draw.feature.home.common.navigation.navigateBottom
import com.a02.draw.feature.home.databinding.ScreenProfileBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.NumberFormat

@AndroidEntryPoint
class ProfileAlbumFragment : BaseFragment<ScreenProfileBinding>(ScreenProfileBinding::inflate) {
    private val viewModel: ProfileAlbumViewModel by viewModels()
    private val imageLoader = HomeImageLoader()
    private val adapter by lazy {
        ProfileDrawingAdapter(imageLoader) { viewModel.onAction(ProfileAlbumAction.OpenDrawing(it)) }
    }

    override fun setupViews(savedInstanceState: Bundle?) {
        binding.header.bindMainTabHeader()
        binding.contentList.setAdaptiveGridLayoutManager(
            minimumItemWidth = resources.getDimensionPixelSize(R.dimen.artwork_min_cell_width),
            minimumSpanCount = 2,
        )
        binding.contentList.adapter = adapter
        binding.albumTab.isSelected = true
        binding.favoriteTab.setDebouncedClickListener { viewModel.onAction(ProfileAlbumAction.OpenFavorites) }
        binding.albumTab.setOnClickListener { }
        binding.bottomNavigationInclude.bindBottomNavigation(BottomDestination.PROFILE) {
            viewModel.onAction(ProfileAlbumAction.OpenBottom(it))
        }
    }

    override fun observeData() {
        collectWhenStarted {
            launch {
                viewModel.state.collect { state ->
                    val numberFormat = NumberFormat.getIntegerInstance()
                    binding.sketchStat.text = numberFormat.format(state.sketchCount)
                    binding.lessonStat.text = numberFormat.format(state.lessonCount)
                    binding.timeStat.text = numberFormat.format(state.lessonMinutes)
                    adapter.submitList(state.drawings)
                    binding.emptyImage.isVisible = state.drawings.isEmpty()
                    binding.emptyImage.setImageResource(R.drawable.figma_profile_empty_album)
                    binding.contentList.isVisible = state.drawings.isNotEmpty()
                }
            }
            launch {
                viewModel.effects.collect { effect ->
                    when (effect) {
                        ProfileAlbumEffect.NavigateComplete -> findNavController().navigate(R.id.drawingCompleteFragment)
                        ProfileAlbumEffect.NavigateFavorites -> findNavController().navigate(R.id.profileFavoriteFragment)
                        is ProfileAlbumEffect.NavigateBottom -> findNavController().navigateBottom(
                            effect.destination
                        )
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        binding.contentList.adapter = null
        imageLoader.close()
        super.onDestroyView()
    }
}
