package com.a02.draw.onboarding.lessons

import androidx.lifecycle.viewModelScope
import com.a02.draw.core.ui.base.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class LessonsViewModel @Inject constructor() :
    BaseViewModel<LessonsUiState, LessonsEffect>(LessonsUiState) {
    fun onAction(action: LessonsAction) {
        when (action) {
            LessonsAction.Continue -> viewModelScope.launch {
                sendEffect(LessonsEffect.NavigateNext)
            }
        }
    }
}
