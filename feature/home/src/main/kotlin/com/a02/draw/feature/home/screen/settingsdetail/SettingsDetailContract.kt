package com.a02.draw.feature.home.screen.settingsdetail

import androidx.annotation.StringRes
import com.a02.draw.core.ui.base.UiEffect
import com.a02.draw.core.ui.base.UiState

data class SettingsDetailUiState(
    @param:StringRes val title: Int,
    @param:StringRes val heading: Int,
    @param:StringRes val body: Int,
    @param:StringRes val bodyAppendix: Int? = null,
) : UiState

sealed interface SettingsDetailAction {
    data object Back : SettingsDetailAction
}

sealed interface SettingsDetailEffect : UiEffect {
    data object NavigateBack : SettingsDetailEffect
}
