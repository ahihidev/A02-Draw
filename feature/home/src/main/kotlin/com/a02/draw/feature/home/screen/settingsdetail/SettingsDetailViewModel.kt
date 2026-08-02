package com.a02.draw.feature.home.screen.settingsdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.a02.draw.core.ui.base.BaseViewModel
import com.a02.draw.feature.home.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
) : BaseViewModel<SettingsDetailUiState, SettingsDetailEffect>(
    savedStateHandle.get<String>("settingId").toDetailState(),
) {
    fun onAction(action: SettingsDetailAction) {
        if (action == SettingsDetailAction.Back) {
            viewModelScope.launch { sendEffect(SettingsDetailEffect.NavigateBack) }
        }
    }
}

private fun String?.toDetailState() = when (this) {
    "help" -> SettingsDetailUiState(
        R.string.help_faqs,
        R.string.how_ar_drawing_works,
        R.string.help_body
    )

    "privacy" -> SettingsDetailUiState(
        R.string.privacy_policy,
        R.string.your_privacy,
        R.string.privacy_body
    )

    "terms" -> SettingsDetailUiState(
        R.string.terms_of_use,
        R.string.using_ar_drawing,
        R.string.terms_body
    )

    else -> SettingsDetailUiState(
        R.string.settings_title,
        R.string.settings_title,
        R.string.settings_title
    )
}
