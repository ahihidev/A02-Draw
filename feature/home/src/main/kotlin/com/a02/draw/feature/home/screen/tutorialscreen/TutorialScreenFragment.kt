package com.a02.draw.feature.home.screen.tutorialscreen

import android.os.Bundle
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.a02.draw.core.ui.ads.AppAdPlacement
import com.a02.draw.core.ui.ads.AppAdsController
import com.a02.draw.core.ui.ads.AppNativeAdFormat
import com.a02.draw.core.ui.base.BaseFragment
import com.a02.draw.core.ui.extensions.applyStatusBarPadding
import com.a02.draw.core.ui.extensions.setDebouncedClickListener
import com.a02.draw.feature.home.R
import com.a02.draw.feature.home.common.image.HomeImageLoader
import com.a02.draw.feature.home.common.motion.HomeMotion
import com.a02.draw.feature.home.common.motion.enterFromBottom
import com.a02.draw.feature.home.common.motion.playStaggeredEntrance
import com.a02.draw.feature.home.common.motion.renderSelectedCard
import com.a02.draw.feature.home.databinding.ScreenTutorialBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TutorialScreenFragment : BaseFragment<ScreenTutorialBinding>(ScreenTutorialBinding::inflate) {
    @Inject
    lateinit var appAdsController: AppAdsController
    private val viewModel: TutorialScreenViewModel by viewModels()
    private val imageLoader = HomeImageLoader()

    override fun setupViews(savedInstanceState: Bundle?) {
        appAdsController.attachNative(
            binding.nativeAdContainer,
            AppAdPlacement.SELECT_MODE,
            AppNativeAdFormat.MEDIUM,
            viewLifecycleOwner,
        )
        binding.toolbar.applyStatusBarPadding()
        binding.cameraMode.renderSelectedCard(false)
        binding.screenMode.renderSelectedCard(true)
        binding.backButton.setDebouncedClickListener { viewModel.onAction(TutorialScreenAction.Back) }
        binding.cameraMode.setDebouncedClickListener { viewModel.onAction(TutorialScreenAction.SelectCameraMode) }
        binding.screenMode.setOnClickListener { }
        binding.drawButton.setDebouncedClickListener { viewModel.onAction(TutorialScreenAction.Start) }
        if (savedInstanceState == null) {
            playStaggeredEntrance(
                listOf(binding.cameraMode, binding.screenMode),
                horizontalDirections = listOf(-1, 1),
                verticalDp = 0f,
            )
            binding.cameraMode.scaleX = 0.97f
            binding.cameraMode.scaleY = 0.97f
            binding.screenMode.scaleX = 1f
            binding.screenMode.scaleY = 1f
            binding.drawButton.enterFromBottom(HomeMotion.CONTENT + HomeMotion.STAGGER)
        }
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
