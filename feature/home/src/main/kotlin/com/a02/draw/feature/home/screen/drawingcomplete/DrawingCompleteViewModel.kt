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
    ),
) {
    private val saveMutex = Mutex()
    private var saveStarted = false

    init {
        saveOnce()
    }

    fun onAction(action: DrawingCompleteAction) {
        when (action) {
            DrawingCompleteAction.Back -> send(DrawingCompleteEffect.NavigateBack)
            DrawingCompleteAction.Home -> send(DrawingCompleteEffect.NavigateHome)
            DrawingCompleteAction.Share -> state.value.capturedUri?.let {
                send(DrawingCompleteEffect.Share(it))
            }

            DrawingCompleteAction.Retake -> {
                drawingSession.update { copy(capturedImageUri = null) }
                send(DrawingCompleteEffect.Retake(state.value.mode))
            }
        }
    }

    private fun saveOnce() {
        if (saveStarted) return
        saveStarted = true
        val session = drawingSession.state.value
        val uri = session.capturedImageUri ?: return
        viewModelScope.launch {
            saveMutex.withLock {
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
