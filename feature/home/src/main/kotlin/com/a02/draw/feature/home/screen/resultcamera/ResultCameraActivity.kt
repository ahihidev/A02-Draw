package com.a02.draw.feature.home.screen.resultcamera

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.os.Environment
import android.view.animation.OvershootInterpolator
import androidx.activity.OnBackPressedCallback
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.core.content.FileProvider
import androidx.core.view.doOnPreDraw
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.lifecycleScope
import com.a02.draw.core.ui.base.BaseActivity
import com.a02.draw.core.ui.extensions.setDebouncedClickListener
import com.a02.draw.feature.home.R
import com.a02.draw.feature.home.common.motion.HomeMotion
import com.a02.draw.feature.home.common.motion.crossfadeVisible
import com.a02.draw.feature.home.common.motion.pulse
import com.a02.draw.feature.home.databinding.ActivityResultCameraBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ResultCameraActivity :
    BaseActivity<ActivityResultCameraBinding>(ActivityResultCameraBinding::inflate) {
    private var cameraController: LifecycleCameraController? = null
    private var captureExecutor: ExecutorService? = null
    private var isCapturing = false

    override fun setupViews(savedInstanceState: Bundle?) {
        binding.cameraPreview.implementationMode =
            androidx.camera.view.PreviewView.ImplementationMode.PERFORMANCE
        binding.cameraPreview.scaleType = androidx.camera.view.PreviewView.ScaleType.FILL_CENTER
        binding.backButton.setDebouncedClickListener { finish() }
        binding.shutterButton.setDebouncedClickListener {
            binding.shutterButton.pulse(1.08f)
            captureDrawing()
        }
        binding.switchCameraButton.setDebouncedClickListener {
            rotateCameraSwitch()
            switchCamera()
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finish()
        })
        playEntranceAnimation()
        startCamera()
    }

    private fun playEntranceAnimation() {
        if (!HomeMotion.enabled()) return
        val distance = 16f * resources.displayMetrics.density
        binding.cameraPreview.alpha = 0f
        binding.topScrim.alpha = 0f
        binding.backButton.alpha = 0f
        binding.backButton.translationX = -distance
        binding.cameraTitle.alpha = 0f
        binding.cameraTitle.translationY = -distance
        binding.shutterButton.alpha = 0f
        binding.shutterButton.scaleX = 0.65f
        binding.shutterButton.scaleY = 0.65f
        binding.switchCameraButton.alpha = 0f
        binding.switchCameraButton.scaleX = 0.8f
        binding.switchCameraButton.scaleY = 0.8f
        binding.root.doOnPreDraw {
            binding.cameraPreview.animate()
                .alpha(1f)
                .setDuration(PREVIEW_ENTER_DURATION_MILLIS)
                .start()
            binding.topScrim.animate()
                .alpha(1f)
                .setDuration(CONTROL_ENTER_DURATION_MILLIS)
                .start()
            binding.backButton.animate()
                .alpha(1f)
                .translationX(0f)
                .setStartDelay(CONTROL_ENTER_DELAY_MILLIS)
                .setDuration(CONTROL_ENTER_DURATION_MILLIS)
                .setInterpolator(FastOutSlowInInterpolator())
                .start()
            binding.cameraTitle.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(CONTROL_ENTER_DELAY_MILLIS)
                .setDuration(CONTROL_ENTER_DURATION_MILLIS)
                .setInterpolator(FastOutSlowInInterpolator())
                .start()
            binding.shutterButton.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(SHUTTER_ENTER_DELAY_MILLIS)
                .setDuration(SHUTTER_ENTER_DURATION_MILLIS)
                .setInterpolator(OvershootInterpolator(1.1f))
                .start()
            binding.switchCameraButton.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(SWITCH_ENTER_DELAY_MILLIS)
                .setDuration(CONTROL_ENTER_DURATION_MILLIS)
                .setInterpolator(FastOutSlowInInterpolator())
                .start()
        }
    }

    private fun startCamera() {
        cameraController = LifecycleCameraController(this).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            bindToLifecycle(this@ResultCameraActivity)
            binding.cameraPreview.controller = this
        }
    }

    private fun switchCamera() {
        val controller = cameraController ?: return
        val selector = if (controller.cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        runCatching {
            check(controller.hasCamera(selector))
            controller.cameraSelector = selector
        }.onFailure {
            Snackbar.make(binding.root, R.string.camera_lens_unavailable, Snackbar.LENGTH_SHORT)
                .show()
        }
    }

    private fun captureDrawing() {
        val controller = cameraController ?: return
        if (isCapturing) return
        isCapturing = true
        playCaptureFlash()
        renderCapturing(true)
        val executor = captureExecutor ?: Executors.newSingleThreadExecutor().also {
            captureExecutor = it
        }
        controller.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val rotation = image.imageInfo.rotationDegrees
                val bitmap = runCatching { image.toBitmap() }.getOrNull()
                image.close()
                if (bitmap == null) return showCaptureError()
                lifecycleScope.launch(Dispatchers.IO) {
                    val uri = saveOrientedBitmap(bitmap, rotation)
                    withContext(Dispatchers.Main) {
                        if (uri == null) showCaptureError() else {
                            setResult(
                                RESULT_OK,
                                Intent().setData(uri)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                            )
                            finish()
                        }
                    }
                }
            }

            override fun onError(exception: ImageCaptureException) = showCaptureError()
        })
    }

    private fun saveOrientedBitmap(source: Bitmap, rotationDegrees: Int): android.net.Uri? {
        val output = if (rotationDegrees % 360 == 0) source else Bitmap.createBitmap(
            source,
            0,
            0,
            source.width,
            source.height,
            Matrix().apply { postRotate(rotationDegrees.toFloat()) },
            true,
        )
        if (output !== source) source.recycle()
        val directory = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            ?: return null.also { output.recycle() }
        if (!directory.exists() && !directory.mkdirs()) return null.also { output.recycle() }
        val file = File(directory, "drawing-result-${System.currentTimeMillis()}.jpg")
        val saved = runCatching {
            FileOutputStream(file).use { output.compress(Bitmap.CompressFormat.JPEG, 94, it) }
        }.getOrDefault(false)
        output.recycle()
        if (!saved) {
            file.delete()
            return null
        }
        return runCatching {
            FileProvider.getUriForFile(this, "$packageName.files", file)
        }.getOrElse {
            file.delete()
            null
        }
    }

    private fun renderCapturing(capturing: Boolean) {
        binding.captureProgress.crossfadeVisible(capturing, HomeMotion.MICRO)
        binding.shutterButton.isEnabled = !capturing
        binding.switchCameraButton.isEnabled = !capturing
    }

    private fun playCaptureFlash() {
        binding.captureFlash.animate().cancel()
        if (!HomeMotion.enabled()) {
            binding.captureFlash.alpha = 0f
            return
        }
        binding.captureFlash.alpha = 0f
        binding.captureFlash.animate().alpha(0.72f).setDuration(70L).withEndAction {
            binding.captureFlash.animate().alpha(0f).setDuration(110L).start()
        }.start()
    }

    private fun rotateCameraSwitch() {
        binding.switchCameraButton.animate().cancel()
        if (!HomeMotion.enabled()) return
        binding.switchCameraButton.animate().rotationBy(180f)
            .setDuration(HomeMotion.CONTENT).setInterpolator(FastOutSlowInInterpolator()).start()
    }

    private fun showCaptureError() {
        binding.root.post {
            isCapturing = false
            renderCapturing(false)
            Snackbar.make(binding.root, R.string.generic_error, Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        binding.cameraPreview.controller = null
        cameraController?.unbind()
        cameraController = null
        captureExecutor?.shutdown()
        captureExecutor = null
        super.onDestroy()
    }

    companion object {
        private const val PREVIEW_ENTER_DURATION_MILLIS = 300L
        private const val CONTROL_ENTER_DELAY_MILLIS = 80L
        private const val CONTROL_ENTER_DURATION_MILLIS = 280L
        private const val SHUTTER_ENTER_DELAY_MILLIS = 130L
        private const val SHUTTER_ENTER_DURATION_MILLIS = 430L
        private const val SWITCH_ENTER_DELAY_MILLIS = 180L

        fun intent(context: Context) = Intent(context, ResultCameraActivity::class.java)
    }
}
