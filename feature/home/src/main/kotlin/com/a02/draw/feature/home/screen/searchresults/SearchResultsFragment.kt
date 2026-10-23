package com.a02.draw.feature.home.screen.searchresults

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.a02.draw.core.ui.ads.AppAdPlacement
import com.a02.draw.core.ui.ads.AppAdsController
import com.a02.draw.core.ui.ads.AppNativeAdFormat
import com.a02.draw.core.ui.ads.RewardContentKey
import com.a02.draw.core.ui.base.BaseFragment
import com.a02.draw.core.ui.extensions.applyStatusBarPadding
import com.a02.draw.core.ui.extensions.setAdaptiveGridLayoutManager
import com.a02.draw.core.ui.extensions.setDebouncedClickListener
import com.a02.draw.core.ui.widget.ApiSkeletonView
import com.a02.draw.feature.home.R
import com.a02.draw.feature.home.common.ads.requestVipUnlock
import com.a02.draw.feature.home.common.ads.runAdNavigation
import com.a02.draw.feature.home.common.component.ArtworkAdapter
import com.a02.draw.feature.home.common.component.ArtworkRow
import com.a02.draw.feature.home.common.image.HomeImageLoader
import com.a02.draw.feature.home.common.motion.animateFirstVisibleItems
import com.a02.draw.feature.home.common.motion.crossfadeVisible
import com.a02.draw.feature.home.databinding.ScreenSearchBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SearchResultsFragment : BaseFragment<ScreenSearchBinding>(ScreenSearchBinding::inflate) {
    @Inject
    lateinit var appAdsController: AppAdsController
    private val viewModel: SearchResultsViewModel by viewModels()
    private val imageLoader = HomeImageLoader()
    private var rendering = false
    private var hasAnimatedInitialItems = false
    private val adapter by lazy {
        ArtworkAdapter(imageLoader, ::openArtwork, {})
    }

    private fun openArtwork(id: String) {
        val artwork = viewModel.state.value.results.firstOrNull { it.id == id } ?: return
        requestVipUnlock(
            appAdsController,
            RewardContentKey.Artwork(id),
            artwork.title,
        ) { viewModel.onAction(SearchResultsAction.OpenArtwork(id)) }
    }

    override fun setupViews(savedInstanceState: Bundle?) {
        appAdsController.attachNative(
            binding.nativeAdContainer,
            AppAdPlacement.SEARCH,
            AppNativeAdFormat.LARGE,
            viewLifecycleOwner,
        )
        binding.toolbar.applyStatusBarPadding()
        binding.contentList.setAdaptiveGridLayoutManager(
            minimumItemWidth = resources.getDimensionPixelSize(R.dimen.artwork_min_cell_width),
            minimumSpanCount = 2,
        )
        binding.loadingSkeleton.skeletonMode = ApiSkeletonView.Mode.GRID
        binding.contentList.adapter = adapter
        binding.backButton.setDebouncedClickListener { viewModel.onAction(SearchResultsAction.Back) }
        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                value: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) = Unit

            override fun afterTextChanged(value: Editable?) = Unit
            override fun onTextChanged(value: CharSequence?, start: Int, before: Int, count: Int) {
                if (!rendering) {
                    viewModel.onAction(
                        SearchResultsAction.QueryChanged(
                            value?.toString().orEmpty()
                        )
                    )
                }
            }
        })
        binding.searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                viewModel.onAction(SearchResultsAction.Submit)
                true
            } else false
        }
    }

    override fun observeData() {
        collectWhenStarted {
            launch {
                viewModel.state.collect { state ->
                    val showSkeleton = state.isLoading && state.results.isEmpty()
                    if (binding.searchInput.text.toString() != state.query) {
                        rendering = true
                        binding.searchInput.setText(state.query)
                        binding.searchInput.setSelection(state.query.length)
                        rendering = false
                    }
                    binding.sectionTitle.setText(R.string.search_results)
                    adapter.submitList(state.results.map {
                        ArtworkRow(
                            it,
                            isFavorite = false,
                            showFavorite = false,
                            isLocked = !appAdsController.isUnlocked(
                                RewardContentKey.Artwork(it.id),
                            ),
                        )
                    })
                    val showEmpty = state.results.isEmpty() && !state.isLoading
                    binding.loadingSkeleton.crossfadeVisible(showSkeleton)
                    binding.contentList.crossfadeVisible(!showSkeleton && !showEmpty)
                    binding.emptyMessage.crossfadeVisible(showEmpty)
                    if (!showSkeleton && state.results.isNotEmpty() && !hasAnimatedInitialItems) {
                        hasAnimatedInitialItems = true
                        binding.contentList.animateFirstVisibleItems()
                    }
                }
            }
            launch {
                merge(
                    appAdsController.rewardAccessState.map { Unit },
                    appAdsController.isPremium.map { Unit },
                ).collect {
                    adapter.submitList(viewModel.state.value.results.map { artwork ->
                        ArtworkRow(
                            artwork,
                            isFavorite = false,
                            showFavorite = false,
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
                        is SearchResultsEffect.OpenTutorial -> findNavController()
                            .navigate(R.id.tutorialCameraFragment)

                        SearchResultsEffect.NavigateBack -> runAdNavigation(appAdsController) {
                            findNavController().navigateUp()
                        }
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
