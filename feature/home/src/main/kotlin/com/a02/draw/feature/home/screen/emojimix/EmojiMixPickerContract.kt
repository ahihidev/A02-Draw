package com.a02.draw.feature.home.screen.emojimix

import com.a02.draw.core.ui.base.UiEffect
import com.a02.draw.core.ui.base.UiState

data class EmojiMixPickerUiState(
    val mode: EmojiMixMode = EmojiMixMode.MIX_2,
    val selected: List<String> = emptyList(),
    val options: List<EmojiOption> = EmojiKitchenCatalog.options,
) : UiState {
    val canCreate: Boolean get() = selected.size == mode.slotCount

    fun canSelect(emoji: String): Boolean = when {
        emoji in selected -> true
        selected.size >= mode.slotCount -> false
        mode != EmojiMixMode.MIX_2 || selected.size != 1 -> true
        else -> EmojiKitchenCatalog.find(selected.first(), emoji) != null
    }
}

sealed interface EmojiMixPickerAction {
    data class Toggle(val emoji: String) : EmojiMixPickerAction
    data object Clear : EmojiMixPickerAction
    data object Create : EmojiMixPickerAction
    data object Back : EmojiMixPickerAction
}

sealed interface EmojiMixPickerEffect : UiEffect {
    data object NavigateBack : EmojiMixPickerEffect
    data class OpenResult(val mode: EmojiMixMode, val inputs: List<String>) : EmojiMixPickerEffect
}
