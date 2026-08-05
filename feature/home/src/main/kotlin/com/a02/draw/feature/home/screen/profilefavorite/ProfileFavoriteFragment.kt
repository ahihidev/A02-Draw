package com.a02.draw.feature.home.screen.profilefavorite

import android.os.Bundle
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.a02.draw.core.ui.base.BaseFragment
import com.a02.draw.core.ui.extensions.setAdaptiveGridLayoutManager
import com.a02.draw.core.ui.extensions.setDebouncedClickListener
import com.a02.draw.feature.home.R
import com.a02.draw.feature.home.common.component.ArtworkAdapter
import com.a02.draw.feature.home.common.component.ArtworkRow
import com.a02.draw.feature.home.common.component.ProfileDrawingAdapter
import com.a02.draw.feature.home.common.image.HomeImageLoader
import com.a02.draw.feature.home.common.model.BottomDestination
import com.a02.draw.feature.home.common.navigation.bindBottomNavigation
import com.a02.draw.feature.home.common.navigation.bindMainTabHeader
import com.a02.draw.feature.home.common.navigation.navigateBottom
import com.a02.draw.feature.home.databinding.ScreenProfileBinding
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.NumberFormat

@AndroidEntryPoint
class ProfileFavoriteFragment : BaseFragment<ScreenProfileBinding>(
    ScreenProfileBinding::inflate,
    useScreenTransitions = false,
) {
    private val viewModel: ProfileFavoriteViewModel by viewModels()
    private val imageLoader = HomeImageLoader()
    private val favoriteAdapter by lazy {
        ArtworkAdapter(
            imageLoader,
            { viewModel.onAction(ProfileFavoriteAction.OpenArtwork(it)) },
            { viewModel.onAction(ProfileFavoriteAction.ToggleFavorite(it)) },
        )
    }
    private val albumAdapter by lazy {
        ProfileDrawingAdapter(imageLoader) {
            viewModel.onAction(ProfileFavoriteAction.OpenDrawing(it))
        }
    }

    override fun setupViews(savedInstanceState: Bundle?) {
        binding.header.bindMainTabHeader()
        binding.contentList.setAdaptiveGridLayoutManager(
            minimumItemWidth = resources.getDimensionPixelSize(R.dimen.artwork_min_cell_width),
            minimumSpanCount = 2,
        )
        binding.albumContentList.setAdaptiveGridLayoutManager(
            minimumItemWidth = resources.getDimensionPixelSize(R.dimen.artwork_min_cell_width),
            minimumSpanCount = 2,
        )
        binding.contentList.adapter = favoriteAdapter
        binding.albumContentList.adapter = albumAdapter
        binding.favoriteTab.setDebouncedClickListener {
            viewModel.onAction(ProfileFavoriteAction.SelectTab(ProfileTab.FAVORITES))
        }
        binding.albumTab.setDebouncedClickListener {
            viewModel.onAction(ProfileFavoriteAction.SelectTab(ProfileTab.ALBUM))
        }
        binding.bottomNavigationInclude.bindBottomNavigation(BottomDestination.PROFILE) {
            viewModel.onAction(ProfileFavoriteAction.OpenBottom(it))
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
                    favoriteAdapter.submitList(state.favorites.map { ArtworkRow(it, true, true) })
                    albumAdapter.submitList(state.albumDrawings)
                    renderTab(state)
                }
            }
            launch {
                viewModel.effects.collect { effect ->
                    when (effect) {
                        ProfileFavoriteEffect.NavigateTutorial -> findNavController().navigate(R.id.tutorialCameraFragment)
                        ProfileFavoriteEffect.NavigateComplete -> findNavController().navigate(R.id.drawingCompleteFragment)
                        is ProfileFavoriteEffect.NavigateBottom -> findNavController().navigateBottom(
                            effect.destination
                        )

                        ProfileFavoriteEffect.ShowError -> Snackbar.make(
                            binding.root,
                            R.string.generic_error,
                            Snackbar.LENGTH_SHORT,
                        ).show()
                    }
                }
            }
        }
    }

    private fun renderTab(state: ProfileFavoriteUiState) {
        val showingFavorites = state.selectedTab == ProfileTab.FAVORITES
        val isEmpty =
            if (showingFavorites) state.favorites.isEmpty() else state.albumDrawings.isEmpty()
        binding.favoriteTab.isSelected = showingFavorites
        binding.albumTab.isSelected = !showingFavorites
        binding.contentList.isVisible = showingFavorites && !isEmpty
        binding.albumContentList.isVisible = !showingFavorites && !isEmpty
        binding.emptyImage.isVisible = isEmpty
        binding.emptyImage.setImageResource(
            if (showingFavorites) {
                R.drawable.figma_profile_empty_favorite
            } else {
                R.drawable.figma_profile_empty_album
            },
        )
    }

    override fun onDestroyView() {
        binding.contentList.adapter = null
        binding.albumContentList.adapter = null
        imageLoader.close()
        super.onDestroyView()
    }
}
