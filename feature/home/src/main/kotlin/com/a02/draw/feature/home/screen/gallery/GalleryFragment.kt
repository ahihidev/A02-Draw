package com.a02.draw.feature.home.screen.gallery

import android.os.Bundle
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.a02.draw.core.ui.ads.AppAdPlacement
import com.a02.draw.core.ui.ads.AppAdsController
import com.a02.draw.core.ui.ads.AppNativeAdFormat
import com.a02.draw.core.ui.ads.RewardContentKey
import com.a02.draw.core.ui.base.BaseFragment
import com.a02.draw.core.ui.extensions.applyStatusBarPadding
import com.a02.draw.core.ui.extensions.setAdaptiveGridLayoutManager
import com.a02.draw.core.ui.extensions.setDebouncedClickListener
import com.a02.draw.feature.home.R
import com.a02.draw.feature.home.common.ads.requestVipUnlock
import com.a02.draw.feature.home.common.ads.runAdNavigation
import com.a02.draw.feature.home.common.component.ArtworkAdapter
import com.a02.draw.feature.home.common.component.ArtworkRow
import com.a02.draw.feature.home.common.image.HomeImageLoader
import com.a02.draw.feature.home.common.motion.animateFirstVisibleItems
import com.a02.draw.feature.home.common.motion.crossfadeVisible
import com.a02.draw.feature.home.databinding.ScreenGalleryBinding
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class GalleryFragment : BaseFragment<ScreenGalleryBinding>(ScreenGalleryBinding::inflate) {
    @Inject
    lateinit var appAdsController: AppAdsController
    private val viewModel: GalleryViewModel by viewModels()
    private val imageLoader = HomeImageLoader()
    private val artworkAdapter by lazy {
        ArtworkAdapter(
            imageLoader,
            ::openArtwork,
            { viewModel.onAction(GalleryAction.ToggleFavorite(it)) },
        )
    }

    private fun openArtwork(id: String) {
        val artwork = viewModel.state.value.artworks.firstOrNull { it.id == id } ?: return
        requestVipUnlock(
            appAdsController,
            RewardContentKey.Artwork(id),
            artwork.title,
        ) { viewModel.onAction(GalleryAction.OpenArtwork(id)) }
    }
    private val quickFilterAdapter = QuickFilterAdapter {
        viewModel.onAction(GalleryAction.SelectQuickFilter(it))
    }
    private var hasAnimatedInitialItems = false

    override fun setupViews(savedInstanceState: Bundle?) {
        val hasTopic = arguments?.getString("topicId") != null
        appAdsController.attachNative(
            binding.nativeAdContainer,
            if (hasTopic) AppAdPlacement.TOPIC_DETAIL else AppAdPlacement.APP_GENERIC,
            if (hasTopic) AppNativeAdFormat.LARGE else AppNativeAdFormat.MEDIUM,
            viewLifecycleOwner,
        )
        binding.toolbar.applyStatusBarPadding()
        binding.artworkList.setAdaptiveGridLayoutManager(
            minimumItemWidth = resources.getDimensionPixelSize(R.dimen.artwork_min_cell_width),
            minimumSpanCount = 2,
        )
        binding.artworkList.adapter = artworkAdapter
        binding.quickFilterList.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.quickFilterList.adapter = quickFilterAdapter
        binding.filterOverlay.isVisible = false
        binding.backButton.setDebouncedClickListener { viewModel.onAction(GalleryAction.Back) }
        binding.filterButton.setDebouncedClickListener { viewModel.onAction(GalleryAction.OpenFilter) }
    }

    override fun observeData() {
        collectWhenStarted {
            launch {
                viewModel.state.collect { state ->
                    val showSkeleton = state.isLoading && state.artworks.isEmpty()
                    binding.title.text = state.title.ifBlank { getString(R.string.gallery) }
                    artworkAdapter.submitList(state.artworks.map {
                        ArtworkRow(
                            it,
                            it.id in state.favoriteIds,
                            showFavorite = true,
                            showTitle = false,
                            isLocked = !appAdsController.isUnlocked(
                                RewardContentKey.Artwork(it.id),
                            ),
                        )
                    })
                    quickFilterAdapter.submitList(state.quickFilters.map {
                        QuickFilterItem(it, it == state.selectedQuickFilter)
                    })
                    val showEmpty = state.artworks.isEmpty() && !state.isLoading
                    binding.loadingSkeleton.crossfadeVisible(showSkeleton)
                    binding.quickFilterList.crossfadeVisible(!showSkeleton)
                    binding.artworkList.crossfadeVisible(!showSkeleton && !showEmpty)
                    binding.emptyMessage.crossfadeVisible(showEmpty)
                    if (!showSkeleton && state.artworks.isNotEmpty() && !hasAnimatedInitialItems) {
                        hasAnimatedInitialItems = true
                        binding.artworkList.animateFirstVisibleItems()
                    }
                }
            }
            launch {
                merge(
                    appAdsController.rewardAccessState.map { Unit },
                    appAdsController.isPremium.map { Unit },
                ).collect {
                    val state = viewModel.state.value
                    artworkAdapter.submitList(state.artworks.map { artwork ->
                        ArtworkRow(
                            artwork,
                            artwork.id in state.favoriteIds,
                            showFavorite = true,
                            showTitle = false,
                            isLocked = !appAdsController.isUnlocked(
                                RewardContentKey.Artwork(artwork.id),
                            ),
                        )
                    })
                }
            }
            launch {
                viewModel.effects.collect { effect ->
                    when (effect) {
                        GalleryEffect.NavigateBack -> runAdNavigation(appAdsController) {
                            findNavController().navigateUp()
                        }

                        GalleryEffect.NavigateFilter -> runAdNavigation(appAdsController) {
                            findNavController().navigate(R.id.filterFragment)
                        }
                        GalleryEffect.NavigateTutorial -> findNavController().navigate(R.id.tutorialCameraFragment)
                        GalleryEffect.ShowError -> Snackbar.make(
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
        binding.artworkList.adapter = null
        binding.quickFilterList.adapter = null
        imageLoader.close()
        super.onDestroyView()
    }
}
