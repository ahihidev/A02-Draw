package com.a02.draw.onboarding.lessons

import android.os.Bundle
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.a02.draw.R
import com.a02.draw.core.ui.base.BaseFragment
import com.a02.draw.core.ui.extensions.setDebouncedClickListener
import com.a02.draw.databinding.ScreenOnboardingLessonsBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LessonsFragment : BaseFragment<ScreenOnboardingLessonsBinding>(
    ScreenOnboardingLessonsBinding::inflate,
) {
    private val viewModel: LessonsViewModel by viewModels()

    override fun setupViews(savedInstanceState: Bundle?) {
        binding.continueButton.setDebouncedClickListener {
            viewModel.onAction(LessonsAction.Continue)
        }
    }

    override fun observeData() {
        collectWhenStarted {
            viewModel.effects.collect {
                findNavController().navigate(R.id.topicsFragment)
            }
        }
    }
}
