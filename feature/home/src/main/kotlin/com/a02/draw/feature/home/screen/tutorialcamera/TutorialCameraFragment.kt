package com.a02.draw.feature.home.screen.tutorialcamera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
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
import com.a02.draw.feature.home.screen.camera.CameraActivity
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TutorialCameraFragment : BaseFragment<ScreenTutorialBinding>(ScreenTutorialBinding::inflate) {
    private val viewModel: TutorialCameraViewModel by viewModels()
    private val imageLoader = HomeImageLoader()

    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) launchCamera() else Snackbar.make(
            binding.root,
            R.string.camera_permission_required,
            Snackbar.LENGTH_SHORT,
        ).show()
    }

    private val camera = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != android.app.Activity.RESULT_OK) return@registerForActivityResult
        if (result.data?.getBooleanExtra(CameraActivity.EXTRA_DRAWING_FINISHED, false) == true) {
            viewModel.onAction(TutorialCameraAction.DrawingFinished)
        } else {
            result.data?.data?.let {
                viewModel.onAction(TutorialCameraAction.CameraCaptured(it.toString()))
            }
        }
    }

    override fun setupViews(savedInstanceState: Bundle?) {
        binding.toolbar.applyStatusBarPadding()
        binding.cameraMode.renderSelectedCard(true)
        binding.screenMode.renderSelectedCard(false)
        binding.backButton.setDebouncedClickListener { viewModel.onAction(TutorialCameraAction.Back) }
        binding.cameraMode.setOnClickListener { }
        binding.screenMode.setDebouncedClickListener { viewModel.onAction(TutorialCameraAction.SelectScreenMode) }
        binding.drawButton.setDebouncedClickListener { viewModel.onAction(TutorialCameraAction.Start) }
        if (savedInstanceState == null) {
            playStaggeredEntrance(
                listOf(binding.cameraMode, binding.screenMode),
                horizontalDirections = listOf(-1, 1),
                verticalDp = 0f,
            )
            binding.cameraMode.scaleX = 1f
            binding.cameraMode.scaleY = 1f
            binding.screenMode.scaleX = 0.97f
            binding.screenMode.scaleY = 0.97f
            binding.drawButton.enterFromBottom(HomeMotion.CONTENT + HomeMotion.STAGGER)
        }
        val shouldResumeDrawing = findNavController().currentBackStackEntry
            ?.savedStateHandle
            ?.remove<Boolean>(RESUME_CAMERA_REQUEST_KEY) == true
        if (shouldResumeDrawing) binding.root.post(::requestOrLaunchCamera)
    }

    override fun observeData() {
        collectWhenStarted {
            launch {
                viewModel.state.collect { state ->
                    loadReference(state.session.pickedImageUri, state.session.referenceImage)
                }
            }
            launch {
                viewModel.effects.collect { effect ->
                    when (effect) {
                        TutorialCameraEffect.NavigateBack -> findNavController().navigateUp()
                        TutorialCameraEffect.NavigateScreenMode -> findNavController()
                            .navigate(R.id.action_tutorialCameraFragment_to_tutorialScreenFragment)

                        TutorialCameraEffect.LaunchCamera -> requestOrLaunchCamera()
                        TutorialCameraEffect.NavigateComplete -> findNavController()
                            .navigate(R.id.drawingCompleteFragment)
                    }
                }
            }
        }
    }

    private fun requestOrLaunchCamera() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) launchCamera() else cameraPermission.launch(Manifest.permission.CAMERA)
    }

    private fun launchCamera() {
        camera.launch(
            CameraActivity.intent(requireContext(), viewModel.state.value.session),
            ActivityOptionsCompat.makeCustomAnimation(
                requireContext(),
                R.anim.motion_camera_enter,
                R.anim.motion_screen_fade_out,
            ),
        )
    }

    private fun loadReference(uri: String?, image: com.a02.draw.domain.model.ContentImage?) {
        if (uri != null) {
            imageLoader.loadUri(binding.cameraModeImage, uri, R.drawable.drawing_trace_overlay)
            imageLoader.loadUri(binding.screenModeImage, uri, R.drawable.drawing_trace_overlay)
        } else if (image != null) {
            imageLoader.load(binding.cameraModeImage, image, R.drawable.drawing_trace_overlay)
            imageLoader.load(binding.screenModeImage, image, R.drawable.drawing_trace_overlay)
        }
    }

    override fun onDestroyView() {
        imageLoader.close()
        super.onDestroyView()
    }
}
