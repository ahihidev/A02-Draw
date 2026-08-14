package com.a02.draw.onboarding.projector

import com.a02.draw.core.ui.base.UiEffect
import com.a02.draw.core.ui.base.UiState

data object ProjectorUiState : UiState

sealed interface ProjectorAction {
    data object Continue : ProjectorAction
}

sealed interface ProjectorEffect : UiEffect {
    data object NavigateNext : ProjectorEffect
}
