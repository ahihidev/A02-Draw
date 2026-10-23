package com.a02.draw.language

import androidx.lifecycle.SavedStateHandle
import com.a02.draw.MainDispatcherRule
import com.a02.draw.core.common.result.AppError
import com.a02.draw.core.common.result.AppResult
import com.a02.draw.domain.model.AppPreferences
import com.a02.draw.domain.model.ThemeMode
import com.a02.draw.domain.repository.AppPreferencesRepository
import com.a02.draw.domain.usecase.ObserveAppPreferencesUseCase
import com.a02.draw.domain.usecase.UpdateAppPreferencesUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LanguageViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `shows 15 supported languages and restores persisted selection`() = runTest {
        val repository = FakePreferencesRepository(
            AppPreferences(languageTag = "ja"),
        )

        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        assertEquals(15, viewModel.state.value.languages.size)
        assertEquals("en", viewModel.state.value.languages.last().option.languageTag)
        assertEquals("ja", viewModel.state.value.selectedLanguageTag)
        assertEquals(1, viewModel.state.value.languages.count { it.isSelected })
    }

    @Test
    fun `continue persists selection once and opens intro`() = runTest {
        val repository = FakePreferencesRepository(AppPreferences())
        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        viewModel.onAction(LanguageAction.Select("de"))
        viewModel.onAction(LanguageAction.Continue)
        viewModel.onAction(LanguageAction.Continue)
        advanceUntilIdle()

        assertEquals("de", repository.preferences.value.languageTag)
        assertEquals(1, repository.languageWrites)
        assertEquals(LanguageEffect.OpenIntro("de"), viewModel.effects.first())
    }

    @Test
    fun `save failure keeps screen interactive and reports error`() = runTest {
        val repository = FakePreferencesRepository(AppPreferences(), shouldFail = true)
        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        viewModel.onAction(LanguageAction.Continue)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isSaving)
        assertEquals(LanguageEffect.ShowSaveError, viewModel.effects.first())

        viewModel.onAction(LanguageAction.Select("fr"))
        assertEquals("fr", viewModel.state.value.selectedLanguageTag)
        assertTrue(viewModel.state.value.languages.single { it.option.languageTag == "fr" }.isSelected)
    }

    private fun createViewModel(repository: AppPreferencesRepository) = LanguageViewModel(
        updatePreferences = UpdateAppPreferencesUseCase(repository),
        observePreferences = ObserveAppPreferencesUseCase(repository),
        savedStateHandle = SavedStateHandle(),
    )

    private class FakePreferencesRepository(
        initialPreferences: AppPreferences,
        private val shouldFail: Boolean = false,
    ) : AppPreferencesRepository {
        val preferences = MutableStateFlow(initialPreferences)
        var languageWrites = 0

        override fun observePreferences(): Flow<AppPreferences> = preferences

        override suspend fun setThemeMode(themeMode: ThemeMode): AppResult<Unit> =
            AppResult.Success(Unit)

        override suspend fun setLanguageTag(languageTag: String): AppResult<Unit> {
            languageWrites += 1
            if (shouldFail) return AppResult.Failure(AppError.Database())
            preferences.value = preferences.value.copy(languageTag = languageTag)
            return AppResult.Success(Unit)
        }

        override suspend fun setOnboardingCompleted(completed: Boolean): AppResult<Unit> =
            AppResult.Success(Unit)

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
}
