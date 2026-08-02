package com.a02.draw.feature.home

import androidx.lifecycle.SavedStateHandle
import com.a02.draw.core.common.result.AppResult
import com.a02.draw.domain.model.AppPreferences
import com.a02.draw.domain.model.AppSettingItem
import com.a02.draw.domain.model.ArCatalog
import com.a02.draw.domain.model.Artwork
import com.a02.draw.domain.model.ContentImage
import com.a02.draw.domain.model.Drawing
import com.a02.draw.domain.model.DrawingLesson
import com.a02.draw.domain.model.DrawingLessonStep
import com.a02.draw.domain.model.DrawingTopic
import com.a02.draw.domain.model.LessonCategory
import com.a02.draw.domain.model.ThemeMode
import com.a02.draw.domain.repository.AppPreferencesRepository
import com.a02.draw.domain.repository.ArContentRepository
import com.a02.draw.domain.repository.DrawingRepository
import com.a02.draw.domain.usecase.GetArCatalogUseCase
import com.a02.draw.domain.usecase.ObserveAppPreferencesUseCase
import com.a02.draw.domain.usecase.ObserveDrawingsUseCase
import com.a02.draw.domain.usecase.UpdateAppPreferencesUseCase
import com.a02.draw.feature.home.common.drawing.DrawingControlAction
import com.a02.draw.feature.home.common.model.BottomDestination
import com.a02.draw.feature.home.common.model.DeviceImageSource
import com.a02.draw.feature.home.common.model.GalleryFilter
import com.a02.draw.feature.home.common.session.DefaultDrawingSessionStore
import com.a02.draw.feature.home.common.session.DefaultGalleryFilterSessionStore
import com.a02.draw.feature.home.common.session.GalleryFilterSession
import com.a02.draw.feature.home.screen.camera.CameraAction
import com.a02.draw.feature.home.screen.camera.CameraEffect
import com.a02.draw.feature.home.screen.camera.CameraViewModel
import com.a02.draw.feature.home.screen.drawingopacity.DrawingOpacityAction
import com.a02.draw.feature.home.screen.drawingopacity.DrawingOpacityEffect
import com.a02.draw.feature.home.screen.drawingopacity.DrawingOpacityViewModel
import com.a02.draw.feature.home.screen.filter.FilterAction
import com.a02.draw.feature.home.screen.filter.FilterEffect
import com.a02.draw.feature.home.screen.filter.FilterViewModel
import com.a02.draw.feature.home.screen.gallery.GalleryAction
import com.a02.draw.feature.home.screen.gallery.GalleryEffect
import com.a02.draw.feature.home.screen.gallery.GalleryViewModel
import com.a02.draw.feature.home.screen.home.HomeAction
import com.a02.draw.feature.home.screen.home.HomeScreenEffect
import com.a02.draw.feature.home.screen.home.HomeViewModel
import com.a02.draw.feature.home.screen.learn.LearnAction
import com.a02.draw.feature.home.screen.learn.LearnEffect
import com.a02.draw.feature.home.screen.learn.LearnViewModel
import com.a02.draw.feature.home.screen.learncategorydetail.LearnCategoryDetailAction
import com.a02.draw.feature.home.screen.learncategorydetail.LearnCategoryDetailEffect
import com.a02.draw.feature.home.screen.learncategorydetail.LearnCategoryDetailViewModel
import com.a02.draw.feature.home.screen.learnleveldetail.LearnLevelDetailAction
import com.a02.draw.feature.home.screen.learnleveldetail.LearnLevelDetailEffect
import com.a02.draw.feature.home.screen.learnleveldetail.LearnLevelDetailViewModel
import com.a02.draw.feature.home.screen.profilealbum.ProfileAlbumAction
import com.a02.draw.feature.home.screen.profilealbum.ProfileAlbumEffect
import com.a02.draw.feature.home.screen.profilealbum.ProfileAlbumViewModel
import com.a02.draw.feature.home.screen.profilefavorite.ProfileFavoriteAction
import com.a02.draw.feature.home.screen.profilefavorite.ProfileFavoriteEffect
import com.a02.draw.feature.home.screen.profilefavorite.ProfileFavoriteViewModel
import com.a02.draw.feature.home.screen.search.SearchAction
import com.a02.draw.feature.home.screen.search.SearchEffect
import com.a02.draw.feature.home.screen.search.SearchViewModel
import com.a02.draw.feature.home.screen.settings.SettingsAction
import com.a02.draw.feature.home.screen.settings.SettingsEffect
import com.a02.draw.feature.home.screen.settings.SettingsViewModel
import com.a02.draw.feature.home.screen.settingsdetail.SettingsDetailAction
import com.a02.draw.feature.home.screen.settingsdetail.SettingsDetailEffect
import com.a02.draw.feature.home.screen.settingsdetail.SettingsDetailViewModel
import com.a02.draw.feature.home.screen.tutorialcamera.TutorialCameraAction
import com.a02.draw.feature.home.screen.tutorialcamera.TutorialCameraEffect
import com.a02.draw.feature.home.screen.tutorialcamera.TutorialCameraViewModel
import com.a02.draw.feature.home.screen.tutorialscreen.TutorialScreenAction
import com.a02.draw.feature.home.screen.tutorialscreen.TutorialScreenEffect
import com.a02.draw.feature.home.screen.tutorialscreen.TutorialScreenViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScreenViewModelNavigationTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val content = GetArCatalogUseCase(FakeContentRepository())
    private val preferencesRepository = FakePreferencesRepository()
    private val drawingsRepository = FakeDrawingRepository()
    private val observePreferences = ObserveAppPreferencesUseCase(preferencesRepository)
    private val updatePreferences = UpdateAppPreferencesUseCase(preferencesRepository)
    private val observeDrawings = ObserveDrawingsUseCase(drawingsRepository)

    @Test
    fun `camera emits back`() = runTest {
        val viewModel = CameraViewModel(SavedStateHandle(), updatePreferences)
        viewModel.onAction(CameraAction.Control(DrawingControlAction.Back))
        assertEquals(CameraEffect.Finish, viewModel.effects.first())
    }

    @Test
    fun `camera lesson moves through ordered images and persists progress`() = runTest {
        val viewModel = CameraViewModel(
            SavedStateHandle(
                mapOf(
                    "camera.lesson_id" to "lesson-1",
                    "camera.lesson_step" to 0,
                    "camera.lesson_count" to 2,
                    "camera.lesson_step_urls" to arrayOf(
                        "https://example.com/1.png",
                        "https://example.com/2.png",
                    ),
                    "camera.lesson_step_local_keys" to arrayOf("", ""),
                ),
            ),
            updatePreferences,
        )

        viewModel.onAction(CameraAction.Control(DrawingControlAction.NextStep))
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.session.lessonStepIndex)
        assertEquals("https://example.com/2.png", viewModel.state.value.session.traceImage?.url)
        assertEquals(1, preferencesRepository.preferences.lessonCompletedSteps["lesson-1"])
    }

    @Test
    fun `opacity emits back`() = runTest {
        val viewModel = DrawingOpacityViewModel(DefaultDrawingSessionStore(), updatePreferences)
        viewModel.onAction(DrawingOpacityAction.Control(DrawingControlAction.Back))
        assertEquals(DrawingOpacityEffect.NavigateBack, viewModel.effects.first())
    }

    @Test
    fun `filter applies session and closes`() = runTest {
        val viewModel = FilterViewModel(DefaultGalleryFilterSessionStore())
        viewModel.onAction(FilterAction.Apply)
        assertEquals(FilterEffect.Close, viewModel.effects.first())
    }

    @Test
    fun `gallery opens filter`() = runTest {
        val viewModel = GalleryViewModel(
            SavedStateHandle(), content, observePreferences, updatePreferences,
            DefaultGalleryFilterSessionStore(), DefaultDrawingSessionStore(),
        )
        viewModel.onAction(GalleryAction.OpenFilter)
        assertEquals(GalleryEffect.NavigateFilter, viewModel.effects.first())
    }

    @Test
    fun `gallery clears filters left by the previous gallery`() = runTest {
        val filters = DefaultGalleryFilterSessionStore().apply {
            update {
                GalleryFilterSession(
                    topicId = "animal",
                    difficulty = "Hard",
                    quickFilter = GalleryFilter.SAVED,
                )
            }
        }

        GalleryViewModel(
            SavedStateHandle(), content, observePreferences, updatePreferences,
            filters, DefaultDrawingSessionStore(),
        )
        advanceUntilIdle()

        assertEquals(GalleryFilterSession(), filters.state.value)
    }

    @Test
    fun `home opens search`() = runTest {
        val viewModel = HomeViewModel(content, DefaultDrawingSessionStore())
        viewModel.onAction(HomeAction.OpenSearch)
        assertEquals(HomeScreenEffect.NavigateSearch, viewModel.effects.first())
    }

    @Test
    fun `home closes source modal before opening picker`() = runTest {
        val viewModel = HomeViewModel(content, DefaultDrawingSessionStore())
        viewModel.onAction(HomeAction.OpenSourceModal)
        viewModel.onAction(HomeAction.SelectSource(DeviceImageSource.GALLERY))
        viewModel.onAction(HomeAction.ConfirmSource)
        advanceUntilIdle()

        assertEquals(false, viewModel.state.value.isSourceModalVisible)
        assertEquals(HomeScreenEffect.OpenPhotoPicker, viewModel.effects.first())
    }

    @Test
    fun `learn opens search`() = runTest {
        val viewModel = LearnViewModel(content)
        viewModel.onAction(LearnAction.OpenSearch)
        assertEquals(LearnEffect.NavigateSearch, viewModel.effects.first())
    }

    @Test
    fun `category detail restores id and handles back`() = runTest {
        val viewModel = LearnCategoryDetailViewModel(
            SavedStateHandle(mapOf("categoryId" to "animal")), content, observePreferences,
            DefaultDrawingSessionStore(),
        )
        advanceUntilIdle()
        assertEquals("Animal", viewModel.state.value.title)
        viewModel.onAction(LearnCategoryDetailAction.Back)
        assertEquals(LearnCategoryDetailEffect.NavigateBack, viewModel.effects.first())
    }

    @Test
    fun `level detail restores lesson id and handles back`() = runTest {
        val viewModel = LearnLevelDetailViewModel(
            SavedStateHandle(mapOf("lessonId" to "lesson-1")), content,
            observePreferences,
            DefaultDrawingSessionStore(),
        )
        advanceUntilIdle()
        assertEquals("lesson-1", viewModel.state.value.lesson?.id)
        viewModel.onAction(LearnLevelDetailAction.Back)
        assertEquals(LearnLevelDetailEffect.NavigateBack, viewModel.effects.first())
    }

    @Test
    fun `level detail resumes from saved lesson step`() = runTest {
        preferencesRepository.setLessonCompletedSteps("lesson-1", 2)
        val drawingSession = DefaultDrawingSessionStore()
        val viewModel = LearnLevelDetailViewModel(
            SavedStateHandle(mapOf("lessonId" to "lesson-1")), content,
            observePreferences, drawingSession,
        )
        advanceUntilIdle()

        viewModel.onAction(LearnLevelDetailAction.OpenTutorial)
        advanceUntilIdle()

        assertEquals(2, drawingSession.state.value.lessonStepIndex)
        assertEquals("lesson_step_3", drawingSession.state.value.traceImage?.localKey)
        assertEquals(LearnLevelDetailEffect.NavigateTutorial, viewModel.effects.first())
    }

    @Test
    fun `album opens favorites`() = runTest {
        val viewModel = ProfileAlbumViewModel(
            content, observeDrawings, DefaultDrawingSessionStore(),
        )
        viewModel.onAction(ProfileAlbumAction.OpenFavorites)
        assertEquals(ProfileAlbumEffect.NavigateFavorites, viewModel.effects.first())
    }

    @Test
    fun `favorites opens album`() = runTest {
        val viewModel = ProfileFavoriteViewModel(
            content, observePreferences, observeDrawings, updatePreferences,
            DefaultDrawingSessionStore(),
        )
        viewModel.onAction(ProfileFavoriteAction.OpenAlbum)
        assertEquals(ProfileFavoriteEffect.NavigateAlbum, viewModel.effects.first())
    }

    @Test
    fun `search handles back`() = runTest {
        val viewModel = SearchViewModel(content)
        viewModel.onAction(SearchAction.Back)
        assertEquals(SearchEffect.NavigateBack, viewModel.effects.first())
    }

    @Test
    fun `settings emits bottom navigation`() = runTest {
        val viewModel = SettingsViewModel(content)
        viewModel.onAction(SettingsAction.OpenBottom(BottomDestination.HOME))
        assertEquals(
            SettingsEffect.NavigateBottom(BottomDestination.HOME),
            viewModel.effects.first(),
        )
    }

    @Test
    fun `settings detail restores id and handles back`() = runTest {
        val viewModel = SettingsDetailViewModel(SavedStateHandle(mapOf("settingId" to "help")))
        assertEquals(R.string.help_faqs, viewModel.state.value.title)
        viewModel.onAction(SettingsDetailAction.Back)
        assertEquals(SettingsDetailEffect.NavigateBack, viewModel.effects.first())
    }

    @Test
    fun `camera tutorial handles back`() = runTest {
        val viewModel = TutorialCameraViewModel(DefaultDrawingSessionStore())
        viewModel.onAction(TutorialCameraAction.Back)
        assertEquals(TutorialCameraEffect.NavigateBack, viewModel.effects.first())
    }

    @Test
    fun `screen tutorial opens canvas`() = runTest {
        val viewModel = TutorialScreenViewModel(DefaultDrawingSessionStore())
        viewModel.onAction(TutorialScreenAction.Start)
        assertEquals(TutorialScreenEffect.NavigateCanvas, viewModel.effects.first())
    }

    private class FakeContentRepository : ArContentRepository {
        override suspend fun getCatalog(forceRefresh: Boolean): AppResult<ArCatalog> =
            AppResult.Success(CATALOG)
    }

    private class FakePreferencesRepository : AppPreferencesRepository {
        private val state = MutableStateFlow(AppPreferences())
        val preferences: AppPreferences get() = state.value
        override fun observePreferences(): Flow<AppPreferences> = state
        override suspend fun setThemeMode(themeMode: ThemeMode) = AppResult.Success(Unit)
        override suspend fun setOnboardingCompleted(completed: Boolean) = AppResult.Success(Unit)
        override suspend fun setFavoriteArtworkIds(ids: Set<String>): AppResult<Unit> {
            state.value = state.value.copy(favoriteArtworkIds = ids)
            return AppResult.Success(Unit)
        }

        override suspend fun setLessonCompletedSteps(
            lessonId: String,
            completedSteps: Int,
        ): AppResult<Unit> {
            state.value = state.value.copy(
                lessonCompletedSteps = state.value.lessonCompletedSteps + (lessonId to completedSteps),
            )
            return AppResult.Success(Unit)
        }

        override suspend fun setMusicEnabled(enabled: Boolean) = AppResult.Success(Unit)
    }

    private class FakeDrawingRepository : DrawingRepository {
        override fun observeDrawings(): Flow<List<Drawing>> = MutableStateFlow(emptyList())
        override suspend fun getDrawing(id: Long): AppResult<Drawing> = error("unused")
        override suspend fun saveDrawing(drawing: Drawing): AppResult<Long> = AppResult.Success(1)
        override suspend fun deleteDrawing(id: Long): AppResult<Unit> = AppResult.Success(Unit)
    }

    private companion object {
        val IMAGE = ContentImage(localKey = "topic_animal")
        val CATALOG = ArCatalog(
            topics = listOf(DrawingTopic("animal", "Animal", IMAGE)),
            artworks = listOf(Artwork("art-1", "animal", "Dog", IMAGE)),
            lessons = listOf(
                DrawingLesson(
                    "lesson-1",
                    "animal",
                    "Dog lesson",
                    5,
                    image = IMAGE,
                    totalSteps = 3,
                    steps = (1..3).map {
                        DrawingLessonStep(it, ContentImage(localKey = "lesson_step_$it"))
                    },
                ),
            ),
            categories = listOf(LessonCategory("animal", "Animal", "Easy", 1, IMAGE)),
            plans = emptyList(),
            settings = listOf(AppSettingItem("help", "Help & FAQs")),
        )
    }
}
