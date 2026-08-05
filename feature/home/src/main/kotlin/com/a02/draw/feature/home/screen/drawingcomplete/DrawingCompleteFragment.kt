package com.a02.draw.feature.home.screen.drawingcomplete

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.animation.OvershootInterpolator
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.a02.draw.core.ui.base.BaseFragment
import com.a02.draw.core.ui.extensions.applyStatusBarPadding
import com.a02.draw.core.ui.extensions.applySystemBarsPadding
import com.a02.draw.core.ui.extensions.setDebouncedClickListener
import com.a02.draw.feature.home.R
import com.a02.draw.feature.home.common.image.HomeImageLoader
import com.a02.draw.feature.home.common.model.DrawingMode
import com.a02.draw.feature.home.common.motion.HomeMotion
import com.a02.draw.feature.home.common.motion.pulse
import com.a02.draw.feature.home.databinding.ScreenDrawingCompleteBinding
import com.a02.draw.feature.home.screen.resultcamera.ResultCameraActivity
import com.a02.draw.feature.home.screen.tutorialcamera.RESUME_CAMERA_REQUEST_KEY
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DrawingCompleteFragment :
    BaseFragment<ScreenDrawingCompleteBinding>(ScreenDrawingCompleteBinding::inflate) {
    private val viewModel: DrawingCompleteViewModel by viewModels()
    private val imageLoader = HomeImageLoader()
    private var hasRenderedState = false
    private var lastRenderedUri: String? = null
    private var lastSaved = false
    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) launchResultCamera() else Snackbar.make(
            binding.root,
            R.string.camera_permission_required,
            Snackbar.LENGTH_SHORT,
        ).show()
    }
    private val resultCamera = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let {
                viewModel.onAction(DrawingCompleteAction.PhotoCaptured(it.toString()))
            }
        }
    }

    override fun setupViews(savedInstanceState: Bundle?) {
        binding.root.applySystemBarsPadding(includeTop = false)
        binding.toolbar.applyStatusBarPadding()
        binding.backButton.setDebouncedClickListener { viewModel.onAction(DrawingCompleteAction.Back) }
        binding.homeButton.setDebouncedClickListener { viewModel.onAction(DrawingCompleteAction.Home) }
        binding.primaryButton.setDebouncedClickListener {
            viewModel.onAction(
                if (viewModel.state.value.capturedUri == null) DrawingCompleteAction.TakePhoto
                else DrawingCompleteAction.Share,
            )
        }
        binding.secondaryButton.setDebouncedClickListener {
            viewModel.onAction(
                if (viewModel.state.value.capturedUri == null) DrawingCompleteAction.ContinueDrawing
                else DrawingCompleteAction.RetakePhoto,
            )
        }
        binding.celebrationAnimation.setFailureListener { failure ->
            Log.w(TAG, "Unable to load drawing completion celebration", failure)
            binding.celebrationAnimation.isVisible = false
        }
        if (savedInstanceState == null) {
            playEntranceAnimation()
            playCelebration()
        } else {
            binding.celebrationAnimation.isVisible = false
        }
    }

    override fun observeData() {
        collectWhenStarted {
            launch {
                viewModel.state.collect { state ->
                    val shouldRevealPhoto = hasRenderedState &&
                            lastRenderedUri != state.capturedUri && state.capturedUri != null
                    binding.title.setText(
                        if (state.capturedUri == null) R.string.good_job
                        else R.string.photo_saved_title,
                    )
                    binding.description.setText(
                        if (state.capturedUri == null) R.string.take_photo_description
                        else R.string.drawing_saved_description,
                    )
                    binding.primaryButton.setText(
                        if (state.capturedUri == null) R.string.take_photo else R.string.share_drawing,
                    )
                    binding.secondaryButton.setText(
                        if (state.capturedUri == null) R.string.not_finished
                        else R.string.retake_photo,
                    )
                    binding.saveStatus.text = when {
                        state.isSaving -> getString(R.string.saving_drawing)
                        state.isSaved -> getString(R.string.drawing_saved)
                        else -> ""
                    }
                    if (hasRenderedState && state.isSaved && !lastSaved) {
                        binding.saveStatus.pulse(1.12f)
                    }
                    binding.primaryButton.isEnabled = !state.isSaving
                    binding.secondaryButton.isEnabled = !state.isSaving
                    imageLoader.loadUri(
                        binding.drawingImage,
                        state.capturedUri,
                        R.drawable.complete_art,
                        retainDrawableWhileLoading = true,
                        onLoaded = if (shouldRevealPhoto) ::playPhotoCapturedAnimation else null,
                    )
                    lastRenderedUri = state.capturedUri
                    lastSaved = state.isSaved
                    hasRenderedState = true
                }
            }
            launch { viewModel.effects.collect(::handleEffect) }
        }
    }

    private fun handleEffect(effect: DrawingCompleteEffect) {
        when (effect) {
            DrawingCompleteEffect.NavigateHome -> findNavController().navigate(
                R.id.mainHomeFragment,
                null,
                NavOptions.Builder().setPopUpTo(R.id.mainHomeFragment, true).build(),
            )

            DrawingCompleteEffect.LaunchResultCamera -> requestOrLaunchResultCamera()

            is DrawingCompleteEffect.Share -> runCatching {
                startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "image/*"
                            putExtra(Intent.EXTRA_STREAM, effect.uri.toUri())
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        },
                        getString(R.string.share_drawing),
                    ),
                )
            }.onFailure {
                Snackbar.make(binding.root, R.string.generic_error, Snackbar.LENGTH_SHORT).show()
            }

            is DrawingCompleteEffect.ContinueDrawing -> resumeDrawing(effect.mode)
            DrawingCompleteEffect.ShowSaveError -> Snackbar.make(
                binding.root,
                R.string.generic_error,
                Snackbar.LENGTH_SHORT,
            ).show()
        }
    }

    private fun requestOrLaunchResultCamera() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) launchResultCamera() else cameraPermission.launch(Manifest.permission.CAMERA)
    }

    private fun launchResultCamera() {
        resultCamera.launch(
            ResultCameraActivity.intent(requireContext()),
            ActivityOptionsCompat.makeCustomAnimation(
                requireContext(),
                R.anim.motion_camera_enter,
                R.anim.motion_screen_fade_out,
            ),
        )
    }

    private fun playEntranceAnimation() {
        if (!HomeMotion.enabled()) return
        val distance = 18f * resources.displayMetrics.density
        val views = listOf(
            binding.title,
            binding.description,
            binding.resultCard,
            binding.saveStatus,
            binding.primaryButton,
            binding.secondaryButton,
        )
        views.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = distance
            if (view === binding.resultCard) {
                view.scaleX = 0.92f
                view.scaleY = 0.92f
            }
            binding.root.doOnPreDraw {
                view.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setStartDelay(index * ENTRANCE_STAGGER_MILLIS)
                    .setDuration(ENTRANCE_DURATION_MILLIS)
                    .setInterpolator(FastOutSlowInInterpolator())
                    .start()
            }
        }
    }

    private fun playPhotoCapturedAnimation() {
        if (!HomeMotion.enabled()) return
        val distance = 12f * resources.displayMetrics.density
        binding.resultCard.apply {
            animate().cancel()
            alpha = 0.55f
            scaleX = 0.88f
            scaleY = 0.88f
            animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(PHOTO_REVEAL_DURATION_MILLIS)
                .setInterpolator(OvershootInterpolator(0.8f))
                .start()
        }
        listOf(
            binding.title,
            binding.description,
            binding.saveStatus
        ).forEachIndexed { index, view ->
            view.animate().cancel()
            view.alpha = 0f
            view.translationY = distance
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(index * PHOTO_TEXT_STAGGER_MILLIS)
                .setDuration(PHOTO_TEXT_DURATION_MILLIS)
                .setInterpolator(FastOutSlowInInterpolator())
                .start()
        }
    }

    private fun playCelebration() {
        if (!HomeMotion.enabled()) {
            binding.celebrationAnimation.isVisible = false
            return
        }
        binding.celebrationAnimation.isVisible = true
        binding.celebrationAnimation.doOnPreDraw {
            binding.celebrationAnimation.playAnimation()
        }
    }

    private fun resumeDrawing(mode: DrawingMode) {
        val navController = findNavController()
        if (mode == DrawingMode.CAMERA) {
            navController.previousBackStackEntry
                ?.takeIf { it.destination.id == R.id.tutorialCameraFragment }
                ?.savedStateHandle
                ?.set(RESUME_CAMERA_REQUEST_KEY, true)
        }
        navController.navigateUp()
    }

    override fun onDestroyView() {
        binding.celebrationAnimation.cancelAnimation()
        listOf(
            binding.title,
            binding.description,
            binding.resultCard,
            binding.saveStatus,
            binding.primaryButton,
            binding.secondaryButton,
        ).forEach { it.animate().cancel() }
        imageLoader.close()
        super.onDestroyView()
    }

    private companion object {
        const val TAG = "DrawingComplete"
        const val ENTRANCE_DURATION_MILLIS = 320L
        const val ENTRANCE_STAGGER_MILLIS = 45L
        const val PHOTO_REVEAL_DURATION_MILLIS = 340L
        const val PHOTO_TEXT_DURATION_MILLIS = 300L
        const val PHOTO_TEXT_STAGGER_MILLIS = 45L
    }
}
