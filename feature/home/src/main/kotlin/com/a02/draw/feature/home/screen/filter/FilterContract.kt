package com.a02.draw.feature.home.screen.filter

import com.a02.draw.core.ui.base.UiEffect
import com.a02.draw.core.ui.base.UiState
import com.a02.draw.domain.model.ArtworkStyle

data class FilterUiState(
    val difficulty: String? = null,
    val style: ArtworkStyle? = null,
) : UiState

sealed interface FilterAction {
    data class ToggleDifficulty(val value: String) : FilterAction
    data class ToggleStyle(val value: ArtworkStyle) : FilterAction
    data object Reset : FilterAction
    data object Apply : FilterAction
    data object Back : FilterAction
}

sealed interface FilterEffect : UiEffect {
    data object Close : FilterEffect
}
