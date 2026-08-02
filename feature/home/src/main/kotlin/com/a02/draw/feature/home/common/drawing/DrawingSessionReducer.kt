package com.a02.draw.feature.home.common.drawing

import com.a02.draw.feature.home.common.model.DrawingCameraPanel
import com.a02.draw.feature.home.common.model.DrawingCanvasPanel
import com.a02.draw.feature.home.common.model.DrawingTool
import com.a02.draw.feature.home.common.session.DrawingSessionStore
import com.a02.draw.feature.home.common.session.atLessonStep

enum class DrawingNavigation { NONE, BACK, CAPTURE, CANVAS, OPACITY }

fun DrawingSessionStore.reduce(action: DrawingControlAction): DrawingNavigation {
    when (action) {
        DrawingControlAction.Back -> return DrawingNavigation.BACK
        DrawingControlAction.Complete, DrawingControlAction.Shutter -> return DrawingNavigation.CAPTURE
        DrawingControlAction.PreviousStep -> update {
            atLessonStep(lessonStepIndex - 1)
        }

        DrawingControlAction.NextStep -> {
            val session = state.value
            if (session.lessonStepCount > 0 &&
                session.lessonStepIndex >= session.lessonStepCount - 1
            ) {
                return DrawingNavigation.CAPTURE
            }
            update { atLessonStep(lessonStepIndex + 1) }
        }

        is DrawingControlAction.SelectTool -> {
            update { copy(tool = action.tool) }
            return when (action.tool) {
                DrawingTool.CANVAS -> DrawingNavigation.CANVAS
                DrawingTool.OPACITY -> DrawingNavigation.OPACITY
                DrawingTool.CAMERA -> DrawingNavigation.NONE
            }
        }

        DrawingControlAction.ToggleOverlay -> update { copy(overlayVisible = !overlayVisible) }
        DrawingControlAction.ToggleLock -> update { copy(overlayLocked = !overlayLocked) }
        DrawingControlAction.ToggleFlip -> update { copy(overlayFlipped = !overlayFlipped) }
        DrawingControlAction.RotateOverlay -> update {
            copy(overlayRotationDegrees = (overlayRotationDegrees + 90f) % 360f)
        }

        DrawingControlAction.CenterOverlay -> update {
            copy(overlayOffsetX = 0f, overlayOffsetY = 0f)
        }

        DrawingControlAction.ResetOverlayTransform -> update {
            copy(
                overlayOffsetX = 0f,
                overlayOffsetY = 0f,
                zoom = 1f,
                overlayRotationDegrees = 0f,
                overlayFlipped = false,
            )
        }

        DrawingControlAction.ToggleRemoveBackground -> update {
            copy(removeImageEnabled = !removeImageEnabled)
        }

        is DrawingControlAction.OpenCanvasPanel -> update {
            copy(canvasPanel = if (canvasPanel == action.panel) DrawingCanvasPanel.NONE else action.panel)
        }

        is DrawingControlAction.SelectCropRatio -> update { copy(cropRatio = action.ratio) }
        is DrawingControlAction.SelectGridSize -> update {
            copy(gridSize = if (gridSize == action.size) 0 else action.size.coerceIn(3, 5))
        }

        is DrawingControlAction.OpenCameraPanel -> update {
            copy(cameraPanel = if (cameraPanel == action.panel) DrawingCameraPanel.NONE else action.panel)
        }

        is DrawingControlAction.SelectCameraZoom -> update { copy(cameraZoom = action.zoom) }
        is DrawingControlAction.SelectCameraRatio -> update { copy(cameraRatio = action.ratio) }
        is DrawingControlAction.SelectCaptureDelay -> update {
            copy(captureDelaySeconds = action.seconds.takeIf { it in setOf(3, 10) } ?: 0)
        }

        DrawingControlAction.ToggleCameraLens -> update {
            copy(useFrontCamera = !useFrontCamera, cameraZoom = 1f, flashEnabled = false)
        }

        DrawingControlAction.ToggleCameraGuide -> update {
            copy(cameraGuideEnabled = !cameraGuideEnabled)
        }

        is DrawingControlAction.ChangeOpacity -> update {
            copy(opacity = action.opacity.coerceIn(0.1f, 1f))
        }
    }
    return DrawingNavigation.NONE
}
