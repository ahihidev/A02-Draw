package com.a02.draw.feature.home.screen.search

import com.a02.draw.core.ui.base.UiEffect
import com.a02.draw.core.ui.base.UiState
import com.a02.draw.domain.model.TrendingSearch

data class SearchUiState(
    val query: String = "",
    val trending: List<TrendingSearch> = emptyList(),
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
) : UiState

sealed interface SearchAction {
    data class QueryChanged(val value: String) : SearchAction
    data object Submit : SearchAction
    data class SelectTrending(val query: String) : SearchAction
    data object Back : SearchAction
    data object Retry : SearchAction
}

sealed interface SearchEffect : UiEffect {
    data class OpenResults(val query: String) : SearchEffect
    data object NavigateBack : SearchEffect
}
