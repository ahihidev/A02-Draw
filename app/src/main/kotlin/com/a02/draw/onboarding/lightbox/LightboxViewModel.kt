package com.a02.draw.onboarding.lightbox

import androidx.lifecycle.viewModelScope
import com.a02.draw.core.ui.base.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class LightboxViewModel @Inject constructor() :
    BaseViewModel<LightboxUiState, LightboxEffect>(LightboxUiState) {
    fun onAction(action: LightboxAction) {
        when (action) {
            LightboxAction.Continue -> viewModelScope.launch {
                sendEffect(LightboxEffect.NavigateNext)
            }
        }
    }
}
