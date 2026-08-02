package com.a02.draw.feature.home.screen.settings

import com.a02.draw.core.ui.base.UiEffect
import com.a02.draw.core.ui.base.UiState
import com.a02.draw.domain.model.AppSettingItem
import com.a02.draw.feature.home.common.model.BottomDestination

data class SettingsUiState(
    val items: List<AppSettingItem> = emptyList(),
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
) : UiState

sealed interface SettingsAction {
    data object Retry : SettingsAction
    data class OpenItem(val settingId: String) : SettingsAction
    data class OpenBottom(val destination: BottomDestination) : SettingsAction
}

sealed interface SettingsEffect : UiEffect {
    data class NavigateDetail(val settingId: String) : SettingsEffect
    data class OpenExternal(val target: String) : SettingsEffect
    data object OpenStore : SettingsEffect
    data object ShareApp : SettingsEffect
    data class NavigateBottom(val destination: BottomDestination) : SettingsEffect
}
