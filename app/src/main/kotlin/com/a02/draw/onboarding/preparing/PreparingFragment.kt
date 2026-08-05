package com.a02.draw.onboarding.preparing

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.viewModels
import com.a02.draw.MainActivity
import com.a02.draw.R
import com.a02.draw.core.ui.base.BaseFragment
import com.a02.draw.databinding.ScreenOnboardingPreparingBinding
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PreparingFragment : BaseFragment<ScreenOnboardingPreparingBinding>(
    ScreenOnboardingPreparingBinding::inflate,
) {
    private val viewModel: PreparingViewModel by viewModels()

    override fun setupViews(savedInstanceState: Bundle?) = Unit

    override fun observeData() {
        collectWhenStarted {
            launch { viewModel.state.collect(::render) }
            launch { viewModel.effects.collect(::handleEffect) }
        }
    }

    private fun render(state: PreparingUiState) = with(binding) {
        preparingFavoritesProgress.progress = state.favoritesProgress
        preparingReferencesProgress.progress = state.referencesProgress
        preparingToolsProgress.progress = state.toolsProgress
    }

    private fun handleEffect(effect: PreparingEffect) {
        when (effect) {
            PreparingEffect.OpenMain -> {
                startActivity(
                    Intent(requireContext(), MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
                )
                requireActivity().finish()
            }

            is PreparingEffect.ShowMessage -> Snackbar.make(
                binding.root,
                effect.messageRes,
                Snackbar.LENGTH_INDEFINITE,
            ).setAction(R.string.onboarding_retry) {
                viewModel.onAction(PreparingAction.Start)
            }.show()
        }
    }
}
