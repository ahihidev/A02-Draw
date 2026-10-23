package com.a02.draw

import com.a02.draw.core.common.result.AppResult
import com.a02.draw.domain.model.AppPreferences
import com.a02.draw.domain.model.ArCatalog
import com.a02.draw.domain.model.ContentImage
import com.a02.draw.domain.model.DrawingTopic
import com.a02.draw.domain.model.ThemeMode
import com.a02.draw.domain.repository.AppPreferencesRepository
import com.a02.draw.domain.repository.ArContentRepository
import com.a02.draw.domain.usecase.GetArCatalogUseCase
import com.a02.draw.domain.usecase.UpdateAppPreferencesUseCase
import com.a02.draw.onboarding.common.session.DefaultOnboardingSessionStore
import com.a02.draw.onboarding.lessons.LessonsAction
import com.a02.draw.onboarding.lessons.LessonsEffect
import com.a02.draw.onboarding.lessons.LessonsViewModel
import com.a02.draw.onboarding.lightbox.LightboxAction
import com.a02.draw.onboarding.lightbox.LightboxEffect
import com.a02.draw.onboarding.lightbox.LightboxViewModel
import com.a02.draw.onboarding.preparing.PreparingEffect
import com.a02.draw.onboarding.preparing.PreparingViewModel
import com.a02.draw.onboarding.projector.ProjectorAction
import com.a02.draw.onboarding.projector.ProjectorEffect
import com.a02.draw.onboarding.projector.ProjectorViewModel
import com.a02.draw.onboarding.topics.TopicsAction
import com.a02.draw.onboarding.topics.TopicsEffect
import com.a02.draw.onboarding.topics.TopicsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingScreenViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `projector continue navigates to lightbox`() = runTest {
        val viewModel = ProjectorViewModel()

        viewModel.onAction(ProjectorAction.Continue)
        advanceUntilIdle()

        assertEquals(ProjectorEffect.NavigateNext, viewModel.effects.first())
    }

    @Test
    fun `lightbox continue navigates to lessons`() = runTest {
        val viewModel = LightboxViewModel()

        viewModel.onAction(LightboxAction.Continue)
        advanceUntilIdle()

        assertEquals(LightboxEffect.NavigateNext, viewModel.effects.first())
    }

    @Test
    fun `lessons continue navigates to topics`() = runTest {
        val viewModel = LessonsViewModel()

        viewModel.onAction(LessonsAction.Continue)
        advanceUntilIdle()

        assertEquals(LessonsEffect.NavigateNext, viewModel.effects.first())
    }

    @Test
    fun `topics loads catalog shares selection and navigates next`() = runTest {
        val store = DefaultOnboardingSessionStore()
        val viewModel = TopicsViewModel(
            GetArCatalogUseCase(FakeContentRepository()),
            store,
        )

        advanceUntilIdle()
        viewModel.onAction(TopicsAction.ToggleTopic("animal"))
        viewModel.onAction(TopicsAction.Continue)
        advanceUntilIdle()

        assertEquals(listOf("animal"), viewModel.state.value.topics.map { it.id })
        assertEquals(setOf("animal"), store.state.value.selectedTopicIds)
        assertEquals(TopicsEffect.NavigateNext, viewModel.effects.first())
    }

    @Test
    fun `topics requires a selection before navigating next`() = runTest {
        val viewModel = TopicsViewModel(
            GetArCatalogUseCase(FakeContentRepository()),
            DefaultOnboardingSessionStore(),
        )
        advanceUntilIdle()

        viewModel.onAction(TopicsAction.Continue)

        assertEquals(TopicsEffect.ShowSelectionRequired, viewModel.effects.first())
    }

    @Test
    fun `onboarding store limits topic selection and shares selected topic`() {
        val store = DefaultOnboardingSessionStore()
        store.setTopics(
            (1..5).map { DrawingTopic("topic-$it", "Topic $it", ContentImage()) },
        )

        (1..5).forEach { store.toggleTopic("topic-$it") }

        assertEquals(setOf("topic-3", "topic-4", "topic-5"), store.state.value.selectedTopicIds)
        assertEquals("topic-3", store.state.value.selectedTopic?.id)
    }

    @Test
    fun `preparing persists completion and opens main`() = runTest {
        val preferences = MutableStateFlow(AppPreferences())
        val viewModel = PreparingViewModel(
            UpdateAppPreferencesUseCase(FakePreferencesRepository(preferences)),
            DefaultOnboardingSessionStore(),
        )

        advanceTimeBy(6_000)
        runCurrent()

        assertTrue(preferences.value.onboardingCompleted)
        assertEquals(PreparingEffect.OpenMain, viewModel.effects.first())
    }

    @Test
    fun `preparing fills each progress bar sequentially over two seconds`() = runTest {
        val viewModel = PreparingViewModel(
            UpdateAppPreferencesUseCase(FakePreferencesRepository(MutableStateFlow(AppPreferences()))),
            DefaultOnboardingSessionStore(),
        )

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(50, viewModel.state.value.favoritesProgress)
        assertEquals(0, viewModel.state.value.referencesProgress)
        assertEquals(0, viewModel.state.value.toolsProgress)

        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(100, viewModel.state.value.favoritesProgress)
        assertEquals(50, viewModel.state.value.referencesProgress)
        assertEquals(0, viewModel.state.value.toolsProgress)

        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(100, viewModel.state.value.favoritesProgress)
        assertEquals(100, viewModel.state.value.referencesProgress)
        assertEquals(50, viewModel.state.value.toolsProgress)

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(100, viewModel.state.value.toolsProgress)
    }

    @Test
    fun `preparing stays on onboarding when completion cannot be persisted`() = runTest {
        val preferences = MutableStateFlow(AppPreferences())
        val viewModel = PreparingViewModel(
            UpdateAppPreferencesUseCase(FakePreferencesRepository(preferences, shouldFail = true)),
            DefaultOnboardingSessionStore(),
        )

        advanceTimeBy(6_000)
        runCurrent()

        assertEquals(false, preferences.value.onboardingCompleted)
        assertEquals(PreparingEffect.ShowMessage(R.string.generic_error), viewModel.effects.first())
    }

    private class FakePreferencesRepository(
        private val preferences: MutableStateFlow<AppPreferences>,
        private val shouldFail: Boolean = false,
    ) : AppPreferencesRepository {
        override fun observePreferences(): Flow<AppPreferences> = preferences
        override suspend fun setThemeMode(themeMode: ThemeMode): AppResult<Unit> =
            AppResult.Success(Unit)

        override suspend fun setLanguageTag(languageTag: String): AppResult<Unit> =
            AppResult.Success(Unit)

        override suspend fun setOnboardingCompleted(completed: Boolean): AppResult<Unit> {
            if (shouldFail) return AppResult.Failure(com.a02.draw.core.common.result.AppError.Database())
            preferences.value = preferences.value.copy(onboardingCompleted = completed)
            return AppResult.Success(Unit)
        }

        override suspend fun setPremium(isPremium: Boolean) = AppResult.Success(Unit)
        override suspend fun setRewardUnlockedItemIds(ids: Set<String>) = AppResult.Success(Unit)

        override suspend fun setFavoriteArtworkIds(ids: Set<String>): AppResult<Unit> =
            AppResult.Success(Unit)

        override suspend fun setLessonCompletedSteps(
            lessonId: String,
            completedSteps: Int,
        ): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun setMusicEnabled(enabled: Boolean): AppResult<Unit> =
            AppResult.Success(Unit)
    }

    private class FakeContentRepository : ArContentRepository {
        override suspend fun getCatalog(forceRefresh: Boolean): AppResult<ArCatalog> =
            AppResult.Success(
                ArCatalog(
                    topics = listOf(DrawingTopic("animal", "Animal", ContentImage())),
                    artworks = emptyList(),
                    lessons = emptyList(),
                    categories = emptyList(),
                    settings = emptyList(),
                ),
            )
    }
}
