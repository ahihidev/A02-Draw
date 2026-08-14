package com.a02.draw.feature.home.screen.profilefavorite

import androidx.lifecycle.viewModelScope
import com.a02.draw.core.common.result.AppResult
import com.a02.draw.core.ui.base.BaseViewModel
import com.a02.draw.domain.model.ArCatalog
import com.a02.draw.domain.model.Drawing
import com.a02.draw.domain.usecase.GetArCatalogUseCase
import com.a02.draw.domain.usecase.ObserveAppPreferencesUseCase
import com.a02.draw.domain.usecase.ObserveDrawingsUseCase
import com.a02.draw.domain.usecase.UpdateAppPreferencesUseCase
import com.a02.draw.feature.home.common.component.ProfileDrawingRow
import com.a02.draw.feature.home.common.model.DrawingMode
import com.a02.draw.feature.home.common.session.DrawingSession
import com.a02.draw.feature.home.common.session.DrawingSessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileFavoriteViewModel @Inject constructor(
    private val getCatalog: GetArCatalogUseCase,
    observePreferences: ObserveAppPreferencesUseCase,
    observeDrawings: ObserveDrawingsUseCase,
    private val updatePreferences: UpdateAppPreferencesUseCase,
    private val drawingSession: DrawingSessionStore,
) : BaseViewModel<ProfileFavoriteUiState, ProfileFavoriteEffect>(ProfileFavoriteUiState()) {
    private var catalog: ArCatalog? = null
    private var favoriteIds = emptySet<String>()
    private var drawings = emptyList<Drawing>()

    init {
        viewModelScope.launch {
            when (val result = getCatalog()) {
                is AppResult.Success -> {
                    catalog = result.data
                    render()
                }

                is AppResult.Failure -> Unit
            }
        }
        viewModelScope.launch {
            observePreferences().collect {
                favoriteIds = it.favoriteArtworkIds
                render()
            }
        }
        viewModelScope.launch {
            observeDrawings().collect {
                drawings = it
                render()
            }
        }
    }

    fun onAction(action: ProfileFavoriteAction) {
        when (action) {
            is ProfileFavoriteAction.OpenArtwork -> openArtwork(action.artworkId)
            is ProfileFavoriteAction.OpenDrawing -> openDrawing(action.drawingId)
            is ProfileFavoriteAction.ToggleFavorite -> toggleFavorite(action.artworkId)
            is ProfileFavoriteAction.SelectTab -> updateState { copy(selectedTab = action.tab) }
            is ProfileFavoriteAction.OpenBottom -> send(ProfileFavoriteEffect.NavigateBottom(action.destination))
        }
    }

    private fun render() {
        updateState {
            copy(
                favorites = catalog?.artworks.orEmpty().filter { it.id in favoriteIds },
                albumDrawings = drawings.map { drawing ->
                    ProfileDrawingRow(
                        drawing.id,
                        drawing.title,
                        drawing.mediaUri,
                        catalog?.artworks?.firstOrNull { it.id == drawing.artworkId }?.image,
                    )
                },
                favoriteIds = this@ProfileFavoriteViewModel.favoriteIds,
                sketchCount = drawings.size,
                lessonCount = drawings.mapNotNull(Drawing::lessonId).distinct().size,
                lessonMinutes = drawings.filter { it.lessonId != null }
                    .distinctBy(Drawing::lessonId)
                    .sumOf { it.lessonMinutes ?: 0 },
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
        send(ProfileFavoriteEffect.NavigateTutorial)
    }

    private fun openDrawing(id: Long) {
        val drawing = drawings.firstOrNull { it.id == id } ?: return
        val artwork = catalog?.artworks?.firstOrNull { it.id == drawing.artworkId }
        val lesson = catalog?.lessons?.firstOrNull { it.id == drawing.lessonId }
        drawingSession.reset(
            DrawingSession(
                artworkId = drawing.artworkId,
                lessonId = drawing.lessonId,
                referenceTitle = drawing.title,
                referenceImage = lesson?.image ?: artwork?.image,
                traceImage = lesson?.image ?: artwork?.traceImage ?: artwork?.image,
                capturedImageUri = drawing.mediaUri,
                activeDrawingId = drawing.id,
                lessonMinutes = drawing.lessonMinutes,
                mode = if (drawing.usesCamera) DrawingMode.CAMERA else DrawingMode.SCREEN,
            ),
        )
        send(ProfileFavoriteEffect.NavigateComplete)
    }

    private fun toggleFavorite(id: String) {
        val previous = favoriteIds
        val next = favoriteIds.toMutableSet().apply { if (!add(id)) remove(id) }
        favoriteIds = next
        render()
        viewModelScope.launch {
            if (updatePreferences.setFavorites(next) is AppResult.Failure && favoriteIds == next) {
                favoriteIds = previous
                render()
                sendEffect(ProfileFavoriteEffect.ShowError)
            }
        }
    }

    private fun send(effect: ProfileFavoriteEffect) {
        viewModelScope.launch { sendEffect(effect) }
    }
}
