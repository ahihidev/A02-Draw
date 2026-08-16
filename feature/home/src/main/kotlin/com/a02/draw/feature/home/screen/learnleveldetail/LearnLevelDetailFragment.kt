package com.a02.draw.feature.home.screen.learnleveldetail

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
import com.a02.draw.core.ui.extensions.setDebouncedClickListener
import com.a02.draw.feature.home.R
import com.a02.draw.feature.home.common.ads.requestVipUnlock
import com.a02.draw.feature.home.common.ads.runAdNavigation
import com.a02.draw.feature.home.common.component.LessonAdapter
import com.a02.draw.feature.home.common.component.LessonRow
import com.a02.draw.feature.home.common.image.HomeImageLoader
import com.a02.draw.feature.home.databinding.ScreenLearnDetailBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LearnLevelDetailFragment :
    BaseFragment<ScreenLearnDetailBinding>(ScreenLearnDetailBinding::inflate) {
    @Inject
    lateinit var appAdsController: AppAdsController
    private val viewModel: LearnLevelDetailViewModel by viewModels()
    private val imageLoader = HomeImageLoader()
    private val adapter by lazy {
        LessonAdapter(imageLoader) { openLesson() }
    }

    private fun openLesson() {
        val lesson = viewModel.state.value.lesson ?: return
        requestVipUnlock(
            appAdsController,
            RewardContentKey.Lesson(lesson.id, lesson.categoryId),
            lesson.title,
        ) { viewModel.onAction(LearnLevelDetailAction.OpenTutorial) }
    }

    override fun setupViews(savedInstanceState: Bundle?) {
        appAdsController.attachNative(
            binding.nativeAdContainer,
            AppAdPlacement.LEARN_DETAIL,
            AppNativeAdFormat.MEDIUM,
            viewLifecycleOwner,
        )
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
                            state.completedSteps,
                            isLocked = !appAdsController.isUnlocked(
                                RewardContentKey.Lesson(it.id, it.categoryId),
                            ),
                        )
                    }))
                    binding.loadingSkeleton.isVisible = showSkeleton
                    binding.lessonList.isVisible = !showSkeleton
                }
            }
            launch {
                merge(
                    appAdsController.rewardAccessState.map { Unit },
                    appAdsController.isPremium.map { Unit },
                ).collect {
                    val state = viewModel.state.value
                    adapter.submitList(listOfNotNull(state.lesson?.let { lesson ->
                        LessonRow(
                            lesson,
                            state.completedSteps,
                            isLocked = !appAdsController.isUnlocked(
                                RewardContentKey.Lesson(lesson.id, lesson.categoryId),
                            ),
                        )
                    }))
                }
            }
            launch {
                viewModel.effects.collect { effect ->
                    when (effect) {
                        LearnLevelDetailEffect.NavigateBack -> runAdNavigation(appAdsController) {
                            findNavController().navigateUp()
                        }
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
