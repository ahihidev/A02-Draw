package com.a02.draw.feature.home.screen.profilealbum

import androidx.lifecycle.viewModelScope
import com.a02.draw.core.common.result.AppResult
import com.a02.draw.core.ui.base.BaseViewModel
import com.a02.draw.domain.model.ArCatalog
import com.a02.draw.domain.model.Drawing
import com.a02.draw.domain.usecase.GetArCatalogUseCase
import com.a02.draw.domain.usecase.ObserveDrawingsUseCase
import com.a02.draw.feature.home.common.component.ProfileDrawingRow
import com.a02.draw.feature.home.common.model.DrawingMode
import com.a02.draw.feature.home.common.session.DrawingSession
import com.a02.draw.feature.home.common.session.DrawingSessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class ProfileAlbumViewModel @Inject constructor(
    private val getCatalog: GetArCatalogUseCase,
    observeDrawings: ObserveDrawingsUseCase,
    private val drawingSession: DrawingSessionStore,
) : BaseViewModel<ProfileAlbumUiState, ProfileAlbumEffect>(ProfileAlbumUiState()) {
    private var catalog: ArCatalog? = null
    private var sourceDrawings = emptyList<Drawing>()

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
            observeDrawings().collect {
                sourceDrawings = it
                render()
            }
        }
    }

    fun onAction(action: ProfileAlbumAction) {
        when (action) {
            is ProfileAlbumAction.OpenDrawing -> openDrawing(action.drawingId)
            ProfileAlbumAction.OpenFavorites -> send(ProfileAlbumEffect.NavigateFavorites)
            is ProfileAlbumAction.OpenBottom -> send(ProfileAlbumEffect.NavigateBottom(action.destination))
        }
    }

    private fun render() {
        updateState {
            copy(
                drawings = sourceDrawings.map { drawing ->
                    ProfileDrawingRow(
                        drawing.id,
                        drawing.title,
                        drawing.mediaUri,
                        catalog?.artworks?.firstOrNull { it.id == drawing.artworkId }?.image,
                    )
                },
                sketchCount = sourceDrawings.size,
                lessonCount = sourceDrawings.mapNotNull(Drawing::lessonId).distinct().size,
                lessonMinutes = sourceDrawings.filter { it.lessonId != null }
                    .distinctBy(Drawing::lessonId)
                    .sumOf { it.lessonMinutes ?: 0 },
            )
        }
    }

    private fun openDrawing(id: Long) {
        val drawing = sourceDrawings.firstOrNull { it.id == id } ?: return
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
        send(ProfileAlbumEffect.NavigateComplete)
    }

    private fun send(effect: ProfileAlbumEffect) {
        viewModelScope.launch { sendEffect(effect) }
    }
}
