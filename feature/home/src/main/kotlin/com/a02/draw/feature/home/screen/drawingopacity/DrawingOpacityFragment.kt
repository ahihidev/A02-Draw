package com.a02.draw.feature.home.screen.drawingopacity

import android.os.Bundle
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.a02.draw.core.ui.base.BaseFragment
import com.a02.draw.core.ui.extensions.applyStatusBarHeight
import com.a02.draw.feature.home.R
import com.a02.draw.feature.home.common.drawing.DrawingControlsBinder
import com.a02.draw.feature.home.databinding.FragmentDrawingBinding
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DrawingOpacityFragment :
    BaseFragment<FragmentDrawingBinding>(FragmentDrawingBinding::inflate) {
    private val viewModel: DrawingOpacityViewModel by viewModels()
    private lateinit var controls: DrawingControlsBinder

    override fun setupViews(savedInstanceState: Bundle?) {
        binding.drawingControls.drawingStatusBarScrim.applyStatusBarHeight(
            lightStatusBarIcons = false,
        )
        controls = DrawingControlsBinder(binding.drawingControls) {
            viewModel.onAction(DrawingOpacityAction.Control(it))
        }
        binding.arDrawView.onTransformCommitted = {
            viewModel.onAction(DrawingOpacityAction.Transform(it))
        }
    }

    override fun observeData() {
        collectWhenStarted {
            launch {
                viewModel.state.collect { state ->
                    binding.arDrawView.render(state.session)
                    controls.render(state.session)
                }
            }
            launch {
                viewModel.effects.collect { effect ->
                    when (effect) {
                        DrawingOpacityEffect.NavigateBack -> findNavController().navigateUp()
                        DrawingOpacityEffect.NavigateCanvas -> findNavController().navigate(
                            R.id.drawingCanvasFragment,
                            null,
                            navOptions {
                                popUpTo(R.id.drawingOpacityFragment) { inclusive = true }
                            },
                        )

                        DrawingOpacityEffect.NavigateComplete -> findNavController().navigate(R.id.drawingCompleteFragment)
                        DrawingOpacityEffect.ShowProgressError -> Snackbar.make(
                            binding.root,
                            R.string.generic_error,
                            Snackbar.LENGTH_SHORT,
                        ).show()
                    }
                }
            }
        }
    }

}
