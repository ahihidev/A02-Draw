package com.a02.draw.feature.home.screen.drawingcanvas

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
class DrawingCanvasFragment :
    BaseFragment<FragmentDrawingBinding>(FragmentDrawingBinding::inflate) {
    private val viewModel: DrawingCanvasViewModel by viewModels()
    private lateinit var controls: DrawingControlsBinder

    override fun setupViews(savedInstanceState: Bundle?) {
        binding.drawingControls.drawingStatusBarScrim.applyStatusBarHeight(
            lightStatusBarIcons = false,
        )
        controls = DrawingControlsBinder(binding.drawingControls) {
            viewModel.onAction(DrawingCanvasAction.Control(it))
        }
        binding.arDrawView.onTransformCommitted = {
            viewModel.onAction(DrawingCanvasAction.Transform(it))
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
                        DrawingCanvasEffect.NavigateBack -> findNavController().navigateUp()
                        DrawingCanvasEffect.NavigateOpacity -> findNavController().navigate(
                            R.id.drawingOpacityFragment,
                            null,
                            navOptions {
                                popUpTo(R.id.drawingCanvasFragment) { inclusive = true }
                            },
                        )

                        DrawingCanvasEffect.NavigateComplete -> findNavController().navigate(R.id.drawingCompleteFragment)
                        DrawingCanvasEffect.ShowProgressError -> Snackbar.make(
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
