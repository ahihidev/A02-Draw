package com.a02.draw.onboarding.lessons

import com.a02.draw.core.ui.base.UiEffect
import com.a02.draw.core.ui.base.UiState

data object LessonsUiState : UiState

sealed interface LessonsAction {
    data object Continue : LessonsAction
}

sealed interface LessonsEffect : UiEffect {
    data object NavigateNext : LessonsEffect
}
