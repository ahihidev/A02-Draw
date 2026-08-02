package com.a02.draw.onboarding.projector

import android.os.Bundle
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.a02.draw.R
import com.a02.draw.core.ui.base.BaseFragment
import com.a02.draw.core.ui.extensions.setDebouncedClickListener
import com.a02.draw.databinding.ScreenOnboardingProjectorBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ProjectorFragment : BaseFragment<ScreenOnboardingProjectorBinding>(
    ScreenOnboardingProjectorBinding::inflate,
) {
    private val viewModel: ProjectorViewModel by viewModels()

    override fun setupViews(savedInstanceState: Bundle?) {
        binding.continueButton.setDebouncedClickListener {
            viewModel.onAction(ProjectorAction.Continue)
        }
    }

    override fun observeData() {
        collectWhenStarted {
            viewModel.effects.collect {
                findNavController().navigate(R.id.lightboxFragment)
            }
        }
    }
}
