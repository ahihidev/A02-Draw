package com.a02.draw.feature.home.screen.drawingcomplete

import androidx.lifecycle.viewModelScope
import com.a02.draw.core.common.result.AppResult
import com.a02.draw.core.ui.base.BaseViewModel
import com.a02.draw.domain.model.Drawing
import com.a02.draw.domain.usecase.SaveDrawingUseCase
import com.a02.draw.feature.home.common.model.DrawingMode
import com.a02.draw.feature.home.common.session.DrawingSessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

@HiltViewModel
class DrawingCompleteViewModel @Inject constructor(
    private val drawingSession: DrawingSessionStore,
    private val saveDrawing: SaveDrawingUseCase,
) : BaseViewModel<DrawingCompleteUiState, DrawingCompleteEffect>(
    DrawingCompleteUiState(
        capturedUri = drawingSession.state.value.capturedImageUri,
        mode = drawingSession.state.value.mode,
        isSaved = drawingSession.state.value.capturedImageUri != null &&
                drawingSession.state.value.activeDrawingId != null,
    ),
) {
    private val saveMutex = Mutex()
    private var lastSavedUri: String? = null

    init {
        val session = drawingSession.state.value
        if (session.activeDrawingId == null) saveCapturedDrawing(session.capturedImageUri)
    }

    fun onAction(action: DrawingCompleteAction) {
        when (action) {
            DrawingCompleteAction.Back -> send(DrawingCompleteEffect.ContinueDrawing(state.value.mode))
            DrawingCompleteAction.Home -> send(DrawingCompleteEffect.NavigateHome)
            DrawingCompleteAction.TakePhoto,
            DrawingCompleteAction.RetakePhoto
                -> send(DrawingCompleteEffect.LaunchResultCamera)

            is DrawingCompleteAction.PhotoCaptured -> {
                drawingSession.update { copy(capturedImageUri = action.uri) }
                updateState { copy(capturedUri = action.uri, isSaved = false) }
                saveCapturedDrawing(action.uri)
            }

            DrawingCompleteAction.Share -> state.value.capturedUri?.let {
                send(DrawingCompleteEffect.Share(it))
            }

            DrawingCompleteAction.ContinueDrawing -> {
                drawingSession.update { copy(capturedImageUri = null) }
                send(DrawingCompleteEffect.ContinueDrawing(state.value.mode))
            }
        }
    }

    private fun saveCapturedDrawing(uri: String?) {
        if (uri == null || uri == lastSavedUri) return
        viewModelScope.launch {
            saveMutex.withLock {
                if (uri == lastSavedUri) return@withLock
                val session = drawingSession.state.value
                updateState { copy(isSaving = true) }
                val drawing = Drawing(
                    id = session.activeDrawingId ?: 0,
                    title = session.referenceTitle ?: "AR sketch",
                    updatedAtEpochMillis = System.currentTimeMillis(),
                    mediaUri = uri,
                    artworkId = session.artworkId.takeIf { session.lessonId == null },
                    lessonId = session.lessonId,
                    lessonMinutes = session.lessonMinutes,
                    usesCamera = session.mode == DrawingMode.CAMERA,
                )
                when (val result = saveDrawing(drawing)) {
                    is AppResult.Success -> {
                        lastSavedUri = uri
                        drawingSession.update { copy(activeDrawingId = result.data) }
                        updateState { copy(isSaving = false, isSaved = true) }
                    }

                    is AppResult.Failure -> {
                        updateState { copy(isSaving = false) }
                        sendEffect(DrawingCompleteEffect.ShowSaveError)
                    }
                }
            }
        }
    }

    private fun send(effect: DrawingCompleteEffect) {
        viewModelScope.launch { sendEffect(effect) }
    }
}
