package com.a02.draw.feature.home

import androidx.lifecycle.SavedStateHandle
import com.a02.draw.core.common.result.AppResult
import com.a02.draw.domain.model.AppPreferences
import com.a02.draw.domain.model.ArCatalog
import com.a02.draw.domain.model.Artwork
import com.a02.draw.domain.model.ContentImage
import com.a02.draw.domain.model.Drawing
import com.a02.draw.domain.model.ThemeMode
import com.a02.draw.domain.repository.AppPreferencesRepository
import com.a02.draw.domain.repository.ArContentRepository
import com.a02.draw.domain.repository.DrawingRepository
import com.a02.draw.domain.usecase.GetArCatalogUseCase
import com.a02.draw.domain.usecase.SaveDrawingUseCase
import com.a02.draw.domain.usecase.UpdateAppPreferencesUseCase
import com.a02.draw.feature.home.common.drawing.DrawingControlAction
import com.a02.draw.feature.home.common.model.DrawingMode
import com.a02.draw.feature.home.common.session.DefaultDrawingSessionStore
import com.a02.draw.feature.home.common.session.DrawingSession
import com.a02.draw.feature.home.common.session.atLessonStep
import com.a02.draw.feature.home.screen.drawingcanvas.DrawingCanvasAction
import com.a02.draw.feature.home.screen.drawingcanvas.DrawingCanvasEffect
import com.a02.draw.feature.home.screen.drawingcanvas.DrawingCanvasViewModel
import com.a02.draw.feature.home.screen.drawingcomplete.DrawingCompleteAction
import com.a02.draw.feature.home.screen.drawingcomplete.DrawingCompleteEffect
import com.a02.draw.feature.home.screen.drawingcomplete.DrawingCompleteViewModel
import com.a02.draw.feature.home.screen.searchresults.SearchResultsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScreenArchitectureTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `drawing session is shared and can be reset between screens`() {
        val store = DefaultDrawingSessionStore()

        store.update { copy(artworkId = "art-1", opacity = 0.7f) }
        assertEquals("art-1", store.state.value.artworkId)
        assertEquals(0.7f, store.state.value.opacity)

        store.reset(DrawingSession(lessonId = "lesson-2"))
        assertEquals("lesson-2", store.state.value.lessonId)
        assertEquals(null, store.state.value.artworkId)
    }

    @Test
    fun `drawing session selects the matching lesson image`() {
        val first = ContentImage(localKey = "step-1")
        val second = ContentImage(localKey = "step-2")
        val session = DrawingSession(
            traceImage = first,
            lessonSteps = listOf(first, second),
            lessonStepCount = 2,
        )

        val next = session.atLessonStep(1)

        assertEquals(1, next.lessonStepIndex)
        assertEquals(second, next.traceImage)
    }

    @Test
    fun `canvas view model reduces controls and emits navigation`() = runTest {
        val store = DefaultDrawingSessionStore()
        val viewModel = DrawingCanvasViewModel(
            store,
            UpdateAppPreferencesUseCase(FakePreferencesRepository()),
        )
        advanceUntilIdle()

        viewModel.onAction(
            DrawingCanvasAction.Control(DrawingControlAction.ChangeOpacity(0.8f)),
        )
        viewModel.onAction(DrawingCanvasAction.Control(DrawingControlAction.Complete))
        advanceUntilIdle()

        assertEquals(0.8f, store.state.value.opacity)
        assertEquals(null, store.state.value.capturedImageUri)
        assertEquals(DrawingCanvasEffect.NavigateComplete, viewModel.effects.first())
    }

    @Test
    fun `complete view model asks for a result photo before saving`() = runTest {
        val store = DefaultDrawingSessionStore()
        val repository = RecordingDrawingRepository()
        val viewModel = DrawingCompleteViewModel(store, SaveDrawingUseCase(repository))

        viewModel.onAction(DrawingCompleteAction.TakePhoto)
        assertEquals(DrawingCompleteEffect.LaunchResultCamera, viewModel.effects.first())
        assertTrue(repository.saved.isEmpty())

        viewModel.onAction(DrawingCompleteAction.PhotoCaptured("content://drawing/photo"))
        advanceUntilIdle()

        assertEquals("content://drawing/photo", viewModel.state.value.capturedUri)
        assertTrue(viewModel.state.value.isSaved)
        assertEquals(listOf("content://drawing/photo"), repository.saved.map(Drawing::mediaUri))
    }

    @Test
    fun `complete view model returns to the active drawing when it is not finished`() = runTest {
        val store = DefaultDrawingSessionStore().apply {
            reset(DrawingSession(mode = DrawingMode.CAMERA))
        }
        val viewModel = DrawingCompleteViewModel(
            store,
            SaveDrawingUseCase(RecordingDrawingRepository()),
        )

        viewModel.onAction(DrawingCompleteAction.ContinueDrawing)

        assertEquals(
            DrawingCompleteEffect.ContinueDrawing(DrawingMode.CAMERA),
            viewModel.effects.first(),
        )
    }

    @Test
    fun `complete view model saves drawing only once`() = runTest {
        val store = DefaultDrawingSessionStore().apply {
            reset(
                DrawingSession(
                    referenceTitle = "Bird",
                    capturedImageUri = "content://drawing/bird",
                    mode = DrawingMode.SCREEN,
                ),
            )
        }
        val repository = RecordingDrawingRepository()
        val viewModel = DrawingCompleteViewModel(store, SaveDrawingUseCase(repository))
        advanceUntilIdle()

        viewModel.onAction(DrawingCompleteAction.Share)
        viewModel.onAction(DrawingCompleteAction.Home)
        advanceUntilIdle()

        assertEquals(1, repository.saved.size)
        assertTrue(viewModel.state.value.isSaved)
        assertEquals(42L, store.state.value.activeDrawingId)
        assertEquals(
            DrawingCompleteEffect.Share("content://drawing/bird"),
            viewModel.effects.first()
        )
    }

    @Test
    fun `saved navigation query restores search results after recreation`() = runTest {
        val catalog = ArCatalog(
            topics = emptyList(),
            artworks = listOf(
                Artwork("dog", "animal", "Happy Dog", ContentImage()),
                Artwork("cat", "animal", "Quiet Cat", ContentImage()),
            ),
            lessons = emptyList(),
            categories = emptyList(),
            plans = emptyList(),
            settings = emptyList(),
        )
        val viewModel = SearchResultsViewModel(
            SavedStateHandle(mapOf("query" to "dog")),
            GetArCatalogUseCase(
                object : ArContentRepository {
                    override suspend fun getCatalog(forceRefresh: Boolean) =
                        AppResult.Success(catalog)
                },
            ),
            DefaultDrawingSessionStore(),
        )
        advanceUntilIdle()

        assertEquals("dog", viewModel.state.value.query)
        assertEquals(listOf("dog"), viewModel.state.value.results.map(Artwork::id))
    }

    private class RecordingDrawingRepository : DrawingRepository {
        val saved = mutableListOf<Drawing>()

        override fun observeDrawings(): Flow<List<Drawing>> = emptyFlow()
        override suspend fun getDrawing(id: Long): AppResult<Drawing> = error("unused")
        override suspend fun saveDrawing(drawing: Drawing): AppResult<Long> {
            saved += drawing
            return AppResult.Success(42L)
        }

        override suspend fun deleteDrawing(id: Long): AppResult<Unit> = error("unused")
    }

    private class FakePreferencesRepository : AppPreferencesRepository {
        override fun observePreferences(): Flow<AppPreferences> = emptyFlow()
        override suspend fun setThemeMode(themeMode: ThemeMode) = AppResult.Success(Unit)
        override suspend fun setOnboardingCompleted(completed: Boolean) = AppResult.Success(Unit)
        override suspend fun setFavoriteArtworkIds(ids: Set<String>) = AppResult.Success(Unit)
        override suspend fun setLessonCompletedSteps(lessonId: String, completedSteps: Int) =
            AppResult.Success(Unit)

        override suspend fun setMusicEnabled(enabled: Boolean) = AppResult.Success(Unit)
    }
}
