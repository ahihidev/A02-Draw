package com.a02.draw

import com.a02.draw.core.common.result.AppResult
import com.a02.draw.domain.model.AppPreferences
import com.a02.draw.domain.model.ThemeMode
import com.a02.draw.domain.repository.AppPreferencesRepository
import com.a02.draw.domain.usecase.ObserveAppPreferencesUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StartupViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `first launch routes to onboarding and later launch routes to main`() = runTest {
        val preferences = MutableStateFlow(AppPreferences())
        val viewModel = StartupViewModel(
            ObserveAppPreferencesUseCase(FakePreferencesRepository(preferences)),
        )
        advanceUntilIdle()
        assertEquals(StartupDestination.ONBOARDING, viewModel.destination.value)

        preferences.value = AppPreferences(onboardingCompleted = true)
        advanceUntilIdle()
        assertEquals(StartupDestination.MAIN, viewModel.destination.value)
    }

    private class FakePreferencesRepository(
        private val preferences: MutableStateFlow<AppPreferences>,
    ) : AppPreferencesRepository {
        override fun observePreferences(): Flow<AppPreferences> = preferences
        override suspend fun setThemeMode(themeMode: ThemeMode): AppResult<Unit> =
            AppResult.Success(Unit)

        override suspend fun setOnboardingCompleted(completed: Boolean): AppResult<Unit> =
            AppResult.Success(Unit)

        override suspend fun setFavoriteArtworkIds(ids: Set<String>): AppResult<Unit> =
            AppResult.Success(Unit)

        override suspend fun setLessonCompletedSteps(
            lessonId: String,
            completedSteps: Int,
        ): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun setMusicEnabled(enabled: Boolean): AppResult<Unit> =
            AppResult.Success(Unit)
    }
}
