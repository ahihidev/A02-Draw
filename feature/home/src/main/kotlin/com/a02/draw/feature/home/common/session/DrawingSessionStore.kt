package com.a02.draw.feature.home.common.session

import com.a02.draw.domain.model.ContentImage
import com.a02.draw.feature.home.common.model.DrawingCameraPanel
import com.a02.draw.feature.home.common.model.DrawingCameraRatio
import com.a02.draw.feature.home.common.model.DrawingCanvasPanel
import com.a02.draw.feature.home.common.model.DrawingCropRatio
import com.a02.draw.feature.home.common.model.DrawingMode
import com.a02.draw.feature.home.common.model.DrawingTool
import kotlinx.coroutines.flow.StateFlow

data class DrawingSession(
    val artworkId: String? = null,
    val lessonId: String? = null,
    val categoryId: String? = null,
    val referenceTitle: String? = null,
    val referenceImage: ContentImage? = null,
    val traceImage: ContentImage? = null,
    val lessonSteps: List<ContentImage> = emptyList(),
    val pickedImageUri: String? = null,
    val capturedImageUri: String? = null,
    val replacedMediaUri: String? = null,
    val activeDrawingId: Long? = null,
    val lessonMinutes: Int? = null,
    val lessonStepIndex: Int = 0,
    val lessonStepCount: Int = 0,
    val mode: DrawingMode = DrawingMode.CAMERA,
    val tool: DrawingTool = DrawingTool.CAMERA,
    val opacity: Float = 0.4f,
    val zoom: Float = 1f,
    val overlayOffsetX: Float = 0f,
    val overlayOffsetY: Float = 0f,
    val overlayRotationDegrees: Float = 0f,
    val cameraZoom: Float = 1f,
    val captureDelaySeconds: Int = 0,
    val useFrontCamera: Boolean = false,
    val cameraGuideEnabled: Boolean = false,
    val flashEnabled: Boolean = false,
    val overlayLocked: Boolean = false,
    val overlayFlipped: Boolean = false,
    val removeImageEnabled: Boolean = false,
    val overlayVisible: Boolean = true,
    val canvasPanel: DrawingCanvasPanel = DrawingCanvasPanel.NONE,
    val cropRatio: DrawingCropRatio = DrawingCropRatio.RESET,
    val gridSize: Int = 0,
    val cameraPanel: DrawingCameraPanel = DrawingCameraPanel.NONE,
    val cameraRatio: DrawingCameraRatio = DrawingCameraRatio.FULL,
    val isRecording: Boolean = false,
)

interface DrawingSessionStore {
    val state: StateFlow<DrawingSession>
    fun update(reducer: DrawingSession.() -> DrawingSession)
    fun reset(reference: DrawingSession = DrawingSession())
}

fun DrawingSession.atLessonStep(index: Int): DrawingSession {
    val lastIndex = (lessonStepCount - 1).coerceAtLeast(0)
    val boundedIndex = index.coerceIn(0, lastIndex)
    return copy(
        lessonStepIndex = boundedIndex,
        traceImage = lessonSteps.getOrNull(boundedIndex) ?: traceImage,
    )
}
