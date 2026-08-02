package com.a02.draw.feature.home.screen.searchresults

import com.a02.draw.core.ui.base.UiEffect
import com.a02.draw.core.ui.base.UiState
import com.a02.draw.domain.model.Artwork

data class SearchResultsUiState(
    val query: String = "",
    val results: List<Artwork> = emptyList(),
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
) : UiState

sealed interface SearchResultsAction {
    data class QueryChanged(val value: String) : SearchResultsAction
    data object Submit : SearchResultsAction
    data class OpenArtwork(val artworkId: String) : SearchResultsAction
    data object Back : SearchResultsAction
    data object Retry : SearchResultsAction
}

sealed interface SearchResultsEffect : UiEffect {
    data class OpenTutorial(val artworkId: String) : SearchResultsEffect
    data object NavigateBack : SearchResultsEffect
}
