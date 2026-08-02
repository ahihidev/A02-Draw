package com.a02.draw.feature.home.screen.drawingcomplete

import com.a02.draw.core.ui.base.UiEffect
import com.a02.draw.core.ui.base.UiState
import com.a02.draw.feature.home.common.model.DrawingMode

data class DrawingCompleteUiState(
    val capturedUri: String? = null,
    val mode: DrawingMode = DrawingMode.CAMERA,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
) : UiState

sealed interface DrawingCompleteAction {
    data object Back : DrawingCompleteAction
    data object Home : DrawingCompleteAction
    data object Share : DrawingCompleteAction
    data object Retake : DrawingCompleteAction
}

sealed interface DrawingCompleteEffect : UiEffect {
    data object NavigateBack : DrawingCompleteEffect
    data object NavigateHome : DrawingCompleteEffect
    data class Share(val uri: String) : DrawingCompleteEffect
    data class Retake(val mode: DrawingMode) : DrawingCompleteEffect
    data object ShowSaveError : DrawingCompleteEffect
}
