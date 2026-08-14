package com.a02.draw.feature.home.screen.drawingopacity

import com.a02.draw.core.ui.base.UiEffect
import com.a02.draw.core.ui.base.UiState
import com.a02.draw.feature.home.common.drawing.DrawingControlAction
import com.a02.draw.feature.home.common.drawing.OverlayTransform
import com.a02.draw.feature.home.common.session.DrawingSession

data class DrawingOpacityUiState(val session: DrawingSession = DrawingSession()) : UiState

sealed interface DrawingOpacityAction {
    data class Control(val action: DrawingControlAction) : DrawingOpacityAction
    data class Transform(val value: OverlayTransform) : DrawingOpacityAction
}

sealed interface DrawingOpacityEffect : UiEffect {
    data object NavigateBack : DrawingOpacityEffect
    data object NavigateCanvas : DrawingOpacityEffect
    data object NavigateComplete : DrawingOpacityEffect
    data object ShowProgressError : DrawingOpacityEffect
}
