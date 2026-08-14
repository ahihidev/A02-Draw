package com.a02.draw.feature.home.screen.camera

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.video.AudioConfig
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.a02.draw.core.ui.base.BaseActivity
import com.a02.draw.feature.home.R
import com.a02.draw.feature.home.common.drawing.DrawingControlsBinder
import com.a02.draw.feature.home.common.drawing.OverlayPreview
import com.a02.draw.feature.home.common.image.HomeImageLoader
import com.a02.draw.feature.home.common.model.DrawingCameraPanel
import com.a02.draw.feature.home.common.model.DrawingCameraRatio
import com.a02.draw.feature.home.common.model.DrawingCropRatio
import com.a02.draw.feature.home.common.motion.HomeMotion
import com.a02.draw.feature.home.common.session.DrawingSession
import com.a02.draw.feature.home.databinding.ActivityCameraBinding
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt

@AndroidEntryPoint
class CameraActivity : BaseActivity<ActivityCameraBinding>(ActivityCameraBinding::inflate) {
    private val viewModel: CameraViewModel by viewModels()
    private var latestSession = DrawingSession()
    private var cameraController: LifecycleCameraController? = null
    private var activeRecording: Recording? = null
    private var imageCaptureExecutor: ExecutorService? = null
    private var captureTimerJob: Job? = null
    private var captureInProgress = false
    private var videoCaptureEnabled = false
    private val imageLoader = HomeImageLoader()
    private lateinit var controls: DrawingControlsBinder

    override fun setupViews(savedInstanceState: Bundle?) {
        binding.cameraPreview.implementationMode =
            androidx.camera.view.PreviewView.ImplementationMode.PERFORMANCE
        binding.cameraPreview.scaleType = androidx.camera.view.PreviewView.ScaleType.FILL_CENTER
        binding.cameraOverlayImage.clipToOutline = true
        binding.arDrawView.setCameraOverlayExternal(true)
        binding.arDrawView.onTransformCommitted = { viewModel.onAction(CameraAction.Transform(it)) }
        binding.arDrawView.onOverlayPreview = ::renderOverlayPreview
        controls = DrawingControlsBinder(binding.drawingControls) {
            viewModel.onAction(CameraAction.Control(it))
        }
        binding.arDrawView.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                renderOverlayPreview(latestSession.toOverlayPreview())
            }
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finishCamera()
        })
        ensureCameraController()
    }

    override fun observeData() {
        collectWhenStarted {
            launch {
                viewModel.state.collect {
                    latestSession = it.session
                    render(it.session)
                }
            }
            launch { viewModel.effects.collect(::handleEffect) }
        }
    }

    private fun handleEffect(effect: CameraEffect) {
        when (effect) {
            CameraEffect.Finish -> finishCamera()
            CameraEffect.FinishDrawing -> {
                setResult(
                    RESULT_OK,
                    Intent().putExtra(EXTRA_DRAWING_FINISHED, true),
                )
                finish()
            }
            is CameraEffect.Capture -> scheduleCompositePhoto(effect.delaySeconds)
            is CameraEffect.SetTorch -> runCatching {
                cameraController?.enableTorch(effect.enabled)
            }.onFailure { showError() }

            is CameraEffect.SetZoom -> runCatching {
                val limits = cameraController?.zoomState?.value
                cameraController?.setZoomRatio(
                    if (limits == null) effect.zoom
                    else effect.zoom.coerceIn(limits.minZoomRatio, limits.maxZoomRatio),
                )
            }.onFailure { showError() }

            is CameraEffect.SetCameraLens -> setCameraLens(effect.useFrontCamera)
            is CameraEffect.SetRecording -> if (effect.enabled) startRecording() else stopRecording()
            CameraEffect.ShowProgressError -> showError()
        }
    }

    private fun render(session: DrawingSession) {
        binding.arDrawView.render(session)
        controls.render(session)
        binding.cameraOverlayContainer.visibility =
            if (session.overlayVisible) View.VISIBLE else View.INVISIBLE
        binding.cameraOverlayImage.alpha = session.opacity
        binding.cameraOverlayImage.colorFilter = if (session.removeImageEnabled) {
            REMOVE_LIGHT_BACKGROUND_FILTER
        } else null
        if (session.pickedImageUri != null) {
            imageLoader.loadUri(
                binding.cameraOverlayImage,
                session.pickedImageUri,
                R.drawable.drawing_trace_overlay,
            )
        } else {
            session.traceImage?.let {
                imageLoader.load(
                    binding.cameraOverlayImage,
                    it,
                    R.drawable.drawing_trace_overlay,
                    retainDrawableWhileLoading = true,
                )
            } ?: binding.cameraOverlayImage.setImageResource(R.drawable.drawing_trace_overlay)
        }
        configureCameraUseCases(
            session.cameraPanel == DrawingCameraPanel.RECORD || session.isRecording,
        )
        renderOverlayPreview(session.toOverlayPreview())
    }

    private fun ensureCameraController() {
        if (cameraController != null) return
        cameraController = LifecycleCameraController(this).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setVideoCaptureQualitySelector(
                QualitySelector.fromOrderedList(
                    listOf(Quality.HD, Quality.SD),
                    FallbackStrategy.lowerQualityOrHigherThan(Quality.SD),
                ),
            )
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            bindToLifecycle(this@CameraActivity)
            binding.cameraPreview.controller = this
        }
    }

    private fun configureCameraUseCases(videoEnabled: Boolean) {
        val controller = cameraController ?: return
        if (videoCaptureEnabled == videoEnabled) return
        controller.setEnabledUseCases(
            CameraController.IMAGE_CAPTURE or if (videoEnabled) CameraController.VIDEO_CAPTURE else 0,
        )
        videoCaptureEnabled = videoEnabled
    }

    private fun scheduleCompositePhoto(delaySeconds: Int) {
        if (captureInProgress || captureTimerJob?.isActive == true) return
        if (delaySeconds <= 0) {
            captureCompositePhoto()
            return
        }
        captureTimerJob = lifecycleScope.launch {
            binding.cameraCountdown.isVisible = true
            try {
                for (remaining in delaySeconds downTo 1) {
                    binding.cameraCountdown.text = getString(R.string.countdown_number, remaining)
                    binding.cameraCountdown.alpha = 0f
                    binding.cameraCountdown.scaleX = 0.78f
                    binding.cameraCountdown.scaleY = 0.78f
                    binding.cameraCountdown.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(COUNTDOWN_ANIMATION_MILLIS)
                        .start()
                    delay(1_000L)
                }
                binding.cameraCountdown.isVisible = false
                captureCompositePhoto()
            } finally {
                binding.cameraCountdown.animate().cancel()
                binding.cameraCountdown.isVisible = false
                captureTimerJob = null
            }
        }
    }

    private fun setCameraLens(useFrontCamera: Boolean) {
        val controller = cameraController ?: return
        val selector = if (useFrontCamera) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        val changed = runCatching {
            check(controller.hasCamera(selector))
            controller.enableTorch(false)
            controller.cameraSelector = selector
            controller.setZoomRatio(1f)
        }.isSuccess
        if (!changed) {
            viewModel.onAction(CameraAction.CameraLensRejected)
            showError(R.string.camera_lens_unavailable)
        }
    }

    private fun captureCompositePhoto() {
        if (captureInProgress) return
        captureInProgress = true
        playCaptureFlash()
        val controller = cameraController ?: return showCaptureError()
        val executor = imageCaptureExecutor ?: Executors.newSingleThreadExecutor().also {
            imageCaptureExecutor = it
        }
        controller.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val rotation = image.imageInfo.rotationDegrees
                val bitmap = runCatching { image.toBitmap() }.getOrNull()
                image.close()
                if (bitmap == null) showCaptureError() else binding.root.post {
                    composeAndSave(bitmap, rotation)
                }
            }

            override fun onError(exception: ImageCaptureException) = showCaptureError()
        })
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

    private fun composeAndSave(cameraBitmap: Bitmap, rotationDegrees: Int) {
        val width = binding.root.width
        val height = binding.root.height
        if (width <= 0 || height <= 0) {
            cameraBitmap.recycle()
            return showCaptureError()
        }
        val overlay = createBitmap(width, height)
        binding.arDrawView.drawExport(Canvas(overlay))
        lifecycleScope.launch(Dispatchers.Default) {
            val oriented = cameraBitmap.rotate(rotationDegrees)
            val composite = createBitmap(width, height)
            val canvas = Canvas(composite)
            canvas.drawCenterCrop(oriented, width, height)
            canvas.drawBitmap(
                overlay,
                0f,
                0f,
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
            )
            if (oriented !== cameraBitmap) oriented.recycle()
            cameraBitmap.recycle()
            overlay.recycle()
            val uri = saveBitmap(composite, latestSession.cameraRatio.outputRatio)
            withContext(Dispatchers.Main) {
                captureInProgress = false
                if (uri == null) showError() else {
                    setResult(
                        RESULT_OK,
                        Intent().setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                    )
                    finish()
                }
            }
        }
    }

    private fun saveBitmap(source: Bitmap, ratio: Float?): android.net.Uri? {
        val output = source.cropToRatio(ratio)
        if (output !== source) source.recycle()
        val directory = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
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
            FileProvider.getUriForFile(this, "$packageName.files", file)
        }.getOrElse {
            file.delete()
            null
        }
    }

    private fun startRecording() {
        ensureCameraController()
        configureCameraUseCases(true)
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "ar-drawing-${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Video.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_MOVIES}/AR Drawing"
                )
            }
        }
        val options = androidx.camera.video.MediaStoreOutputOptions.Builder(
            contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        ).setContentValues(values).build()
        activeRecording = runCatching {
            cameraController?.startRecording(
                options,
                AudioConfig.AUDIO_DISABLED,
                ContextCompat.getMainExecutor(this),
            ) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    activeRecording = null
                    viewModel.onAction(CameraAction.RecordingFinished)
                    Snackbar.make(
                        binding.root,
                        if (event.hasError()) R.string.video_recording_error else R.string.video_saved,
                        Snackbar.LENGTH_SHORT,
                    ).show()
                }
            }
        }.getOrNull()
        if (activeRecording == null) {
            viewModel.onAction(CameraAction.RecordingFinished)
            showError(R.string.video_recording_error)
        }
    }

    private fun stopRecording() {
        activeRecording?.stop()
        activeRecording = null
    }

    private fun renderOverlayPreview(preview: OverlayPreview) {
        val transform = binding.arDrawView.layoutTransform()
        val (logicalWidth, logicalHeight) = when (latestSession.cropRatio) {
            DrawingCropRatio.PORTRAIT -> 184f to 328f
            DrawingCropRatio.LANDSCAPE -> 328f to 184f
            DrawingCropRatio.RESET, DrawingCropRatio.SQUARE -> 328f to 328f
        }
        val width = (logicalWidth * transform.scale).toInt().coerceAtLeast(1)
        val height = (logicalHeight * transform.scale).toInt().coerceAtLeast(1)
        binding.cameraOverlayContainer.layoutParams = FrameLayout.LayoutParams(width, height)
        binding.cameraOverlayContainer.x =
            transform.offsetX + 180f * transform.scale - width / 2f + preview.offsetX * transform.scale
        binding.cameraOverlayContainer.y =
            transform.offsetY + 358f * transform.scale - height / 2f + preview.offsetY * transform.scale
        binding.cameraOverlayContainer.scaleX =
            if (latestSession.overlayFlipped) -preview.zoom else preview.zoom
        binding.cameraOverlayContainer.scaleY = preview.zoom
        binding.cameraOverlayContainer.rotation = latestSession.overlayRotationDegrees
        binding.cameraOverlayImage.alpha = preview.opacity
    }

    private fun finishCamera() {
        captureTimerJob?.cancel()
        captureTimerJob = null
        activeRecording?.stop()
        activeRecording = null
        setResult(RESULT_CANCELED)
        finish()
    }

    private fun showCaptureError() {
        binding.root.post {
            captureInProgress = false
            showError()
        }
    }

    private fun showError(message: Int = R.string.generic_error) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        captureTimerJob?.cancel()
        activeRecording?.close()
        imageCaptureExecutor?.shutdown()
        imageLoader.close()
        binding.cameraPreview.controller = null
        cameraController?.unbind()
        cameraController = null
        super.onDestroy()
    }

    companion object {
        private const val COUNTDOWN_ANIMATION_MILLIS = 180L
        const val EXTRA_PICKED_URI = "camera.picked_uri"
        const val EXTRA_TRACE_URL = "camera.trace_url"
        const val EXTRA_TRACE_LOCAL_KEY = "camera.trace_local_key"
        const val EXTRA_REFERENCE_TITLE = "camera.reference_title"
        const val EXTRA_LESSON_ID = "camera.lesson_id"
        const val EXTRA_LESSON_STEP = "camera.lesson_step"
        const val EXTRA_LESSON_COUNT = "camera.lesson_count"
        const val EXTRA_LESSON_STEP_URLS = "camera.lesson_step_urls"
        const val EXTRA_LESSON_STEP_LOCAL_KEYS = "camera.lesson_step_local_keys"
        const val EXTRA_DRAWING_FINISHED = "camera.drawing_finished"

        fun intent(context: Context, session: DrawingSession) =
            Intent(context, CameraActivity::class.java).apply {
                putExtra(EXTRA_PICKED_URI, session.pickedImageUri)
                putExtra(EXTRA_TRACE_URL, session.traceImage?.url)
                putExtra(EXTRA_TRACE_LOCAL_KEY, session.traceImage?.localKey)
                putExtra(EXTRA_REFERENCE_TITLE, session.referenceTitle)
                putExtra(EXTRA_LESSON_ID, session.lessonId)
                putExtra(EXTRA_LESSON_STEP, session.lessonStepIndex)
                putExtra(EXTRA_LESSON_COUNT, session.lessonStepCount)
                putExtra(
                    EXTRA_LESSON_STEP_URLS,
                    session.lessonSteps.map { it.url.orEmpty() }.toTypedArray(),
                )
                putExtra(
                    EXTRA_LESSON_STEP_LOCAL_KEYS,
                    session.lessonSteps.map { it.localKey.orEmpty() }.toTypedArray(),
                )
            }
    }
}

private fun DrawingSession.toOverlayPreview() = OverlayPreview(
    overlayOffsetX,
    overlayOffsetY,
    zoom,
    opacity,
)

private fun Bitmap.rotate(degrees: Int): Bitmap =
    if (degrees % 360 == 0) this else Bitmap.createBitmap(
        this,
        0,
        0,
        width,
        height,
        Matrix().apply { postRotate(degrees.toFloat()) },
        true,
    )

private fun Canvas.drawCenterCrop(bitmap: Bitmap, width: Int, height: Int) {
    val targetRatio = width.toFloat() / height
    val sourceRatio = bitmap.width.toFloat() / bitmap.height
    val source = if (sourceRatio > targetRatio) {
        val sourceWidth = (bitmap.height * targetRatio).toInt()
        Rect((bitmap.width - sourceWidth) / 2, 0, (bitmap.width + sourceWidth) / 2, bitmap.height)
    } else {
        val sourceHeight = (bitmap.width / targetRatio).toInt()
        Rect(
            0,
            (bitmap.height - sourceHeight) / 2,
            bitmap.width,
            (bitmap.height + sourceHeight) / 2
        )
    }
    drawBitmap(
        bitmap,
        source,
        Rect(0, 0, width, height),
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
    )
}

private fun Bitmap.cropToRatio(targetRatio: Float?): Bitmap {
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

private val DrawingCameraRatio.outputRatio: Float?
    get() = when (this) {
        DrawingCameraRatio.FULL -> null
        DrawingCameraRatio.RATIO_16_9 -> 16f / 9f
        DrawingCameraRatio.RATIO_4_3 -> 4f / 3f
        DrawingCameraRatio.SQUARE -> 1f
    }

private val REMOVE_LIGHT_BACKGROUND_FILTER = ColorMatrixColorFilter(
    floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        -1f, -1f, -1f, 3f, 0f,
    ),
)
