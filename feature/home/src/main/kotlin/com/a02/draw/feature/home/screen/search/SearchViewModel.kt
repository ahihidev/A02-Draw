package com.a02.draw.feature.home.screen.search

import androidx.lifecycle.viewModelScope
import com.a02.draw.core.common.result.AppResult
import com.a02.draw.core.ui.base.BaseViewModel
import com.a02.draw.domain.usecase.GetArCatalogUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val getCatalog: GetArCatalogUseCase,
) : BaseViewModel<SearchUiState, SearchEffect>(SearchUiState()) {
    private var loadJob: Job? = null

    init {
        load()
    }

    fun onAction(action: SearchAction) {
        when (action) {
            is SearchAction.QueryChanged -> updateState { copy(query = action.value) }
            SearchAction.Submit -> openResults(state.value.query)
            is SearchAction.SelectTrending -> {
                updateState { copy(query = action.query) }
                openResults(action.query)
            }

            SearchAction.Back -> send(SearchEffect.NavigateBack)
            SearchAction.Retry -> load(forceRefresh = true)
        }
    }

    private fun load(forceRefresh: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            updateState { copy(isLoading = true, hasError = false) }
            when (val result = getCatalog(forceRefresh)) {
                is AppResult.Success -> updateState {
                    copy(trending = result.data.trendingSearches.take(4), isLoading = false)
                }

                is AppResult.Failure -> updateState { copy(isLoading = false, hasError = true) }
            }
        }
    }

    private fun openResults(query: String) {
        send(SearchEffect.OpenResults(query.trim()))
    }

    private fun send(effect: SearchEffect) {
        viewModelScope.launch { sendEffect(effect) }
    }
}
