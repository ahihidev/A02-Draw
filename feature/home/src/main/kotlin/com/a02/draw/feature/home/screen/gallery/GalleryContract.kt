package com.a02.draw.feature.home.screen.gallery

import com.a02.draw.core.ui.base.UiEffect
import com.a02.draw.core.ui.base.UiState
import com.a02.draw.domain.model.Artwork
import com.a02.draw.feature.home.common.model.GalleryFilter

data class GalleryUiState(
    val title: String = "",
    val artworks: List<Artwork> = emptyList(),
    val favoriteIds: Set<String> = emptySet(),
    val quickFilters: List<GalleryFilter> = listOf(GalleryFilter.SAVED, GalleryFilter.ALL),
    val selectedQuickFilter: GalleryFilter = GalleryFilter.ALL,
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
) : UiState

sealed interface GalleryAction {
    data object Back : GalleryAction
    data object OpenFilter : GalleryAction
    data class SelectQuickFilter(val filter: GalleryFilter) : GalleryAction
    data class OpenArtwork(val artworkId: String) : GalleryAction
    data class ToggleFavorite(val artworkId: String) : GalleryAction
    data object Retry : GalleryAction
}

sealed interface GalleryEffect : UiEffect {
    data object NavigateBack : GalleryEffect
    data object NavigateFilter : GalleryEffect
    data object NavigateTutorial : GalleryEffect
    data object ShowError : GalleryEffect
}
