package com.a02.draw.feature.home.screen.camera

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.a02.draw.core.common.result.AppResult
import com.a02.draw.core.ui.base.BaseViewModel
import com.a02.draw.domain.model.ContentImage
import com.a02.draw.domain.usecase.UpdateAppPreferencesUseCase
import com.a02.draw.feature.home.common.drawing.DrawingControlAction
import com.a02.draw.feature.home.common.model.DrawingCameraPanel
import com.a02.draw.feature.home.common.model.DrawingCanvasPanel
import com.a02.draw.feature.home.common.model.DrawingMode
import com.a02.draw.feature.home.common.model.DrawingTool
import com.a02.draw.feature.home.common.session.DrawingSession
import com.a02.draw.feature.home.common.session.atLessonStep
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CameraViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val updatePreferences: UpdateAppPreferencesUseCase,
) : BaseViewModel<CameraUiState, CameraEffect>(
    CameraUiState(savedStateHandle.toSession()),
) {
    fun onAction(action: CameraAction) {
        when (action) {
            is CameraAction.Transform -> updateState {
                copy(
                    session = session.copy(
                        overlayOffsetX = action.value.offsetX,
                        overlayOffsetY = action.value.offsetY,
                        zoom = action.value.zoom,
                    ),
                )
            }

            CameraAction.RecordingFinished -> updateState {
                copy(session = session.copy(isRecording = false))
            }

            CameraAction.CameraLensRejected -> mutate {
                copy(useFrontCamera = !useFrontCamera)
            }

            is CameraAction.Control -> handleControl(action.action)
        }
    }

    private fun handleControl(action: DrawingControlAction) {
        when (action) {
            DrawingControlAction.Back -> send(CameraEffect.Finish)
            DrawingControlAction.Complete -> send(CameraEffect.FinishDrawing)
            DrawingControlAction.Shutter -> {
                if (state.value.session.cameraPanel == DrawingCameraPanel.RECORD) toggleRecording()
                else send(CameraEffect.Capture(state.value.session.captureDelaySeconds))
            }

            DrawingControlAction.PreviousStep -> mutate {
                atLessonStep(lessonStepIndex - 1)
            }

            DrawingControlAction.NextStep -> {
                val session = state.value.session
                if (session.lessonStepCount > 0 &&
                    session.lessonStepIndex >= session.lessonStepCount - 1
                ) {
                    saveLessonProgress(session.lessonStepCount)
                    send(CameraEffect.FinishDrawing)
                } else {
                    saveLessonProgress(session.lessonStepIndex + 1)
                    mutate { atLessonStep(lessonStepIndex + 1) }
                }
            }

            is DrawingControlAction.SelectTool -> mutate { copy(tool = action.tool) }
            DrawingControlAction.ToggleOverlay -> mutate { copy(overlayVisible = !overlayVisible) }
            DrawingControlAction.ToggleLock -> mutate { copy(overlayLocked = !overlayLocked) }
            DrawingControlAction.ToggleFlip -> mutate { copy(overlayFlipped = !overlayFlipped) }
            DrawingControlAction.RotateOverlay -> mutate {
                copy(overlayRotationDegrees = (overlayRotationDegrees + 90f) % 360f)
            }

            DrawingControlAction.CenterOverlay -> mutate {
                copy(overlayOffsetX = 0f, overlayOffsetY = 0f)
            }

            DrawingControlAction.ResetOverlayTransform -> mutate {
                copy(
                    overlayOffsetX = 0f,
                    overlayOffsetY = 0f,
                    zoom = 1f,
                    overlayRotationDegrees = 0f,
                    overlayFlipped = false,
                )
            }

            DrawingControlAction.ToggleRemoveBackground -> mutate {
                copy(removeImageEnabled = !removeImageEnabled)
            }

            is DrawingControlAction.OpenCanvasPanel -> mutate {
                copy(canvasPanel = if (canvasPanel == action.panel) DrawingCanvasPanel.NONE else action.panel)
            }

            is DrawingControlAction.SelectCropRatio -> mutate { copy(cropRatio = action.ratio) }
            is DrawingControlAction.SelectGridSize -> mutate {
                copy(gridSize = if (gridSize == action.size) 0 else action.size)
            }

            is DrawingControlAction.OpenCameraPanel -> {
                if (action.panel == DrawingCameraPanel.FLASH) toggleFlash() else mutate {
                    copy(cameraPanel = if (cameraPanel == action.panel) DrawingCameraPanel.NONE else action.panel)
                }
            }

            is DrawingControlAction.SelectCameraZoom -> {
                mutate { copy(cameraZoom = action.zoom, cameraPanel = DrawingCameraPanel.ZOOM) }
                send(CameraEffect.SetZoom(action.zoom))
            }

            is DrawingControlAction.SelectCameraRatio -> mutate { copy(cameraRatio = action.ratio) }
            is DrawingControlAction.SelectCaptureDelay -> mutate {
                copy(captureDelaySeconds = action.seconds.takeIf { it in setOf(3, 10) } ?: 0)
            }

            DrawingControlAction.ToggleCameraLens -> toggleCameraLens()
            DrawingControlAction.ToggleCameraGuide -> mutate {
                copy(cameraGuideEnabled = !cameraGuideEnabled)
            }

            is DrawingControlAction.ChangeOpacity -> mutate {
                copy(opacity = action.opacity.coerceIn(0.1f, 1f))
            }
        }
    }

    private fun toggleFlash() {
        val enabled = !state.value.session.flashEnabled
        mutate { copy(flashEnabled = enabled, cameraPanel = DrawingCameraPanel.FLASH) }
        send(CameraEffect.SetTorch(enabled))
    }

    private fun toggleRecording() {
        val enabled = !state.value.session.isRecording
        mutate { copy(isRecording = enabled, cameraPanel = DrawingCameraPanel.RECORD) }
        send(CameraEffect.SetRecording(enabled))
    }

    private fun toggleCameraLens() {
        val useFrontCamera = !state.value.session.useFrontCamera
        mutate {
            copy(
                useFrontCamera = useFrontCamera,
                cameraZoom = 1f,
                flashEnabled = false,
            )
        }
        send(CameraEffect.SetCameraLens(useFrontCamera))
    }

    private fun mutate(reducer: DrawingSession.() -> DrawingSession) {
        updateState { copy(session = session.reducer()) }
    }

    private fun send(effect: CameraEffect) {
        viewModelScope.launch { sendEffect(effect) }
    }

    private fun saveLessonProgress(completedSteps: Int) {
        val lessonId = state.value.session.lessonId ?: return
        viewModelScope.launch {
            if (updatePreferences.setLessonCompletedSteps(
                    lessonId,
                    completedSteps
                ) is AppResult.Failure
            ) {
                sendEffect(CameraEffect.ShowProgressError)
            }
        }
    }
}

private fun SavedStateHandle.toSession(): DrawingSession {
    val urls = get<Array<String>>(CameraActivity.EXTRA_LESSON_STEP_URLS).orEmpty()
    val localKeys = get<Array<String>>(CameraActivity.EXTRA_LESSON_STEP_LOCAL_KEYS).orEmpty()
    val lessonSteps = List(maxOf(urls.size, localKeys.size)) { index ->
        ContentImage(
            url = urls.getOrNull(index)?.ifBlank { null },
            localKey = localKeys.getOrNull(index)?.ifBlank { null },
        )
    }
    val stepIndex = get<Int>(CameraActivity.EXTRA_LESSON_STEP) ?: 0
    val fallbackTrace = ContentImage(
        url = get<String>(CameraActivity.EXTRA_TRACE_URL),
        localKey = get<String>(CameraActivity.EXTRA_TRACE_LOCAL_KEY),
    ).takeIf { it.url != null || it.localKey != null }
    return DrawingSession(
        lessonId = get<String>(CameraActivity.EXTRA_LESSON_ID),
        referenceTitle = get<String>(CameraActivity.EXTRA_REFERENCE_TITLE),
        pickedImageUri = get<String>(CameraActivity.EXTRA_PICKED_URI),
        traceImage = lessonSteps.getOrNull(stepIndex) ?: fallbackTrace,
        lessonSteps = lessonSteps,
        lessonStepIndex = stepIndex,
        lessonStepCount = get<Int>(CameraActivity.EXTRA_LESSON_COUNT) ?: lessonSteps.size,
        mode = DrawingMode.CAMERA,
        tool = DrawingTool.CAMERA,
    )
}
