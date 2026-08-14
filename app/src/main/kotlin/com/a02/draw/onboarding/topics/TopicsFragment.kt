package com.a02.draw.onboarding.topics

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.a02.draw.R
import com.a02.draw.core.ui.base.BaseFragment
import com.a02.draw.core.ui.extensions.setAdaptiveGridLayoutManager
import com.a02.draw.core.ui.extensions.setDebouncedClickListener
import com.a02.draw.databinding.ScreenOnboardingTopicsBinding
import com.a02.draw.onboarding.common.image.OnboardingImageLoader
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TopicsFragment : BaseFragment<ScreenOnboardingTopicsBinding>(
    ScreenOnboardingTopicsBinding::inflate,
) {
    private val viewModel: TopicsViewModel by viewModels()
    private val imageLoader = OnboardingImageLoader()
    private val adapter = OnboardingTopicAdapter(imageLoader) {
        viewModel.onAction(TopicsAction.ToggleTopic(it))
    }

    override fun setupViews(savedInstanceState: Bundle?) {
        binding.topicList.setAdaptiveGridLayoutManager(
            minimumItemWidth = resources.getDimensionPixelSize(R.dimen.onboarding_topic_min_cell_width),
            minimumSpanCount = MINIMUM_TOPIC_COLUMNS,
        )
        binding.topicList.adapter = adapter
        binding.continueButton.setDebouncedClickListener {
            viewModel.onAction(TopicsAction.Continue)
        }
        binding.retryButton.setDebouncedClickListener {
            viewModel.onAction(TopicsAction.Retry)
        }
    }

    override fun observeData() {
        collectWhenStarted {
            launch { viewModel.state.collect(::render) }
            launch {
                viewModel.effects.collect { effect ->
                    when (effect) {
                        TopicsEffect.NavigateNext -> findNavController().navigate(R.id.preparingFragment)
                        TopicsEffect.ShowSelectionRequired -> Toast.makeText(
                            requireContext(),
                            R.string.onboarding_topics_selection_required,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            }
        }
    }

    private fun render(state: TopicsUiState) {
        val canContinue = state.selectedTopicIds.isNotEmpty() &&
                !state.isLoading &&
                !state.hasError
        binding.loadingIndicator.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        binding.errorPanel.visibility = if (state.hasError) View.VISIBLE else View.GONE
        binding.topicList.visibility = if (!state.isLoading && !state.hasError) {
            View.VISIBLE
        } else {
            View.GONE
        }
        adapter.submitList(
            state.topics.map { topic ->
                OnboardingTopicItem(topic, topic.id in state.selectedTopicIds)
            },
        )
        binding.continueButton.isEnabled = canContinue
        binding.continueButton.alpha = if (canContinue) 1f else DISABLED_BUTTON_ALPHA
    }

    override fun onDestroyView() {
        binding.topicList.adapter = null
        imageLoader.close()
        super.onDestroyView()
    }

    private companion object {
        const val MINIMUM_TOPIC_COLUMNS = 2
        const val DISABLED_BUTTON_ALPHA = 0.48f
    }
}
