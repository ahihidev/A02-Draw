package com.a02.draw.feature.home.screen.learn

import androidx.lifecycle.viewModelScope
import com.a02.draw.core.common.result.AppResult
import com.a02.draw.core.ui.base.BaseViewModel
import com.a02.draw.domain.usecase.GetArCatalogUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@HiltViewModel
class LearnViewModel @Inject constructor(
    private val getCatalog: GetArCatalogUseCase,
) : BaseViewModel<LearnUiState, LearnEffect>(LearnUiState()) {
    private var loadJob: Job? = null

    init {
        load()
    }

    fun onAction(action: LearnAction) {
        when (action) {
            LearnAction.Retry -> load(forceRefresh = true)
            LearnAction.OpenSearch -> send(LearnEffect.NavigateSearch)
            is LearnAction.OpenCategory -> send(LearnEffect.NavigateCategory(action.categoryId))
            is LearnAction.OpenBottom -> send(LearnEffect.NavigateBottom(action.destination))
        }
    }

    private fun load(forceRefresh: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            updateState { copy(isLoading = true, hasError = false) }
            when (val result = getCatalog(forceRefresh)) {
                is AppResult.Success -> updateState {
                    val availableCategoryIds =
                        result.data.lessons.mapTo(hashSetOf()) { it.categoryId }
                    copy(
                        categories = result.data.categories.filter { it.id in availableCategoryIds },
                        isLoading = false,
                    )
                }

                is AppResult.Failure -> updateState { copy(isLoading = false, hasError = true) }
            }
        }
    }

    private fun send(effect: LearnEffect) {
        viewModelScope.launch { sendEffect(effect) }
    }
}
