package com.a02.draw.feature.home.screen.emojimix

import com.a02.draw.core.ui.base.UiEffect
import com.a02.draw.core.ui.base.UiState

data class EmojiMixResultUiState(
    val mode: EmojiMixMode = EmojiMixMode.MIX_2,
    val inputs: List<String> = emptyList(),
    val isLoading: Boolean = true,
    val result: EmojiMixResult? = null,
    val errorMessage: String? = null,
) : UiState

sealed interface EmojiMixResultAction {
    data object Retry : EmojiMixResultAction
    data object Draw : EmojiMixResultAction
    data object CreateNew : EmojiMixResultAction
    data object Back : EmojiMixResultAction
    data class Save(val destinationUri: String) : EmojiMixResultAction
}

sealed interface EmojiMixResultEffect : UiEffect {
    data object NavigateBack : EmojiMixResultEffect
    data object NavigateToDrawingMode : EmojiMixResultEffect
    data class CreateNew(val mode: EmojiMixMode) : EmojiMixResultEffect
    data object Saved : EmojiMixResultEffect
    data class ShowError(val message: String) : EmojiMixResultEffect
}
