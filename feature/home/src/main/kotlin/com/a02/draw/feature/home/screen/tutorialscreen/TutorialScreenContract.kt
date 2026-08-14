package com.a02.draw.feature.home.screen.tutorialscreen

import com.a02.draw.core.ui.base.UiEffect
import com.a02.draw.core.ui.base.UiState
import com.a02.draw.feature.home.common.session.DrawingSession

data class TutorialScreenUiState(val session: DrawingSession = DrawingSession()) : UiState

sealed interface TutorialScreenAction {
    data object Back : TutorialScreenAction
    data object SelectCameraMode : TutorialScreenAction
    data object Start : TutorialScreenAction
}

sealed interface TutorialScreenEffect : UiEffect {
    data object NavigateBack : TutorialScreenEffect
    data object NavigateCameraMode : TutorialScreenEffect
    data object NavigateCanvas : TutorialScreenEffect
}
