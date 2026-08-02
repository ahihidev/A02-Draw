package com.a02.draw.onboarding.preparing

import androidx.annotation.StringRes
import com.a02.draw.core.ui.base.UiEffect
import com.a02.draw.core.ui.base.UiState
import com.a02.draw.domain.model.DrawingTopic

data class PreparingUiState(val selectedTopic: DrawingTopic? = null) : UiState

sealed interface PreparingAction {
    data object Start : PreparingAction
}

sealed interface PreparingEffect : UiEffect {
    data object OpenMain : PreparingEffect
    data class ShowMessage(@param:StringRes val messageRes: Int) : PreparingEffect
}
