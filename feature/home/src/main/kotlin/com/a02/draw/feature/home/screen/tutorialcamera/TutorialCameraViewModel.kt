package com.a02.draw.feature.home.screen.tutorialcamera

import androidx.lifecycle.viewModelScope
import com.a02.draw.core.ui.base.BaseViewModel
import com.a02.draw.feature.home.common.model.DrawingMode
import com.a02.draw.feature.home.common.model.DrawingTool
import com.a02.draw.feature.home.common.session.DrawingSessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TutorialCameraViewModel @Inject constructor(
    private val drawingSession: DrawingSessionStore,
) : BaseViewModel<TutorialCameraUiState, TutorialCameraEffect>(
    TutorialCameraUiState(drawingSession.state.value),
) {
    init {
        viewModelScope.launch { drawingSession.state.collect { updateState { copy(session = it) } } }
    }

    fun onAction(action: TutorialCameraAction) {
        when (action) {
            TutorialCameraAction.Back -> send(TutorialCameraEffect.NavigateBack)
            TutorialCameraAction.SelectScreenMode -> {
                drawingSession.update { copy(mode = DrawingMode.SCREEN, tool = DrawingTool.CANVAS) }
                send(TutorialCameraEffect.NavigateScreenMode)
            }

            TutorialCameraAction.Start -> {
                drawingSession.update { copy(mode = DrawingMode.CAMERA, tool = DrawingTool.CAMERA) }
                send(TutorialCameraEffect.LaunchCamera)
            }

            is TutorialCameraAction.CameraCaptured -> {
                drawingSession.update { copy(capturedImageUri = action.uri, isRecording = false) }
                send(TutorialCameraEffect.NavigateComplete)
            }
        }
    }

    private fun send(effect: TutorialCameraEffect) {
        viewModelScope.launch { sendEffect(effect) }
    }
}
