package com.a02.draw.feature.home.screen.drawingcanvas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.a02.draw.core.ui.base.BaseFragment
import com.a02.draw.core.ui.extensions.applyStatusBarHeight
import com.a02.draw.feature.home.R
import com.a02.draw.feature.home.common.drawing.DrawingControlsBinder
import com.a02.draw.feature.home.databinding.FragmentDrawingBinding
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

@AndroidEntryPoint
class DrawingCanvasFragment :
    BaseFragment<FragmentDrawingBinding>(FragmentDrawingBinding::inflate) {
    private val viewModel: DrawingCanvasViewModel by viewModels()
    private lateinit var controls: DrawingControlsBinder
    private var captureInProgress = false

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

                        DrawingCanvasEffect.CaptureCanvas -> captureCanvas()
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

    private fun captureCanvas() {
        if (captureInProgress || binding.arDrawView.width <= 0 || binding.arDrawView.height <= 0) return
        captureInProgress = true
        val bitmap = createBitmap(binding.arDrawView.width, binding.arDrawView.height)
        binding.arDrawView.drawExport(Canvas(bitmap))
        val context = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            val uri = withContext(Dispatchers.IO) { saveBitmap(context, bitmap) }
            captureInProgress = false
            if (uri != null) viewModel.onAction(DrawingCanvasAction.Captured(uri)) else {
                Snackbar.make(binding.root, R.string.generic_error, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveBitmap(context: Context, source: Bitmap): String? {
        val ratio = viewModel.state.value.session.cropRatio.outputRatio
        val output = source.cropTo(ratio)
        if (output !== source) source.recycle()
        val directory = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            ?: return null.also { output.recycle() }
        if (!directory.exists() && !directory.mkdirs()) return null.also { output.recycle() }
        val file = File(directory, "ar-drawing-${System.currentTimeMillis()}.jpg")
        val saved = runCatching {
            FileOutputStream(file).use { output.compress(Bitmap.CompressFormat.JPEG, 94, it) }
        }.getOrDefault(false)
        output.recycle()
        if (!saved) {
            file.delete()
            return null
        }
        return runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.files", file).toString()
        }.getOrElse {
            file.delete()
            null
        }
    }
}

private fun Bitmap.cropTo(targetRatio: Float?): Bitmap {
    if (targetRatio == null) return this
    val sourceRatio = width.toFloat() / height
    return if (sourceRatio > targetRatio) {
        val outputWidth = (height * targetRatio).roundToInt()
        Bitmap.createBitmap(this, (width - outputWidth) / 2, 0, outputWidth, height)
    } else {
        val outputHeight = (width / targetRatio).roundToInt()
        Bitmap.createBitmap(this, 0, (height - outputHeight) / 2, width, outputHeight)
    }
}

private val com.a02.draw.feature.home.common.model.DrawingCropRatio.outputRatio: Float?
    get() = when (this) {
        com.a02.draw.feature.home.common.model.DrawingCropRatio.RESET -> null
        com.a02.draw.feature.home.common.model.DrawingCropRatio.SQUARE -> 1f
        com.a02.draw.feature.home.common.model.DrawingCropRatio.PORTRAIT -> 9f / 16f
        com.a02.draw.feature.home.common.model.DrawingCropRatio.LANDSCAPE -> 16f / 9f
    }
