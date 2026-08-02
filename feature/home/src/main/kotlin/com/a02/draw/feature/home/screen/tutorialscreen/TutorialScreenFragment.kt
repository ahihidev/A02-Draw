package com.a02.draw.feature.home.screen.tutorialscreen

import android.os.Bundle
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.a02.draw.core.ui.base.BaseFragment
import com.a02.draw.core.ui.extensions.applyStatusBarPadding
import com.a02.draw.core.ui.extensions.setDebouncedClickListener
import com.a02.draw.feature.home.R
import com.a02.draw.feature.home.common.image.HomeImageLoader
import com.a02.draw.feature.home.databinding.ScreenTutorialBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TutorialScreenFragment : BaseFragment<ScreenTutorialBinding>(ScreenTutorialBinding::inflate) {
    private val viewModel: TutorialScreenViewModel by viewModels()
    private val imageLoader = HomeImageLoader()

    override fun setupViews(savedInstanceState: Bundle?) {
        binding.toolbar.applyStatusBarPadding()
        binding.screenMode.isSelected = true
        binding.backButton.setDebouncedClickListener { viewModel.onAction(TutorialScreenAction.Back) }
        binding.cameraMode.setDebouncedClickListener { viewModel.onAction(TutorialScreenAction.SelectCameraMode) }
        binding.screenMode.setOnClickListener { }
        binding.drawButton.setDebouncedClickListener { viewModel.onAction(TutorialScreenAction.Start) }
    }

    override fun observeData() {
        collectWhenStarted {
            launch {
                viewModel.state.collect { state ->
                    val uri = state.session.pickedImageUri
                    val image = state.session.referenceImage
                    when {
                        uri != null -> {
                            imageLoader.loadUri(
                                binding.cameraModeImage,
                                uri,
                                R.drawable.drawing_trace_overlay
                            )
                            imageLoader.loadUri(
                                binding.screenModeImage,
                                uri,
                                R.drawable.drawing_trace_overlay
                            )
                        }

                        image != null -> {
                            imageLoader.load(
                                binding.cameraModeImage,
                                image,
                                R.drawable.drawing_trace_overlay
                            )
                            imageLoader.load(
                                binding.screenModeImage,
                                image,
                                R.drawable.drawing_trace_overlay
                            )
                        }
                    }
                }
            }
            launch {
                viewModel.effects.collect { effect ->
                    when (effect) {
                        TutorialScreenEffect.NavigateBack -> findNavController().navigateUp()
                        TutorialScreenEffect.NavigateCameraMode -> findNavController()
                            .navigate(R.id.action_tutorialScreenFragment_to_tutorialCameraFragment)

                        TutorialScreenEffect.NavigateCanvas -> findNavController()
                            .navigate(R.id.drawingCanvasFragment)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        imageLoader.close()
        super.onDestroyView()
    }
}
