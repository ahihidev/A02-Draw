package com.a02.draw.feature.home

import androidx.annotation.StringRes
import com.a02.draw.core.ui.base.UiEffect
import com.a02.draw.core.ui.base.UiState
import com.a02.draw.domain.model.Drawing

data class HomeUiState(
    val isLoading: Boolean = true,
    val drawings: List<Drawing> = emptyList(),
    val hasError: Boolean = false,
) : UiState

sealed interface HomeEffect : UiEffect {
    data class ShowMessage(@param:StringRes val messageRes: Int) : HomeEffect
}
