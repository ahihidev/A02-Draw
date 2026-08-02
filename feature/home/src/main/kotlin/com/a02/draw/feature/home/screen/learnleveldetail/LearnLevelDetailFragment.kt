package com.a02.draw.feature.home.screen.learnleveldetail

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
class LearnLevelDetailFragment :
    BaseFragment<ScreenLearnDetailBinding>(ScreenLearnDetailBinding::inflate) {
    private val viewModel: LearnLevelDetailViewModel by viewModels()
    private val imageLoader = HomeImageLoader()
    private val adapter by lazy {
        LessonAdapter(imageLoader) {
            viewModel.onAction(
                LearnLevelDetailAction.OpenTutorial
            )
        }
    }

    override fun setupViews(savedInstanceState: Bundle?) {
        binding.toolbar.applyStatusBarPadding()
        binding.lessonList.layoutManager = LinearLayoutManager(requireContext())
        binding.lessonList.adapter = adapter
        binding.backButton.setDebouncedClickListener { viewModel.onAction(LearnLevelDetailAction.Back) }
    }

    override fun observeData() {
        collectWhenStarted {
            launch {
                viewModel.state.collect { state ->
                    val showSkeleton = state.isLoading && state.lesson == null
                    binding.title.text = state.lesson?.title ?: getString(R.string.lessons)
                    binding.description.isVisible = false
                    adapter.submitList(listOfNotNull(state.lesson?.let {
                        LessonRow(
                            it,
                            state.completedSteps
                        )
                    }))
                    binding.loadingSkeleton.isVisible = showSkeleton
                    binding.lessonList.isVisible = !showSkeleton
                }
            }
            launch {
                viewModel.effects.collect { effect ->
                    when (effect) {
                        LearnLevelDetailEffect.NavigateBack -> findNavController().navigateUp()
                        LearnLevelDetailEffect.NavigateTutorial -> findNavController()
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
