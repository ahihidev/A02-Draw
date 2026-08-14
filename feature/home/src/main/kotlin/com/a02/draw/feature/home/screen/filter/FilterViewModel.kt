package com.a02.draw.feature.home.screen.filter

import androidx.lifecycle.viewModelScope
import com.a02.draw.core.ui.base.BaseViewModel
import com.a02.draw.feature.home.common.session.GalleryFilterSessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FilterViewModel @Inject constructor(
    private val filters: GalleryFilterSessionStore,
) : BaseViewModel<FilterUiState, FilterEffect>(
    FilterUiState(filters.state.value.difficulty, filters.state.value.style),
) {
    fun onAction(action: FilterAction) {
        when (action) {
            is FilterAction.ToggleDifficulty -> updateState {
                copy(difficulty = if (difficulty == action.value) null else action.value)
            }

            is FilterAction.ToggleStyle -> updateState {
                copy(style = if (style == action.value) null else action.value)
            }

            FilterAction.Reset -> updateState { FilterUiState() }
            FilterAction.Apply -> {
                filters.update {
                    copy(
                        difficulty = state.value.difficulty,
                        style = state.value.style
                    )
                }
                close()
            }

            FilterAction.Back -> close()
        }
    }

    private fun close() {
        viewModelScope.launch { sendEffect(FilterEffect.Close) }
    }
}
