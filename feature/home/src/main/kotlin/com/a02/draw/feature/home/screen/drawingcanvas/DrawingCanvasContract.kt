package com.a02.draw.feature.home.screen.drawingcanvas

import com.a02.draw.core.ui.base.UiEffect
import com.a02.draw.core.ui.base.UiState
import com.a02.draw.feature.home.common.drawing.DrawingControlAction
import com.a02.draw.feature.home.common.drawing.OverlayTransform
import com.a02.draw.feature.home.common.session.DrawingSession

data class DrawingCanvasUiState(val session: DrawingSession = DrawingSession()) : UiState

sealed interface DrawingCanvasAction {
    data class Control(val action: DrawingControlAction) : DrawingCanvasAction
    data class Transform(val value: OverlayTransform) : DrawingCanvasAction
}

sealed interface DrawingCanvasEffect : UiEffect {
    data object NavigateBack : DrawingCanvasEffect
    data object NavigateOpacity : DrawingCanvasEffect
    data object NavigateComplete : DrawingCanvasEffect
    data object ShowProgressError : DrawingCanvasEffect
}
