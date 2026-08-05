package com.a02.draw.feature.home.screen.camera

import com.a02.draw.core.ui.base.UiEffect
import com.a02.draw.core.ui.base.UiState
import com.a02.draw.feature.home.common.drawing.DrawingControlAction
import com.a02.draw.feature.home.common.drawing.OverlayTransform
import com.a02.draw.feature.home.common.session.DrawingSession

data class CameraUiState(val session: DrawingSession = DrawingSession()) : UiState

sealed interface CameraAction {
    data class Control(val action: DrawingControlAction) : CameraAction
    data class Transform(val value: OverlayTransform) : CameraAction
    data object RecordingFinished : CameraAction
    data object CameraLensRejected : CameraAction
}

sealed interface CameraEffect : UiEffect {
    data object Finish : CameraEffect
    data object FinishDrawing : CameraEffect
    data class Capture(val delaySeconds: Int = 0) : CameraEffect
    data class SetTorch(val enabled: Boolean) : CameraEffect
    data class SetZoom(val zoom: Float) : CameraEffect
    data class SetCameraLens(val useFrontCamera: Boolean) : CameraEffect
    data class SetRecording(val enabled: Boolean) : CameraEffect
    data object ShowProgressError : CameraEffect
}
