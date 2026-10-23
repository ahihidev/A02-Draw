package com.a02.draw.language

import androidx.annotation.StringRes
import com.a02.draw.R
import com.a02.draw.core.ui.base.UiEffect
import com.a02.draw.core.ui.base.UiState

data class LanguageOption(
    val languageTag: String,
    val flag: String,
    @param:StringRes val nameRes: Int,
)

data class LanguageItem(
    val option: LanguageOption,
    val isSelected: Boolean,
)

data class LanguageUiState(
    val languages: List<LanguageItem> = emptyList(),
    val selectedLanguageTag: String = DEFAULT_LANGUAGE_TAG,
    val isSaving: Boolean = false,
) : UiState

sealed interface LanguageAction {
    data class Select(val languageTag: String) : LanguageAction
    data object Continue : LanguageAction
}

sealed interface LanguageEffect : UiEffect {
    data class OpenIntro(val languageTag: String) : LanguageEffect
    data object ShowSaveError : LanguageEffect
}

internal const val DEFAULT_LANGUAGE_TAG = "en"

internal val SUPPORTED_LANGUAGES = listOf(
    LanguageOption("de", "🇩🇪", R.string.language_german),
    LanguageOption("fr", "🇫🇷", R.string.language_french),
    LanguageOption("es", "🇪🇸", R.string.language_spanish),
    LanguageOption("it", "🇮🇹", R.string.language_italian),
    LanguageOption("pt-BR", "🇧🇷", R.string.language_portuguese_brazil),
    LanguageOption("nl", "🇳🇱", R.string.language_dutch),
    LanguageOption("sv", "🇸🇪", R.string.language_swedish),
    LanguageOption("nb", "🇳🇴", R.string.language_norwegian),
    LanguageOption("da", "🇩🇰", R.string.language_danish),
    LanguageOption("fi", "🇫🇮", R.string.language_finnish),
    LanguageOption("ja", "🇯🇵", R.string.language_japanese),
    LanguageOption("ko", "🇰🇷", R.string.language_korean),
    LanguageOption("zh-Hant", "🇹🇼", R.string.language_chinese_traditional),
    LanguageOption("pl", "🇵🇱", R.string.language_polish),
    // English deliberately stays below the initial viewport while the sticky ad is visible.
    LanguageOption("en", "🇺🇸", R.string.language_english),
)
