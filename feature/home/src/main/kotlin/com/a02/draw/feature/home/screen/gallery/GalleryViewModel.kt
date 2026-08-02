package com.a02.draw.feature.home.screen.gallery

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.a02.draw.core.common.result.AppResult
import com.a02.draw.core.ui.base.BaseViewModel
import com.a02.draw.domain.model.ArCatalog
import com.a02.draw.domain.usecase.GetArCatalogUseCase
import com.a02.draw.domain.usecase.ObserveAppPreferencesUseCase
import com.a02.draw.domain.usecase.UpdateAppPreferencesUseCase
import com.a02.draw.feature.home.common.model.GalleryFilter
import com.a02.draw.feature.home.common.session.DrawingSession
import com.a02.draw.feature.home.common.session.DrawingSessionStore
import com.a02.draw.feature.home.common.session.GalleryFilterSession
import com.a02.draw.feature.home.common.session.GalleryFilterSessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GalleryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getCatalog: GetArCatalogUseCase,
    private val observePreferences: ObserveAppPreferencesUseCase,
    private val updatePreferences: UpdateAppPreferencesUseCase,
    private val filters: GalleryFilterSessionStore,
    private val drawingSession: DrawingSessionStore,
) : BaseViewModel<GalleryUiState, GalleryEffect>(GalleryUiState()) {
    private var catalog: ArCatalog? = null
    private var favoriteIds: Set<String> = emptySet()
    private var loadJob: Job? = null

    init {
        val topicId = savedStateHandle.get<String>("topicId")
        filters.update { GalleryFilterSession(topicId = topicId) }
        load()
        viewModelScope.launch { filters.state.collect { render(it) } }
        viewModelScope.launch {
            observePreferences().collect {
                favoriteIds = it.favoriteArtworkIds
                render(filters.state.value)
            }
        }
    }

    fun onAction(action: GalleryAction) {
        when (action) {
            GalleryAction.Back -> send(GalleryEffect.NavigateBack)
            GalleryAction.OpenFilter -> send(GalleryEffect.NavigateFilter)
            is GalleryAction.SelectQuickFilter -> filters.update {
                copy(
                    quickFilter = if (quickFilter == action.filter && action.filter != GalleryFilter.ALL) {
                        GalleryFilter.ALL
                    } else action.filter
                )
            }

            is GalleryAction.OpenArtwork -> openArtwork(action.artworkId)
            is GalleryAction.ToggleFavorite -> toggleFavorite(action.artworkId)
            GalleryAction.Retry -> load(forceRefresh = true)
        }
    }

    private fun load(forceRefresh: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            updateState { copy(isLoading = true, hasError = false) }
            when (val result = getCatalog(forceRefresh)) {
                is AppResult.Success -> {
                    catalog = result.data
                    render(filters.state.value)
                }

                is AppResult.Failure -> updateState { copy(isLoading = false, hasError = true) }
            }
        }
    }

    private fun render(filter: GalleryFilterSession) {
        val content = catalog ?: return
        val topicTitle = content.topics.firstOrNull { it.id == filter.topicId }?.title.orEmpty()
        val tags = content.artworks.asSequence()
            .filter { filter.topicId == null || it.topicId == filter.topicId }
            .flatMap { it.tags.asSequence() }
            .toSet()
        val quickFilters = buildList {
            add(GalleryFilter.SAVED)
            add(GalleryFilter.ALL)
            if ("jujutsu-kaisen" in tags) add(GalleryFilter.JUJUTSU_KAISEN)
            if ("one-piece" in tags) add(GalleryFilter.ONE_PIECE)
            if ("doraemon" in tags) add(GalleryFilter.DORAEMON)
        }
        val visible = content.artworks.filter { artwork ->
            val topicMatches = filter.topicId == null || artwork.topicId == filter.topicId
            val quickMatches = when (filter.quickFilter) {
                GalleryFilter.SAVED -> artwork.id in favoriteIds
                GalleryFilter.ALL -> true
                GalleryFilter.EASY -> artwork.difficulty.equals("Easy", true)
                GalleryFilter.JUJUTSU_KAISEN -> "jujutsu-kaisen" in artwork.tags
                GalleryFilter.ONE_PIECE -> "one-piece" in artwork.tags
                GalleryFilter.DORAEMON -> "doraemon" in artwork.tags
            }
            val difficultyMatches = filter.difficulty == null ||
                    artwork.difficulty.equals(filter.difficulty, true)
            val styleMatches = filter.style == null || artwork.style == filter.style
            topicMatches && quickMatches && difficultyMatches && styleMatches
        }
        updateState {
            copy(
                title = topicTitle,
                artworks = visible,
                favoriteIds = this@GalleryViewModel.favoriteIds,
                quickFilters = quickFilters,
                selectedQuickFilter = filter.quickFilter,
                isLoading = false,
                hasError = false,
            )
        }
    }

    private fun openArtwork(id: String) {
        val artwork = catalog?.artworks?.firstOrNull { it.id == id } ?: return
        drawingSession.reset(
            DrawingSession(
                artworkId = artwork.id,
                referenceTitle = artwork.title,
                referenceImage = artwork.image,
                traceImage = artwork.traceImage ?: artwork.image,
            ),
        )
        send(GalleryEffect.NavigateTutorial)
    }

    private fun toggleFavorite(id: String) {
        val previous = favoriteIds
        val next = favoriteIds.toMutableSet().apply { if (!add(id)) remove(id) }
        favoriteIds = next
        render(filters.state.value)
        viewModelScope.launch {
            if (updatePreferences.setFavorites(next) is AppResult.Failure && favoriteIds == next) {
                favoriteIds = previous
                render(filters.state.value)
                sendEffect(GalleryEffect.ShowError)
            }
        }
    }

    private fun send(effect: GalleryEffect) {
        viewModelScope.launch { sendEffect(effect) }
    }
}
