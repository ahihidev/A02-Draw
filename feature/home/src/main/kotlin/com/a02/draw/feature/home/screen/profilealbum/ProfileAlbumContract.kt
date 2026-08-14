package com.a02.draw.feature.home.screen.profilealbum

import com.a02.draw.core.ui.base.UiEffect
import com.a02.draw.core.ui.base.UiState
import com.a02.draw.feature.home.common.component.ProfileDrawingRow
import com.a02.draw.feature.home.common.model.BottomDestination

data class ProfileAlbumUiState(
    val drawings: List<ProfileDrawingRow> = emptyList(),
    val sketchCount: Int = 0,
    val lessonCount: Int = 0,
    val lessonMinutes: Int = 0,
) : UiState

sealed interface ProfileAlbumAction {
    data class OpenDrawing(val drawingId: Long) : ProfileAlbumAction
    data object OpenFavorites : ProfileAlbumAction
    data class OpenBottom(val destination: BottomDestination) : ProfileAlbumAction
}

sealed interface ProfileAlbumEffect : UiEffect {
    data object NavigateComplete : ProfileAlbumEffect
    data object NavigateFavorites : ProfileAlbumEffect
    data class NavigateBottom(val destination: BottomDestination) : ProfileAlbumEffect
}
