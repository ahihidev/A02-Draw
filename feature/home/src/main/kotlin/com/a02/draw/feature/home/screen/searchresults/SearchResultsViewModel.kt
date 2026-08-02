package com.a02.draw.feature.home.screen.searchresults

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.a02.draw.core.common.result.AppResult
import com.a02.draw.core.ui.base.BaseViewModel
import com.a02.draw.domain.usecase.GetArCatalogUseCase
import com.a02.draw.feature.home.common.session.DrawingSession
import com.a02.draw.feature.home.common.session.DrawingSessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchResultsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getCatalog: GetArCatalogUseCase,
    private val drawingSessionStore: DrawingSessionStore,
) : BaseViewModel<SearchResultsUiState, SearchResultsEffect>(
    SearchResultsUiState(query = savedStateHandle.get<String>("query").orEmpty()),
) {
    private var allArtwork = emptyList<com.a02.draw.domain.model.Artwork>()
    private var loadJob: Job? = null

    init {
        load()
    }

    fun onAction(action: SearchResultsAction) {
        when (action) {
            is SearchResultsAction.QueryChanged -> updateResults(action.value)
            SearchResultsAction.Submit -> updateResults(state.value.query)
            is SearchResultsAction.OpenArtwork -> openArtwork(action.artworkId)
            SearchResultsAction.Back -> send(SearchResultsEffect.NavigateBack)
            SearchResultsAction.Retry -> load(forceRefresh = true)
        }
    }

    private fun load(forceRefresh: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            updateState { copy(isLoading = true, hasError = false) }
            when (val result = getCatalog(forceRefresh)) {
                is AppResult.Success -> {
                    allArtwork = result.data.artworks
                    updateResults(state.value.query)
                    updateState { copy(isLoading = false) }
                }

                is AppResult.Failure -> updateState { copy(isLoading = false, hasError = true) }
            }
        }
    }

    private fun updateResults(query: String) {
        val normalized = query.trim()
        updateState {
            copy(
                query = query,
                results = allArtwork.filter { artwork ->
                    normalized.isBlank() || artwork.title.contains(normalized, true) ||
                            artwork.tags.any { it.contains(normalized, true) }
                },
            )
        }
    }

    private fun openArtwork(id: String) {
        val artwork = allArtwork.firstOrNull { it.id == id } ?: return
        drawingSessionStore.reset(
            DrawingSession(
                artworkId = artwork.id,
                referenceTitle = artwork.title,
                referenceImage = artwork.image,
                traceImage = artwork.traceImage ?: artwork.image,
            ),
        )
        send(SearchResultsEffect.OpenTutorial(id))
    }

    private fun send(effect: SearchResultsEffect) {
        viewModelScope.launch { sendEffect(effect) }
    }
}
