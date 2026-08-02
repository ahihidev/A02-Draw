package com.a02.draw.feature.home.screen.drawingcomplete

import android.content.Intent
import android.os.Bundle
import androidx.core.net.toUri
import androidx.fragment.app.viewModels
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.a02.draw.core.ui.base.BaseFragment
import com.a02.draw.core.ui.extensions.applyStatusBarPadding
import com.a02.draw.core.ui.extensions.setDebouncedClickListener
import com.a02.draw.feature.home.R
import com.a02.draw.feature.home.common.image.HomeImageLoader
import com.a02.draw.feature.home.common.model.DrawingMode
import com.a02.draw.feature.home.databinding.ScreenDrawingCompleteBinding
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DrawingCompleteFragment :
    BaseFragment<ScreenDrawingCompleteBinding>(ScreenDrawingCompleteBinding::inflate) {
    private val viewModel: DrawingCompleteViewModel by viewModels()
    private val imageLoader = HomeImageLoader()

    override fun setupViews(savedInstanceState: Bundle?) {
        binding.toolbar.applyStatusBarPadding()
        binding.backButton.setDebouncedClickListener { viewModel.onAction(DrawingCompleteAction.Back) }
        binding.homeButton.setDebouncedClickListener { viewModel.onAction(DrawingCompleteAction.Home) }
        binding.primaryButton.setDebouncedClickListener { viewModel.onAction(DrawingCompleteAction.Share) }
        binding.secondaryButton.setDebouncedClickListener { viewModel.onAction(DrawingCompleteAction.Retake) }
    }

    override fun observeData() {
        collectWhenStarted {
            launch {
                viewModel.state.collect { state ->
                    binding.description.setText(
                        if (state.capturedUri == null) R.string.take_photo_description
                        else R.string.drawing_saved_description,
                    )
                    binding.primaryButton.setText(
                        if (state.capturedUri == null) R.string.take_photo else R.string.share_drawing,
                    )
                    imageLoader.loadUri(
                        binding.drawingImage,
                        state.capturedUri,
                        R.drawable.complete_art
                    )
                }
            }
            launch { viewModel.effects.collect(::handleEffect) }
        }
    }

    private fun handleEffect(effect: DrawingCompleteEffect) {
        when (effect) {
            DrawingCompleteEffect.NavigateBack -> findNavController().navigateUp()
            DrawingCompleteEffect.NavigateHome -> findNavController().navigate(
                R.id.mainHomeFragment,
                null,
                NavOptions.Builder().setPopUpTo(R.id.mainHomeFragment, true).build(),
            )

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

            is DrawingCompleteEffect.Retake -> retake(effect.mode)
            DrawingCompleteEffect.ShowSaveError -> Snackbar.make(
                binding.root,
                R.string.generic_error,
                Snackbar.LENGTH_SHORT,
            ).show()
        }
    }

    private fun retake(mode: DrawingMode) {
        val navController = findNavController()
        val destination = if (mode == DrawingMode.CAMERA) {
            R.id.tutorialCameraFragment
        } else {
            R.id.tutorialScreenFragment
        }
        if (navController.popBackStack(destination, false)) return
        navController.navigate(
            destination,
            null,
            NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setPopUpTo(R.id.drawingCompleteFragment, true)
                .build(),
        )
    }

    override fun onDestroyView() {
        imageLoader.close()
        super.onDestroyView()
    }
}
