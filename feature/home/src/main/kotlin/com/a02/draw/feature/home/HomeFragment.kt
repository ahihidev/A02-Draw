package com.a02.draw.feature.home

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.video.AudioConfig
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.getSystemService
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.a02.draw.core.ui.base.BaseFragment
import com.a02.draw.feature.home.databinding.FragmentHomeBinding
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt

@AndroidEntryPoint
class HomeFragment : BaseFragment<FragmentHomeBinding>(FragmentHomeBinding::inflate) {
    private val viewModel: HomeViewModel by viewModels()
    private var cameraController: LifecycleCameraController? = null
    private var activeRecording: Recording? = null
    private var latestState = HomeUiState()
    private var renderingSearchText = false
    private var lastRenderedScreen: ArDrawScreen? = null
    private var imageCaptureExecutor: ExecutorService? = null
    private var overlayImageKey: String? = null
    private var overlayImageLoadJob: Job? = null

    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.onAction(ArDrawAction.CameraPermissionResult(granted)) }

    private val photoPicker = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        uri?.let {
            runCatching {
                requireContext().contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.onAction(ArDrawAction.MediaPicked(it.toString()))
        }
    }

    override fun setupViews(savedInstanceState: Bundle?) {
        binding.arDrawView.onAction = viewModel::onAction
        binding.arDrawView.onOverlayPreview = ::renderOverlayPreview
        binding.cameraOverlayImage.clipToOutline = true
        // SurfaceView keeps camera frames off the UI render pipeline. The captured camera frame
        // and overlay are composited explicitly, so smooth preview does not trade away export quality.
        binding.cameraPreview.implementationMode =
            androidx.camera.view.PreviewView.ImplementationMode.PERFORMANCE
        binding.cameraPreview.scaleType = androidx.camera.view.PreviewView.ScaleType.FILL_CENTER
        binding.arDrawView.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                updateSearchInputBounds()
                if (binding.cameraOverlayContainer.isVisible) {
                    renderOverlayPreview(latestState.toOverlayPreview())
                }
            }
        }
        viewModel.onAction(
            ArDrawAction.SyncCameraPermission(
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED,
            ),
        )
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (latestState.handlesBackInApp) {
                        viewModel.onAction(ArDrawAction.Back)
                    } else {
                        isEnabled = false
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                    }
                }
            },
        )
    }

    override fun setupListeners() {
        binding.searchInput.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    value: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) = Unit

                override fun onTextChanged(
                    value: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {
                    if (!renderingSearchText) {
                        viewModel.onAction(
                            ArDrawAction.SearchQueryChanged(
                                value?.toString().orEmpty()
                            )
                        )
                    }
                }

                override fun afterTextChanged(value: Editable?) = Unit
            },
        )
        binding.searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                viewModel.onAction(ArDrawAction.SubmitSearch)
                hideKeyboard()
                true
            } else {
                false
            }
        }
    }

    override fun observeData() {
        collectWhenStarted {
            launch {
                viewModel.state.collect { state ->
                    latestState = state
                    binding.arDrawView.render(state)
                    renderPlatformViews(state)
                }
            }
            launch {
                viewModel.effects.collect(::handleEffect)
            }
        }
    }

    private fun renderPlatformViews(state: HomeUiState) {
        val cameraVisible = state.drawingWithCamera &&
                state.cameraPermissionGranted &&
                state.screen in DRAWING_SCREENS
        binding.cameraPreview.visibility = if (cameraVisible) View.VISIBLE else View.GONE
        binding.arDrawView.setCameraOverlayExternal(cameraVisible)
        renderCameraOverlay(state, cameraVisible)
        if (cameraVisible) ensureCameraController() else releaseCameraController()

        val searchVisible =
            state.screen == ArDrawScreen.SEARCH || state.screen == ArDrawScreen.SEARCH_RESULTS
        val leftSearch = !searchVisible && lastRenderedScreen in setOf(
            ArDrawScreen.SEARCH,
            ArDrawScreen.SEARCH_RESULTS,
        )
        val submittedSearch = state.screen == ArDrawScreen.SEARCH_RESULTS &&
                lastRenderedScreen == ArDrawScreen.SEARCH
        if (leftSearch || submittedSearch) {
            hideKeyboard()
            binding.searchInput.clearFocus()
        }
        binding.searchInput.visibility = if (searchVisible) View.VISIBLE else View.GONE
        updateSearchInputBounds()
        if (binding.searchInput.text.toString() != state.searchQuery) {
            renderingSearchText = true
            binding.searchInput.setText(state.searchQuery)
            binding.searchInput.setSelection(state.searchQuery.length)
            renderingSearchText = false
        }
        lastRenderedScreen = state.screen
    }

    private fun renderCameraOverlay(state: HomeUiState, cameraVisible: Boolean) {
        val visible = cameraVisible && state.overlayVisible
        binding.cameraOverlayContainer.visibility = if (visible) View.VISIBLE else View.GONE
        if (!visible) return
        renderOverlayPreview(
            OverlayPreview(
                offsetX = state.overlayOffsetX,
                offsetY = state.overlayOffsetY,
                zoom = state.zoom,
                opacity = state.opacity,
            ),
        )
        binding.cameraOverlayImage.colorFilter = if (state.removeImageEnabled) {
            REMOVE_LIGHT_BACKGROUND_FILTER
        } else {
            null
        }
        val image = state.selectedArtwork?.traceImage ?: state.selectedArtwork?.image
        val key = state.pickedImageUri ?: image?.url ?: image?.localKey ?: "drawing_trace_overlay"
        if (key == overlayImageKey) return
        overlayImageKey = key
        overlayImageLoadJob?.cancel()
        val localResource = image?.localKey?.let(::localAssetDrawable)
            ?: if (key == "drawing_trace_overlay") R.drawable.drawing_trace_overlay else null
        if (state.pickedImageUri == null && localResource != null) {
            binding.cameraOverlayImage.setImageResource(localResource)
            return
        }
        overlayImageLoadJob = viewLifecycleOwner.lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching {
                    if (state.pickedImageUri != null) {
                        requireContext().contentResolver.openInputStream(key.toUri())
                            ?.use(BitmapFactory::decodeStream)
                    } else {
                        URL(key).openStream().use(BitmapFactory::decodeStream)
                    }
                }.getOrNull()
            }
            if (overlayImageKey == key && bitmap != null) binding.cameraOverlayImage.setImageBitmap(
                bitmap
            )
        }
    }

    private fun renderOverlayPreview(preview: OverlayPreview) {
        if (binding.cameraOverlayContainer.visibility != View.VISIBLE) return
        val rootWidth = binding.root.width
        val rootHeight = binding.root.height
        if (rootWidth <= 0 || rootHeight <= 0) {
            binding.root.post { renderOverlayPreview(preview) }
            return
        }
        val (logicalWidth, logicalHeight) = when (latestState.cropRatio) {
            DrawingCropRatio.PORTRAIT -> 184f to 328f
            DrawingCropRatio.LANDSCAPE -> 328f to 184f
            DrawingCropRatio.RESET, DrawingCropRatio.SQUARE -> 328f to 328f
        }
        val transform = binding.arDrawView.layoutTransform()
        val overlayWidth = (logicalWidth * transform.scale).toInt().coerceAtLeast(1)
        val overlayHeight = (logicalHeight * transform.scale).toInt().coerceAtLeast(1)
        binding.cameraOverlayContainer.layoutParams =
            FrameLayout.LayoutParams(overlayWidth, overlayHeight)
        binding.cameraOverlayContainer.x =
            transform.offsetX + 180f * transform.scale - overlayWidth / 2f +
                    preview.offsetX * transform.scale
        binding.cameraOverlayContainer.y =
            transform.offsetY + 358f * transform.scale - overlayHeight / 2f +
                    preview.offsetY * transform.scale
        binding.cameraOverlayContainer.scaleX =
            if (latestState.overlayFlipped) -preview.zoom else preview.zoom
        binding.cameraOverlayContainer.scaleY = preview.zoom
        binding.cameraOverlayImage.alpha = preview.opacity
    }

    private fun HomeUiState.toOverlayPreview() = OverlayPreview(
        offsetX = overlayOffsetX,
        offsetY = overlayOffsetY,
        zoom = zoom,
        opacity = opacity,
    )

    private fun updateSearchInputBounds() {
        if (binding.arDrawView.width <= 0 || binding.arDrawView.height <= 0) return
        val transform = binding.arDrawView.layoutTransform()
        binding.searchInput.updateLayoutParams<FrameLayout.LayoutParams> {
            width = (328f * transform.scale).roundToInt()
            height = (44f * transform.scale).roundToInt()
            leftMargin = (transform.offsetX + 16f * transform.scale).roundToInt()
            topMargin = (transform.offsetY + 96f * transform.scale).roundToInt()
            rightMargin = 0
        }
        binding.searchInput.setPadding(
            (48f * transform.scale).roundToInt(),
            0,
            (12f * transform.scale).roundToInt(),
            0,
        )
        binding.searchInput.setTextSize(TypedValue.COMPLEX_UNIT_PX, 14f * transform.scale)
    }

    private fun localAssetDrawable(key: String): Int = when (key) {
        "topic_chibi" -> R.drawable.topic_chibi
        "topic_pixel" -> R.drawable.topic_pixel
        "topic_anime" -> R.drawable.topic_anime
        "topic_cartoon" -> R.drawable.topic_cartoon
        "topic_world_cup" -> R.drawable.topic_world_cup
        "topic_bricks" -> R.drawable.topic_bricks
        "topic_animal" -> R.drawable.topic_animal
        "topic_flower" -> R.drawable.topic_flower
        "topic_kids" -> R.drawable.topic_kids
        "drawing_trace_overlay" -> R.drawable.drawing_trace_overlay
        else -> R.drawable.topic_chibi
    }

    private fun handleEffect(effect: HomeEffect) {
        when (effect) {
            is HomeEffect.ShowMessage -> Snackbar.make(
                binding.root,
                effect.messageRes,
                Snackbar.LENGTH_SHORT
            ).show()

            HomeEffect.OpenPhotoPicker -> photoPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )

            HomeEffect.RequestCameraPermission -> cameraPermission.launch(Manifest.permission.CAMERA)
            HomeEffect.CapturePhoto -> captureCompositePhoto()
            HomeEffect.CaptureCanvas -> captureCanvasDrawing()
            is HomeEffect.SetTorch -> {
                ensureCameraController()
                cameraController?.enableTorch(effect.enabled)
            }

            is HomeEffect.SetCameraZoom -> {
                ensureCameraController()
                val zoomState = cameraController?.zoomState?.value
                val boundedZoom = if (zoomState == null) effect.zoom else {
                    effect.zoom.coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
                }
                cameraController?.setZoomRatio(boundedZoom)
            }

            is HomeEffect.SetRecording -> setRecording(effect.enabled)
            is HomeEffect.Share -> share(effect.uri)
            is HomeEffect.OpenExternal -> openExternal(effect.target)
            HomeEffect.OpenStoreListing -> openStoreListing()
            HomeEffect.OpenSubscriptionManager -> openSubscriptionManager()
            HomeEffect.FocusSearch -> focusSearch()
        }
    }

    private fun ensureCameraController() {
        if (cameraController != null) return
        cameraController = LifecycleCameraController(requireContext()).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(CameraController.IMAGE_CAPTURE or CameraController.VIDEO_CAPTURE)
            bindToLifecycle(viewLifecycleOwner)
            binding.cameraPreview.controller = this
        }
    }

    private fun captureCompositePhoto() {
        ensureCameraController()
        val executor = imageCaptureExecutor ?: Executors.newSingleThreadExecutor().also {
            imageCaptureExecutor = it
        }
        cameraController?.takePicture(
            executor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val rotationDegrees = image.imageInfo.rotationDegrees
                    val cameraBitmap = runCatching { image.toBitmap() }.getOrNull()
                    image.close()
                    if (cameraBitmap == null) {
                        showCaptureError()
                        return
                    }
                    view?.post { composeCameraAndOverlay(cameraBitmap, rotationDegrees) }
                        ?: cameraBitmap.recycle()
                }

                override fun onError(exception: ImageCaptureException) = showCaptureError()
            },
        ) ?: showCaptureError()
    }

    private fun composeCameraAndOverlay(cameraBitmap: Bitmap, rotationDegrees: Int) {
        if (view == null) {
            cameraBitmap.recycle()
            return
        }
        val width = binding.root.width
        val height = binding.root.height
        if (width <= 0 || height <= 0) {
            cameraBitmap.recycle()
            return
        }
        binding.cameraOverlayContainer.visibility = View.INVISIBLE
        binding.arDrawView.prepareExport {
            val overlayBitmap = createBitmap(width, height)
            binding.arDrawView.draw(Canvas(overlayBitmap))
            binding.arDrawView.setExportOnly(false)
            renderCameraOverlay(latestState, cameraVisible = true)
            val lifecycle = viewLifecycleOwner.lifecycleScope
            lifecycle.launch(Dispatchers.Default) {
                val orientedCamera = rotateBitmap(cameraBitmap, rotationDegrees)
                val composite = createBitmap(width, height)
                val canvas = Canvas(composite)
                drawCenterCrop(canvas, orientedCamera, width, height)
                canvas.drawBitmap(
                    overlayBitmap,
                    0f,
                    0f,
                    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                )
                if (orientedCamera !== cameraBitmap) orientedCamera.recycle()
                cameraBitmap.recycle()
                overlayBitmap.recycle()
                withContext(Dispatchers.Main) { saveCompositeBitmap(composite) }
            }
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return bitmap
        return Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            Matrix().apply { postRotate(degrees.toFloat()) },
            true,
        )
    }

    private fun drawCenterCrop(canvas: Canvas, bitmap: Bitmap, width: Int, height: Int) {
        val targetRatio = width.toFloat() / height
        val sourceRatio = bitmap.width.toFloat() / bitmap.height
        val source = if (sourceRatio > targetRatio) {
            val sourceWidth = (bitmap.height * targetRatio).toInt()
            Rect(
                (bitmap.width - sourceWidth) / 2,
                0,
                (bitmap.width + sourceWidth) / 2,
                bitmap.height
            )
        } else {
            val sourceHeight = (bitmap.width / targetRatio).toInt()
            Rect(
                0,
                (bitmap.height - sourceHeight) / 2,
                bitmap.width,
                (bitmap.height + sourceHeight) / 2
            )
        }
        canvas.drawBitmap(
            bitmap,
            source,
            Rect(0, 0, width, height),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
        )
    }

    private fun showCaptureError() {
        view?.post {
            view?.let {
                Snackbar.make(it, R.string.generic_error, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun captureCanvasDrawing() {
        val width = binding.arDrawView.width
        val height = binding.arDrawView.height
        if (width <= 0 || height <= 0) return
        binding.arDrawView.prepareExport {
            val bitmap = createBitmap(width, height)
            binding.arDrawView.draw(android.graphics.Canvas(bitmap))
            binding.arDrawView.setExportOnly(false)
            saveCompositeBitmap(bitmap, DrawingCameraRatio.FULL)
        }
    }

    private fun saveCompositeBitmap(
        bitmap: Bitmap,
        ratio: DrawingCameraRatio = latestState.cameraRatio,
    ) {
        val file = newCaptureFile() ?: run {
            bitmap.recycle()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val saved = withContext(Dispatchers.IO) {
                val outputBitmap = cropCapturedBitmap(bitmap, ratio)
                if (outputBitmap !== bitmap) bitmap.recycle()
                val result = runCatching {
                    FileOutputStream(file).use {
                        outputBitmap.compress(
                            Bitmap.CompressFormat.JPEG,
                            94,
                            it
                        )
                    }
                }.getOrDefault(false)
                outputBitmap.recycle()
                result
            }
            if (saved) dispatchCapturedFile(file)
            else {
                file.delete()
                Snackbar.make(binding.root, R.string.generic_error, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun cropCapturedBitmap(bitmap: Bitmap, ratio: DrawingCameraRatio): Bitmap {
        val targetRatio = when (ratio) {
            DrawingCameraRatio.FULL -> return bitmap
            DrawingCameraRatio.RATIO_16_9 -> 16f / 9f
            DrawingCameraRatio.RATIO_4_3 -> 4f / 3f
            DrawingCameraRatio.SQUARE -> 1f
        }
        val sourceRatio = bitmap.width.toFloat() / bitmap.height
        return if (sourceRatio > targetRatio) {
            val width = (bitmap.height * targetRatio).toInt()
            Bitmap.createBitmap(bitmap, (bitmap.width - width) / 2, 0, width, bitmap.height)
        } else {
            val height = (bitmap.width / targetRatio).toInt()
            Bitmap.createBitmap(bitmap, 0, (bitmap.height - height) / 2, bitmap.width, height)
        }
    }

    private fun captureCameraPhoto() {
        ensureCameraController()
        val file = newCaptureFile() ?: return
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        cameraController?.takePicture(
            options,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                    dispatchCapturedFile(file)
                }

                override fun onError(exception: ImageCaptureException) {
                    Snackbar.make(binding.root, R.string.generic_error, Snackbar.LENGTH_SHORT)
                        .show()
                }
            },
        )
    }

    private fun setRecording(enabled: Boolean) {
        if (!enabled) {
            activeRecording?.stop()
            activeRecording = null
            return
        }
        ensureCameraController()
        val file = newVideoFile() ?: return
        val options = FileOutputOptions.Builder(file).build()
        activeRecording = cameraController?.startRecording(
            options,
            AudioConfig.AUDIO_DISABLED,
            ContextCompat.getMainExecutor(requireContext()),
        ) { event ->
            if (event is VideoRecordEvent.Finalize) {
                activeRecording = null
                viewModel.onAction(ArDrawAction.RecordingFinished)
                if (event.hasError()) {
                    file.delete()
                    view?.let {
                        Snackbar.make(
                            it,
                            R.string.video_recording_error,
                            Snackbar.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    view?.let {
                        Snackbar.make(it, R.string.video_saved, Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
        }
        if (activeRecording == null) {
            viewModel.onAction(ArDrawAction.RecordingFinished)
            Snackbar.make(binding.root, R.string.video_recording_error, Snackbar.LENGTH_SHORT)
                .show()
        }
    }

    private fun newCaptureFile(): File? {
        val directory =
            requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: return null
        if (!directory.exists() && !directory.mkdirs()) return null
        return File(directory, "ar-drawing-${System.currentTimeMillis()}.jpg")
    }

    private fun newVideoFile(): File? {
        val directory =
            requireContext().getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: return null
        if (!directory.exists() && !directory.mkdirs()) return null
        return File(directory, "ar-drawing-${System.currentTimeMillis()}.mp4")
    }

    private fun dispatchCapturedFile(file: File) {
        val uri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.files",
            file,
        )
        viewModel.onAction(ArDrawAction.PhotoCaptured(uri.toString()))
    }

    private fun share(uriValue: String?) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (uriValue == null) "text/plain" else "image/jpeg"
            putExtra(Intent.EXTRA_TEXT, getString(R.string.share_message))
            uriValue?.let {
                putExtra(Intent.EXTRA_STREAM, it.toUri())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_drawing)))
    }

    private fun openExternal(target: String) {
        val uri = target.toUri()
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            if (uri.scheme == "market") {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        "https://play.google.com/store/apps/details?id=${requireContext().packageName}".toUri(),
                    ),
                )
            } else if (uri.scheme == "package") {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri))
            }
        }
    }

    private fun openStoreListing() {
        val packageName = requireContext().packageName.removeSuffix(".debug")
        openExternal("market://details?id=$packageName")
    }

    private fun openSubscriptionManager() {
        val packageName = requireContext().packageName.removeSuffix(".debug")
        openExternal("https://play.google.com/store/account/subscriptions?package=$packageName")
    }

    private fun focusSearch() {
        binding.searchInput.requestFocus()
        binding.searchInput.post {
            requireContext().getSystemService<InputMethodManager>()
                ?.showSoftInput(binding.searchInput, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun hideKeyboard() {
        requireContext().getSystemService<InputMethodManager>()
            ?.hideSoftInputFromWindow(binding.searchInput.windowToken, 0)
    }

    override fun onDestroyView() {
        overlayImageLoadJob?.cancel()
        overlayImageLoadJob = null
        overlayImageKey = null
        releaseCameraController()
        imageCaptureExecutor?.shutdownNow()
        imageCaptureExecutor = null
        super.onDestroyView()
    }

    private fun releaseCameraController() {
        activeRecording?.stop()
        activeRecording = null
        cameraController?.unbind()
        cameraController = null
    }

    private companion object {
        val REMOVE_LIGHT_BACKGROUND_FILTER = ColorMatrixColorFilter(
            floatArrayOf(
                0f, 0f, 0f, 0f, 22f,
                0f, 0f, 0f, 0f, 22f,
                0f, 0f, 0f, 0f, 22f,
                -0.333f, -0.333f, -0.333f, 0f, 255f,
            ),
        )
        val DRAWING_SCREENS = setOf(
            ArDrawScreen.DRAWING_CAMERA,
            ArDrawScreen.DRAWING_CANVAS,
            ArDrawScreen.DRAWING_OPACITY,
        )
    }
}
