package com.a02.draw.feature.home.screen.profilefavorite

import com.a02.draw.core.ui.base.UiEffect
import com.a02.draw.core.ui.base.UiState
import com.a02.draw.domain.model.Artwork
import com.a02.draw.feature.home.common.model.BottomDestination

data class ProfileFavoriteUiState(
    val favorites: List<Artwork> = emptyList(),
    val favoriteIds: Set<String> = emptySet(),
    val sketchCount: Int = 0,
    val lessonCount: Int = 0,
    val lessonMinutes: Int = 0,
) : UiState

sealed interface ProfileFavoriteAction {
    data class OpenArtwork(val artworkId: String) : ProfileFavoriteAction
    data class ToggleFavorite(val artworkId: String) : ProfileFavoriteAction
    data object OpenAlbum : ProfileFavoriteAction
    data class OpenBottom(val destination: BottomDestination) : ProfileFavoriteAction
}

sealed interface ProfileFavoriteEffect : UiEffect {
    data object NavigateTutorial : ProfileFavoriteEffect
    data object NavigateAlbum : ProfileFavoriteEffect
    data class NavigateBottom(val destination: BottomDestination) : ProfileFavoriteEffect
    data object ShowError : ProfileFavoriteEffect
}
