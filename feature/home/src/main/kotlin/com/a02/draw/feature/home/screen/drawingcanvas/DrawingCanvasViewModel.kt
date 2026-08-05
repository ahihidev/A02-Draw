package com.a02.draw.feature.home.screen.drawingcanvas

import androidx.lifecycle.viewModelScope
import com.a02.draw.core.common.result.AppResult
import com.a02.draw.core.ui.base.BaseViewModel
import com.a02.draw.domain.usecase.UpdateAppPreferencesUseCase
import com.a02.draw.feature.home.common.drawing.DrawingNavigation
import com.a02.draw.feature.home.common.drawing.reduce
import com.a02.draw.feature.home.common.model.DrawingMode
import com.a02.draw.feature.home.common.model.DrawingTool
import com.a02.draw.feature.home.common.session.DrawingSessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DrawingCanvasViewModel @Inject constructor(
    private val sessionStore: DrawingSessionStore,
    private val updatePreferences: UpdateAppPreferencesUseCase,
) : BaseViewModel<DrawingCanvasUiState, DrawingCanvasEffect>(
    DrawingCanvasUiState(sessionStore.state.value),
) {
    init {
        sessionStore.update { copy(mode = DrawingMode.SCREEN, tool = DrawingTool.CANVAS) }
        viewModelScope.launch { sessionStore.state.collect { updateState { copy(session = it) } } }
    }

    fun onAction(action: DrawingCanvasAction) {
        when (action) {
            is DrawingCanvasAction.Control -> {
                if (action.action == com.a02.draw.feature.home.common.drawing.DrawingControlAction.NextStep) {
                    saveLessonProgress()
                }
                when (sessionStore.reduce(action.action)) {
                    DrawingNavigation.BACK -> send(DrawingCanvasEffect.NavigateBack)
                    DrawingNavigation.CAPTURE -> {
                        sessionStore.update { copy(capturedImageUri = null) }
                        send(DrawingCanvasEffect.NavigateComplete)
                    }
                    DrawingNavigation.OPACITY -> send(DrawingCanvasEffect.NavigateOpacity)
                    else -> Unit
                }
            }

            is DrawingCanvasAction.Transform -> sessionStore.update {
                copy(
                    overlayOffsetX = action.value.offsetX,
                    overlayOffsetY = action.value.offsetY,
                    zoom = action.value.zoom,
                )
            }

        }
    }

    private fun send(effect: DrawingCanvasEffect) {
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
                sendEffect(DrawingCanvasEffect.ShowProgressError)
            }
        }
    }
}
