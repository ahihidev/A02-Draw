package com.a02.draw.feature.home.screen.drawingopacity

import androidx.lifecycle.viewModelScope
import com.a02.draw.core.common.result.AppResult
import com.a02.draw.core.ui.base.BaseViewModel
import com.a02.draw.domain.usecase.UpdateAppPreferencesUseCase
import com.a02.draw.feature.home.common.drawing.DrawingControlAction
import com.a02.draw.feature.home.common.drawing.DrawingNavigation
import com.a02.draw.feature.home.common.drawing.reduce
import com.a02.draw.feature.home.common.model.DrawingTool
import com.a02.draw.feature.home.common.session.DrawingSessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DrawingOpacityViewModel @Inject constructor(
    private val sessionStore: DrawingSessionStore,
    private val updatePreferences: UpdateAppPreferencesUseCase,
) : BaseViewModel<DrawingOpacityUiState, DrawingOpacityEffect>(
    DrawingOpacityUiState(sessionStore.state.value),
) {
    init {
        sessionStore.update { copy(tool = DrawingTool.OPACITY) }
        viewModelScope.launch { sessionStore.state.collect { updateState { copy(session = it) } } }
    }

    fun onAction(action: DrawingOpacityAction) {
        when (action) {
            is DrawingOpacityAction.Control -> {
                if (action.action == DrawingControlAction.NextStep) saveLessonProgress()
                when (sessionStore.reduce(action.action)) {
                    DrawingNavigation.BACK -> send(DrawingOpacityEffect.NavigateBack)
                    DrawingNavigation.CANVAS -> send(DrawingOpacityEffect.NavigateCanvas)
                    DrawingNavigation.CAPTURE -> send(DrawingOpacityEffect.CaptureCanvas)
                    else -> Unit
                }
            }

            is DrawingOpacityAction.Transform -> sessionStore.update {
                copy(
                    overlayOffsetX = action.value.offsetX,
                    overlayOffsetY = action.value.offsetY,
                    zoom = action.value.zoom,
                )
            }

            is DrawingOpacityAction.Captured -> {
                sessionStore.update { copy(capturedImageUri = action.uri) }
                send(DrawingOpacityEffect.NavigateComplete)
            }
        }
    }

    private fun send(effect: DrawingOpacityEffect) {
        viewModelScope.launch { sendEffect(effect) }
    }

    private fun saveLessonProgress() {
        val session = sessionStore.state.value
        val lessonId = session.lessonId ?: return
        val completedSteps = if (session.lessonStepIndex >= session.lessonStepCount - 1) {
            session.lessonStepCount
        } else {
            session.lessonStepIndex + 1
        }
        viewModelScope.launch {
            if (updatePreferences.setLessonCompletedSteps(
                    lessonId,
                    completedSteps
                ) is AppResult.Failure
            ) {
                sendEffect(DrawingOpacityEffect.ShowProgressError)
            }
        }
    }
}
