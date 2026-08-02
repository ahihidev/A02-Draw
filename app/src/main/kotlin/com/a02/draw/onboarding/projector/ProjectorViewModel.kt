package com.a02.draw.onboarding.projector

import androidx.lifecycle.viewModelScope
import com.a02.draw.core.ui.base.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProjectorViewModel @Inject constructor() :
    BaseViewModel<ProjectorUiState, ProjectorEffect>(ProjectorUiState) {
    fun onAction(action: ProjectorAction) {
        when (action) {
            ProjectorAction.Continue -> viewModelScope.launch {
                sendEffect(ProjectorEffect.NavigateNext)
            }
        }
    }
}
