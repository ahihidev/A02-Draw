package com.a02.draw.language

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.a02.draw.core.common.result.AppResult
import com.a02.draw.core.ui.base.BaseViewModel
import com.a02.draw.domain.usecase.ObserveAppPreferencesUseCase
import com.a02.draw.domain.usecase.UpdateAppPreferencesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LanguageViewModel @Inject constructor(
    private val updatePreferences: UpdateAppPreferencesUseCase,
    observePreferences: ObserveAppPreferencesUseCase,
    private val savedStateHandle: SavedStateHandle,
) : BaseViewModel<LanguageUiState, LanguageEffect>(LanguageUiState()) {
    private var continueInProgress = false

    init {
        viewModelScope.launch {
            val restoredTag = savedStateHandle.get<String>(KEY_SELECTED_LANGUAGE)
            val persistedTag = observePreferences().first().languageTag
            select(restoredTag ?: persistedTag ?: DEFAULT_LANGUAGE_TAG)
        }
    }

    fun onAction(action: LanguageAction) {
        when (action) {
            is LanguageAction.Select -> select(action.languageTag)
            LanguageAction.Continue -> saveAndContinue()
        }
    }

    private fun select(languageTag: String) {
        if (continueInProgress || SUPPORTED_LANGUAGES.none { it.languageTag == languageTag }) return
        savedStateHandle[KEY_SELECTED_LANGUAGE] = languageTag
        updateState {
            copy(
                selectedLanguageTag = languageTag,
                languages = SUPPORTED_LANGUAGES.map { option ->
                    LanguageItem(option, option.languageTag == languageTag)
                },
            )
        }
    }

    private fun saveAndContinue() {
        if (continueInProgress) return
        continueInProgress = true
        updateState { copy(isSaving = true) }
        val languageTag = state.value.selectedLanguageTag
        viewModelScope.launch {
            when (updatePreferences.setLanguageTag(languageTag)) {
                is AppResult.Success -> sendEffect(LanguageEffect.OpenIntro(languageTag))
                is AppResult.Failure -> {
                    continueInProgress = false
                    updateState { copy(isSaving = false) }
                    sendEffect(LanguageEffect.ShowSaveError)
                }
            }
        }
    }

    private companion object {
        const val KEY_SELECTED_LANGUAGE = "selected_language"
    }
}
