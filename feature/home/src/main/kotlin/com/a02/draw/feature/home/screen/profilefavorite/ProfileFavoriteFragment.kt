package com.a02.draw.feature.home.screen.profilefavorite

import android.os.Bundle
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.a02.draw.core.ui.base.BaseFragment
import com.a02.draw.core.ui.extensions.applyStatusBarPadding
import com.a02.draw.core.ui.extensions.setDebouncedClickListener
import com.a02.draw.core.ui.extensions.setAdaptiveGridLayoutManager
import com.a02.draw.feature.home.R
import com.a02.draw.feature.home.common.component.ArtworkAdapter
import com.a02.draw.feature.home.common.component.ArtworkRow
import com.a02.draw.feature.home.common.image.HomeImageLoader
import com.a02.draw.feature.home.common.model.BottomDestination
import com.a02.draw.feature.home.common.navigation.bindBottomNavigation
import com.a02.draw.feature.home.common.navigation.navigateBottom
import com.a02.draw.feature.home.databinding.ScreenProfileBinding
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import java.text.NumberFormat
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ProfileFavoriteFragment : BaseFragment<ScreenProfileBinding>(ScreenProfileBinding::inflate) {
    private val viewModel: ProfileFavoriteViewModel by viewModels()
    private val imageLoader = HomeImageLoader()
    private val adapter by lazy {
        ArtworkAdapter(
            imageLoader,
            { viewModel.onAction(ProfileFavoriteAction.OpenArtwork(it)) },
            { viewModel.onAction(ProfileFavoriteAction.ToggleFavorite(it)) },
        )
    }

    override fun setupViews(savedInstanceState: Bundle?) {
        binding.header.applyStatusBarPadding(lightStatusBarIcons = false)
        binding.contentList.setAdaptiveGridLayoutManager(
            minimumItemWidth = resources.getDimensionPixelSize(R.dimen.artwork_min_cell_width),
            minimumSpanCount = 2,
        )
        binding.contentList.adapter = adapter
        binding.favoriteTab.isSelected = true
        binding.favoriteTab.setOnClickListener { }
        binding.albumTab.setDebouncedClickListener { viewModel.onAction(ProfileFavoriteAction.OpenAlbum) }
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
                    adapter.submitList(state.favorites.map { ArtworkRow(it, true, true) })
                    binding.emptyImage.isVisible = state.favorites.isEmpty()
                    binding.emptyImage.setImageResource(R.drawable.figma_profile_empty_favorite)
                    binding.contentList.isVisible = state.favorites.isNotEmpty()
                }
            }
            launch {
                viewModel.effects.collect { effect ->
                    when (effect) {
                        ProfileFavoriteEffect.NavigateTutorial -> findNavController().navigate(R.id.tutorialCameraFragment)
                        ProfileFavoriteEffect.NavigateAlbum -> findNavController().navigate(R.id.profileAlbumFragment)
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

    override fun onDestroyView() {
        binding.contentList.adapter = null
        imageLoader.close()
        super.onDestroyView()
    }
}
