package com.a02.draw.feature.home.screen.tutorialcamera

import com.a02.draw.core.ui.base.UiEffect
import com.a02.draw.core.ui.base.UiState
import com.a02.draw.feature.home.common.session.DrawingSession

data class TutorialCameraUiState(val session: DrawingSession = DrawingSession()) : UiState

sealed interface TutorialCameraAction {
    data object Back : TutorialCameraAction
    data object SelectScreenMode : TutorialCameraAction
    data object Start : TutorialCameraAction
    data class CameraCaptured(val uri: String) : TutorialCameraAction
}

sealed interface TutorialCameraEffect : UiEffect {
    data object NavigateBack : TutorialCameraEffect
    data object NavigateScreenMode : TutorialCameraEffect
    data object LaunchCamera : TutorialCameraEffect
    data object NavigateComplete : TutorialCameraEffect
}
