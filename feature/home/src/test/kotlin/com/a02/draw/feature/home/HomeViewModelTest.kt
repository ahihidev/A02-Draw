package com.a02.draw.feature.home

import app.cash.turbine.test
import com.a02.draw.core.common.result.AppError
import com.a02.draw.core.common.result.AppResult
import com.a02.draw.domain.model.AppPreferences
import com.a02.draw.domain.model.AppSettingItem
import com.a02.draw.domain.model.ArCatalog
import com.a02.draw.domain.model.Artwork
import com.a02.draw.domain.model.ArtworkStyle
import com.a02.draw.domain.model.ContentImage
import com.a02.draw.domain.model.Drawing
import com.a02.draw.domain.model.DrawingTopic
import com.a02.draw.domain.model.SubscriptionPlan
import com.a02.draw.domain.model.ThemeMode
import com.a02.draw.domain.repository.AppPreferencesRepository
import com.a02.draw.domain.repository.ArContentRepository
import com.a02.draw.domain.repository.DrawingRepository
import com.a02.draw.domain.usecase.GetArCatalogUseCase
import com.a02.draw.domain.usecase.ObserveAppPreferencesUseCase
import com.a02.draw.domain.usecase.ObserveDrawingsUseCase
import com.a02.draw.domain.usecase.SaveDrawingUseCase
import com.a02.draw.domain.usecase.UpdateAppPreferencesUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val drawings = MutableStateFlow(
        listOf(Drawing(id = 1, title = "Sketch", updatedAtEpochMillis = 10)),
    )
    private val preferences = MutableStateFlow(AppPreferences())
    private val savedDrawings = mutableListOf<Drawing>()
    private var failPreferencesUpdate = false
    private val drawingRepository = object : DrawingRepository {
        override fun observeDrawings(): Flow<List<Drawing>> = drawings
        override suspend fun getDrawing(id: Long): AppResult<Drawing> = error("unused")
        override suspend fun saveDrawing(drawing: Drawing): AppResult<Long> {
            savedDrawings += drawing
            return AppResult.Success(2)
        }
        override suspend fun deleteDrawing(id: Long): AppResult<Unit> = error("unused")
    }
    private val preferencesRepository = object : AppPreferencesRepository {
        override fun observePreferences(): Flow<AppPreferences> = preferences
        override suspend fun setThemeMode(themeMode: ThemeMode): AppResult<Unit> =
            AppResult.Success(Unit)

        override suspend fun setOnboardingCompleted(completed: Boolean): AppResult<Unit> {
            if (failPreferencesUpdate) return AppResult.Failure(AppError.Validation("write failed"))
            preferences.value = preferences.value.copy(onboardingCompleted = completed)
            return AppResult.Success(Unit)
        }

        override suspend fun setFavoriteArtworkIds(ids: Set<String>): AppResult<Unit> {
            preferences.value = preferences.value.copy(favoriteArtworkIds = ids)
            return AppResult.Success(Unit)
        }

        override suspend fun setMusicEnabled(enabled: Boolean): AppResult<Unit> {
            if (failPreferencesUpdate) return AppResult.Failure(AppError.Validation("write failed"))
            preferences.value = preferences.value.copy(musicEnabled = enabled)
            return AppResult.Success(Unit)
        }
    }

    @Test
    fun `catalog and saved drawings are exposed in state`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(CATALOG, viewModel.state.value.catalog)
        assertEquals(drawings.value, viewModel.state.value.drawings)
    }

    @Test
    fun `completed onboarding opens home on a later launch`() = runTest {
        preferences.value = AppPreferences(onboardingCompleted = true)
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(ArDrawScreen.HOME, viewModel.state.value.screen)
    }

    @Test
    fun `onboarding continues through personalization to home`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        repeat(3) { viewModel.onAction(ArDrawAction.Continue) }
        assertEquals(ArDrawScreen.ONBOARDING_TOPICS, viewModel.state.value.screen)
        viewModel.onAction(ArDrawAction.Continue)
        assertEquals(ArDrawScreen.ONBOARDING_LOADING, viewModel.state.value.screen)

        advanceTimeBy(1_500)
        advanceUntilIdle()
        assertEquals(ArDrawScreen.HOME, viewModel.state.value.screen)
        assertTrue(preferences.value.onboardingCompleted)
    }

    @Test
    fun `catalog plans show paywall and preserve the selected plan`() = runTest {
        val catalogWithPlans = CATALOG.copy(
            plans = listOf(
                SubscriptionPlan(
                    "annual",
                    "Annual",
                    "7 day trial",
                    "\$29.99",
                    isRecommended = true
                ),
            ),
        )
        val viewModel = createViewModel(catalogWithPlans)
        advanceUntilIdle()

        repeat(4) { viewModel.onAction(ArDrawAction.Continue) }
        advanceTimeBy(1_500)
        advanceUntilIdle()

        assertEquals(ArDrawScreen.ONBOARDING_PAYWALL, viewModel.state.value.screen)
        viewModel.onAction(ArDrawAction.SelectPlan("annual"))
        assertEquals("annual", viewModel.state.value.selectedPlanId)
    }

    @Test
    fun `topic selection is capped at three API ids`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        repeat(5) { viewModel.onAction(ArDrawAction.ToggleTopic(it)) }

        assertEquals(setOf("topic-3", "topic-4", "topic-5"), viewModel.state.value.selectedTopicIds)
    }

    @Test
    fun `search filters API artwork by title and tags`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAction(ArDrawAction.SearchQueryChanged("manga"))
        viewModel.onAction(ArDrawAction.SubmitSearch)

        assertEquals(ArDrawScreen.SEARCH_RESULTS, viewModel.state.value.screen)
        assertEquals(listOf("art-2"), viewModel.state.value.visibleArtworks.map { it.id })
    }

    @Test
    fun `search remains global after visiting a topic gallery`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAction(ArDrawAction.OpenGallery("topic-1"))
        viewModel.onAction(ArDrawAction.Back)
        viewModel.onAction(ArDrawAction.OpenSearch)
        viewModel.onAction(ArDrawAction.SearchQueryChanged("manga"))
        viewModel.onAction(ArDrawAction.SubmitSearch)

        assertEquals(listOf("art-2"), viewModel.state.value.visibleArtworks.map { it.id })
    }

    @Test
    fun `favorite changes are persisted`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onAction(ArDrawAction.OpenGallery())

        viewModel.onAction(ArDrawAction.ToggleFavorite("art-1"))
        advanceUntilIdle()

        assertEquals(setOf("art-1"), preferences.value.favoriteArtworkIds)
        assertEquals(ArDrawScreen.GALLERY, viewModel.state.value.screen)
    }

    @Test
    fun `camera flow requests permission captures and saves photo`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onAction(ArDrawAction.OpenTutorial)

        viewModel.effects.test {
            viewModel.onAction(ArDrawAction.StartDrawing)
            assertEquals(HomeEffect.RequestCameraPermission, awaitItem())
            viewModel.onAction(ArDrawAction.CameraPermissionResult(true))
            assertEquals(ArDrawScreen.DRAWING_CAMERA, viewModel.state.value.screen)
            viewModel.onAction(ArDrawAction.CaptureDrawing)
            assertEquals(HomeEffect.CapturePhoto, awaitItem())
            viewModel.onAction(ArDrawAction.PhotoCaptured("content://draw/capture.jpg"))
            assertEquals(HomeEffect.ShowMessage(R.string.drawing_created), awaitItem())
        }

        assertEquals(ArDrawScreen.DRAWING_COMPLETE, viewModel.state.value.screen)
        assertEquals("content://draw/capture.jpg", savedDrawings.single().mediaUri)
    }

    @Test
    fun `gallery picker continues with selected device image`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.effects.test {
            viewModel.onAction(ArDrawAction.OpenSourceModal)
            viewModel.onAction(ArDrawAction.SelectDeviceSource(DeviceImageSource.GALLERY))
            viewModel.onAction(ArDrawAction.ConfirmSource)
            assertEquals(HomeEffect.OpenPhotoPicker, awaitItem())
        }
        viewModel.onAction(ArDrawAction.MediaPicked("content://gallery/image.png"))

        assertEquals("content://gallery/image.png", viewModel.state.value.pickedImageUri)
        assertEquals(ArDrawScreen.TUTORIAL_CAMERA, viewModel.state.value.screen)
    }

    @Test
    fun `canvas controls retain the selected crop grid and image state`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAction(ArDrawAction.OpenCanvasPanel(DrawingCanvasPanel.CROP))
        viewModel.onAction(ArDrawAction.SelectCropRatio(DrawingCropRatio.PORTRAIT))
        viewModel.onAction(ArDrawAction.OpenCanvasPanel(DrawingCanvasPanel.GRID))
        viewModel.onAction(ArDrawAction.SelectGridSize(4))
        viewModel.onAction(ArDrawAction.ToggleFlip)
        viewModel.onAction(ArDrawAction.ToggleRemoveImage)

        assertEquals(DrawingCropRatio.PORTRAIT, viewModel.state.value.cropRatio)
        assertEquals(4, viewModel.state.value.gridSize)
        assertTrue(viewModel.state.value.overlayFlipped)
        assertTrue(viewModel.state.value.removeImageEnabled)
    }

    @Test
    fun `overlay transform is committed atomically and clamped`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAction(ArDrawAction.SetOverlayTransform(999f, -999f, 8f))

        assertEquals(150f, viewModel.state.value.overlayOffsetX)
        assertEquals(-220f, viewModel.state.value.overlayOffsetY)
        assertEquals(3f, viewModel.state.value.zoom)
    }

    @Test
    fun `tapping an open canvas or camera panel closes it`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAction(ArDrawAction.OpenCanvasPanel(DrawingCanvasPanel.CROP))
        viewModel.onAction(ArDrawAction.OpenCanvasPanel(DrawingCanvasPanel.CROP))
        assertEquals(DrawingCanvasPanel.NONE, viewModel.state.value.canvasPanel)

        viewModel.onAction(ArDrawAction.OpenCameraPanel(DrawingCameraPanel.RATIO))
        viewModel.onAction(ArDrawAction.OpenCameraPanel(DrawingCameraPanel.RATIO))
        assertEquals(DrawingCameraPanel.NONE, viewModel.state.value.cameraPanel)
    }

    @Test
    fun `camera zoom and recording dispatch platform effects`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.effects.test {
            viewModel.onAction(ArDrawAction.SelectCameraZoom(1.5f))
            assertEquals(HomeEffect.SetCameraZoom(1.5f), awaitItem())
            viewModel.onAction(ArDrawAction.ToggleRecording)
            assertEquals(HomeEffect.SetRecording(true), awaitItem())
            viewModel.onAction(ArDrawAction.ToggleRecording)
            assertEquals(HomeEffect.SetRecording(false), awaitItem())
        }

        assertEquals(1.5f, viewModel.state.value.cameraZoom)
        assertTrue(!viewModel.state.value.isRecording)
    }

    @Test
    fun `drawing toolbar does not change the selected camera mode`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onAction(ArDrawAction.SyncCameraPermission(true))
        viewModel.onAction(ArDrawAction.OpenTutorial)
        viewModel.onAction(ArDrawAction.StartDrawing)

        viewModel.onAction(ArDrawAction.SelectDrawingTool(ArDrawScreen.DRAWING_CANVAS))
        viewModel.onAction(ArDrawAction.SelectDrawingTool(ArDrawScreen.DRAWING_OPACITY))

        assertTrue(viewModel.state.value.drawingWithCamera)
        assertEquals(ArDrawScreen.DRAWING_OPACITY, viewModel.state.value.screen)
    }

    @Test
    fun `screen mode completion requests a rendered canvas capture`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onAction(ArDrawAction.OpenTutorial)
        viewModel.onAction(ArDrawAction.SelectTutorialMode(false))
        viewModel.onAction(ArDrawAction.StartDrawing)

        viewModel.effects.test {
            viewModel.onAction(ArDrawAction.CompleteDrawing)
            assertEquals(HomeEffect.CaptureCanvas, awaitItem())
        }
        assertTrue(!viewModel.state.value.drawingWithCamera)
    }

    @Test
    fun `tutorial back returns to the screen that opened it`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onAction(ArDrawAction.OpenLearnPath)
        viewModel.onAction(ArDrawAction.OpenLearnDetail())
        viewModel.onAction(ArDrawAction.OpenTutorial)

        viewModel.onAction(ArDrawAction.Back)

        assertEquals(ArDrawScreen.LEARN_LEVEL_DETAIL, viewModel.state.value.screen)
    }

    @Test
    fun `drawing style is a real gallery filter and reset restores color`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onAction(ArDrawAction.SelectDrawingStyle(ArtworkStyle.LINE_SKETCH))

        assertTrue(viewModel.state.value.visibleGalleryArtworks.isEmpty())

        viewModel.onAction(ArDrawAction.ClearFilters)
        assertEquals(null, viewModel.state.value.selectedDrawingStyle)
        assertEquals(2, viewModel.state.value.visibleGalleryArtworks.size)
    }

    @Test
    fun `tapping active gallery filters clears them without opening reset`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAction(ArDrawAction.SelectGalleryFilter(GalleryFilter.EASY))
        viewModel.onAction(ArDrawAction.SelectGalleryFilter(GalleryFilter.EASY))
        viewModel.onAction(ArDrawAction.SelectDifficulty("Hard"))
        viewModel.onAction(ArDrawAction.SelectDifficulty("Hard"))
        viewModel.onAction(ArDrawAction.SelectDrawingStyle(ArtworkStyle.COLOR))
        viewModel.onAction(ArDrawAction.SelectDrawingStyle(ArtworkStyle.COLOR))

        assertEquals(GalleryFilter.ALL, viewModel.state.value.selectedGalleryFilter)
        assertEquals(null, viewModel.state.value.selectedDifficulty)
        assertEquals(null, viewModel.state.value.selectedDrawingStyle)
    }

    @Test
    fun `recording finalize always clears recording state`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onAction(ArDrawAction.ToggleRecording)
        viewModel.onAction(ArDrawAction.RecordingFinished)

        assertTrue(!viewModel.state.value.isRecording)
    }

    @Test
    fun `quick draw opens the complete API artwork catalog`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAction(ArDrawAction.OpenAiEmojiMix)

        assertEquals(ArDrawScreen.GALLERY, viewModel.state.value.screen)
        assertEquals(null, viewModel.state.value.selectedGalleryTopicId)
        assertEquals(CATALOG.artworks, viewModel.state.value.visibleGalleryArtworks)
    }

    @Test
    fun `granting camera permission starts a clean AR session`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onAction(ArDrawAction.ToggleFlip)
        viewModel.onAction(ArDrawAction.ChangeZoom(2f))
        viewModel.onAction(ArDrawAction.OpenCanvasPanel(DrawingCanvasPanel.GRID))

        viewModel.onAction(ArDrawAction.CameraPermissionResult(true))

        assertEquals(ArDrawScreen.DRAWING_CAMERA, viewModel.state.value.screen)
        assertEquals(1f, viewModel.state.value.zoom)
        assertTrue(!viewModel.state.value.overlayFlipped)
        assertEquals(DrawingCanvasPanel.NONE, viewModel.state.value.canvasPanel)
    }

    @Test
    fun `tapping the selected grid size disables the grid and closes its panel`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onAction(ArDrawAction.SelectGridSize(3))
        assertEquals(3, viewModel.state.value.gridSize)
        assertEquals(DrawingCanvasPanel.GRID, viewModel.state.value.canvasPanel)

        viewModel.onAction(ArDrawAction.SelectGridSize(3))

        assertEquals(0, viewModel.state.value.gridSize)
        assertEquals(DrawingCanvasPanel.NONE, viewModel.state.value.canvasPanel)
    }

    @Test
    fun `help setting opens an in-app detail and back returns to settings`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAction(ArDrawAction.OpenSetting("help"))
        assertEquals(ArDrawScreen.SETTINGS_DETAIL, viewModel.state.value.screen)
        assertEquals("help", viewModel.state.value.selectedSettingId)

        viewModel.onAction(ArDrawAction.Back)
        assertEquals(ArDrawScreen.SETTINGS, viewModel.state.value.screen)
    }

    private fun createViewModel(catalog: ArCatalog = CATALOG): HomeViewModel = HomeViewModel(
        getCatalog = GetArCatalogUseCase(
            object : ArContentRepository {
                override suspend fun getCatalog(forceRefresh: Boolean): AppResult<ArCatalog> =
                    AppResult.Success(catalog)
            },
        ),
        observeAppPreferences = ObserveAppPreferencesUseCase(preferencesRepository),
        updatePreferences = UpdateAppPreferencesUseCase(preferencesRepository),
        observeDrawings = ObserveDrawingsUseCase(drawingRepository),
        saveDrawing = SaveDrawingUseCase(drawingRepository),
    )

    private companion object {
        val CATALOG = ArCatalog(
            topics = (1..5).map {
                DrawingTopic("topic-$it", "Topic $it", ContentImage(localKey = "topic_chibi"))
            },
            artworks = listOf(
                Artwork(
                    "art-1",
                    "topic-1",
                    "Cute portrait",
                    ContentImage(localKey = "topic_chibi")
                ),
                Artwork(
                    "art-2",
                    "topic-2",
                    "Anime hero",
                    ContentImage(localKey = "topic_anime"),
                    tags = listOf("manga"),
                ),
            ),
            lessons = emptyList(),
            categories = emptyList(),
            plans = emptyList(),
            settings = listOf(
                AppSettingItem("music", "Music"),
                AppSettingItem("help", "Help & FAQs"),
            ),
        )
    }
}
