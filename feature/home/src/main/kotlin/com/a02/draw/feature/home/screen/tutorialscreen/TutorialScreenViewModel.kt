package com.a02.draw.feature.home.screen.tutorialscreen

import androidx.lifecycle.viewModelScope
import com.a02.draw.core.ui.base.BaseViewModel
import com.a02.draw.feature.home.common.model.DrawingMode
import com.a02.draw.feature.home.common.model.DrawingTool
import com.a02.draw.feature.home.common.session.DrawingSessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TutorialScreenViewModel @Inject constructor(
    private val drawingSession: DrawingSessionStore,
) : BaseViewModel<TutorialScreenUiState, TutorialScreenEffect>(
    TutorialScreenUiState(drawingSession.state.value),
) {
    init {
        drawingSession.update { copy(mode = DrawingMode.SCREEN, tool = DrawingTool.CANVAS) }
        viewModelScope.launch { drawingSession.state.collect { updateState { copy(session = it) } } }
    }

    fun onAction(action: TutorialScreenAction) {
        when (action) {
            TutorialScreenAction.Back -> send(TutorialScreenEffect.NavigateBack)
            TutorialScreenAction.SelectCameraMode -> {
                drawingSession.update { copy(mode = DrawingMode.CAMERA, tool = DrawingTool.CAMERA) }
                send(TutorialScreenEffect.NavigateCameraMode)
            }

            TutorialScreenAction.Start -> send(TutorialScreenEffect.NavigateCanvas)
        }
    }

    private fun send(effect: TutorialScreenEffect) {
        viewModelScope.launch { sendEffect(effect) }
    }
}
