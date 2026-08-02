package com.a02.draw.onboarding.lightbox

import com.a02.draw.core.ui.base.UiEffect
import com.a02.draw.core.ui.base.UiState

data object LightboxUiState : UiState

sealed interface LightboxAction {
    data object Continue : LightboxAction
}

sealed interface LightboxEffect : UiEffect {
    data object NavigateNext : LightboxEffect
}
