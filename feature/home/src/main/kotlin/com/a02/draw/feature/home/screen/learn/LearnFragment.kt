package com.a02.draw.feature.home.screen.learn

import android.os.Bundle
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.a02.draw.core.ui.base.BaseFragment
import com.a02.draw.feature.home.R
import com.a02.draw.feature.home.common.component.CategoryAdapter
import com.a02.draw.feature.home.common.image.HomeImageLoader
import com.a02.draw.feature.home.common.model.BottomDestination
import com.a02.draw.feature.home.common.navigation.bindBottomNavigation
import com.a02.draw.feature.home.common.navigation.bindMainTabHeader
import com.a02.draw.feature.home.common.navigation.navigateBottom
import com.a02.draw.feature.home.databinding.ScreenLearnBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LearnFragment : BaseFragment<ScreenLearnBinding>(ScreenLearnBinding::inflate) {
    private val viewModel: LearnViewModel by viewModels()
    private val imageLoader = HomeImageLoader()
    private val adapter by lazy {
        CategoryAdapter(imageLoader) { viewModel.onAction(LearnAction.OpenCategory(it)) }
    }

    override fun setupViews(savedInstanceState: Bundle?) {
        binding.header.bindMainTabHeader { viewModel.onAction(LearnAction.OpenSearch) }
        binding.categoryList.layoutManager = LinearLayoutManager(requireContext())
        binding.categoryList.adapter = adapter
        binding.bottomNavigationInclude.bindBottomNavigation(BottomDestination.LEARN) {
            viewModel.onAction(LearnAction.OpenBottom(it))
        }
    }

    override fun observeData() {
        collectWhenStarted {
            launch {
                viewModel.state.collect { state ->
                    val showSkeleton = state.isLoading && state.categories.isEmpty()
                    adapter.submitList(state.categories)
                    binding.categoryLoadingSkeleton.isVisible = showSkeleton
                    binding.categoryList.isVisible = !showSkeleton
                }
            }
            launch {
                viewModel.effects.collect { effect ->
                    when (effect) {
                        LearnEffect.NavigateSearch -> findNavController().navigate(R.id.searchFragment)
                        is LearnEffect.NavigateCategory -> findNavController().navigate(
                            R.id.learnCategoryDetailFragment,
                            Bundle().apply { putString("categoryId", effect.categoryId) },
                        )

                        is LearnEffect.NavigateBottom -> findNavController().navigateBottom(effect.destination)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        binding.categoryList.adapter = null
        imageLoader.close()
        super.onDestroyView()
    }
}
