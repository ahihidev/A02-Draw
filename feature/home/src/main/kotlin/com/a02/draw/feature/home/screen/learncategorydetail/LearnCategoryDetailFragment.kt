package com.a02.draw.feature.home.screen.learncategorydetail

import android.os.Bundle
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.a02.draw.core.ui.base.BaseFragment
import com.a02.draw.core.ui.extensions.applyStatusBarPadding
import com.a02.draw.core.ui.extensions.setDebouncedClickListener
import com.a02.draw.feature.home.R
import com.a02.draw.feature.home.common.component.LessonAdapter
import com.a02.draw.feature.home.common.component.LessonRow
import com.a02.draw.feature.home.common.image.HomeImageLoader
import com.a02.draw.feature.home.databinding.ScreenLearnDetailBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LearnCategoryDetailFragment :
    BaseFragment<ScreenLearnDetailBinding>(ScreenLearnDetailBinding::inflate) {
    private val viewModel: LearnCategoryDetailViewModel by viewModels()
    private val imageLoader = HomeImageLoader()
    private val adapter by lazy {
        LessonAdapter(imageLoader) { viewModel.onAction(LearnCategoryDetailAction.OpenLesson(it)) }
    }

    override fun setupViews(savedInstanceState: Bundle?) {
        binding.toolbar.applyStatusBarPadding()
        binding.lessonList.layoutManager = LinearLayoutManager(requireContext())
        binding.lessonList.adapter = adapter
        binding.backButton.setDebouncedClickListener { viewModel.onAction(LearnCategoryDetailAction.Back) }
    }

    override fun observeData() {
        collectWhenStarted {
            launch {
                viewModel.state.collect { state ->
                    val showSkeleton = state.isLoading && state.lessons.isEmpty()
                    binding.title.text = state.title
                    binding.description.text = resources.getQuantityString(
                        R.plurals.guided_lessons_available,
                        state.lessons.size,
                        state.lessons.size,
                    )
                    adapter.submitList(state.lessons.map {
                        LessonRow(it, state.completedSteps[it.id] ?: 0)
                    })
                    binding.loadingSkeleton.isVisible = showSkeleton
                    binding.lessonList.isVisible = !showSkeleton
                    binding.description.isVisible = !showSkeleton
                }
            }
            launch {
                viewModel.effects.collect { effect ->
                    when (effect) {
                        LearnCategoryDetailEffect.NavigateBack -> findNavController().navigateUp()
                        LearnCategoryDetailEffect.NavigateTutorial -> findNavController()
                            .navigate(R.id.tutorialCameraFragment)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        binding.lessonList.adapter = null
        imageLoader.close()
        super.onDestroyView()
    }
}
