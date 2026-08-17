package com.a02.draw.feature.home.screen.emojimix

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.a02.draw.core.ui.base.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EmojiMixPickerViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
) : BaseViewModel<EmojiMixPickerUiState, EmojiMixPickerEffect>(
    EmojiMixPickerUiState(
        mode = savedStateHandle.get<String>(EmojiMixHomeFragment.ARG_MODE)
            ?.let { runCatching { EmojiMixMode.valueOf(it) }.getOrNull() }
            ?: EmojiMixMode.MIX_2,
        selected = savedStateHandle.get<ArrayList<String>>(KEY_SELECTED).orEmpty(),
    ),
) {
    fun onAction(action: EmojiMixPickerAction) {
        when (action) {
            is EmojiMixPickerAction.Toggle -> toggle(action.emoji)
            EmojiMixPickerAction.Clear -> setSelected(emptyList())
            EmojiMixPickerAction.Create -> if (state.value.canCreate) {
                send(EmojiMixPickerEffect.OpenResult(state.value.mode, state.value.selected))
            }

            EmojiMixPickerAction.Back -> send(EmojiMixPickerEffect.NavigateBack)
        }
    }

    private fun toggle(emoji: String) {
        val currentState = state.value
        val current = currentState.selected
        if (!currentState.canSelect(emoji)) return
        val next = when {
            emoji in current -> current - emoji
            current.size >= currentState.mode.slotCount -> current
            else -> current + emoji
        }
        setSelected(next)
    }

    private fun setSelected(selected: List<String>) {
        savedStateHandle[KEY_SELECTED] = ArrayList(selected)
        updateState { copy(selected = selected) }
    }

    private fun send(effect: EmojiMixPickerEffect) {
        viewModelScope.launch { sendEffect(effect) }
    }

    private companion object {
        const val KEY_SELECTED = "emoji_picker.selected"
    }
}
