package com.a02.draw

import com.a02.draw.core.common.result.AppResult
import com.a02.draw.core.ui.ads.PremiumEntitlementController
import com.a02.draw.domain.model.AppPreferences
import com.a02.draw.domain.model.ThemeMode
import com.a02.draw.domain.repository.AppPreferencesRepository
import com.a02.draw.domain.usecase.ObserveAppPreferencesUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    fun `non premium always routes to language`() = runTest {
        val preferences = MutableStateFlow(AppPreferences())
        val viewModel = StartupViewModel(
            ObserveAppPreferencesUseCase(FakePreferencesRepository(preferences)),
            FakePremiumEntitlement(isPremium = false, isInitialized = true),
        )
        advanceUntilIdle()
        assertEquals(StartupDestination.LANGUAGE, viewModel.destination.value)

        preferences.value = AppPreferences(onboardingCompleted = true)
        advanceUntilIdle()
        assertEquals(StartupDestination.LANGUAGE, viewModel.destination.value)
    }

    @Test
    fun `premium skips language and respects onboarding completion`() = runTest {
        val preferences = MutableStateFlow(AppPreferences())
        val viewModel = StartupViewModel(
            ObserveAppPreferencesUseCase(FakePreferencesRepository(preferences)),
            FakePremiumEntitlement(isPremium = true, isInitialized = true),
        )
        advanceUntilIdle()
        assertEquals(StartupDestination.ONBOARDING, viewModel.destination.value)

        preferences.value = AppPreferences(onboardingCompleted = true)
        advanceUntilIdle()
        assertEquals(StartupDestination.MAIN, viewModel.destination.value)
    }

    @Test
    fun `startup routes from verified entitlement instead of raw preference flag`() = runTest {
        val preferences = MutableStateFlow(
            AppPreferences(
                onboardingCompleted = true,
                isPremium = true,
            ),
        )
        val viewModel = StartupViewModel(
            ObserveAppPreferencesUseCase(FakePreferencesRepository(preferences)),
            FakePremiumEntitlement(isPremium = false, isInitialized = true),
        )

        advanceUntilIdle()

        assertEquals(StartupDestination.LANGUAGE, viewModel.destination.value)
    }

    @Test
    fun `startup waits for application premium initialization`() = runTest {
        val preferences = MutableStateFlow(AppPreferences(onboardingCompleted = true))
        val entitlement = FakePremiumEntitlement(isPremium = false, isInitialized = false)
        val viewModel = StartupViewModel(
            ObserveAppPreferencesUseCase(FakePreferencesRepository(preferences)),
            entitlement,
        )
        advanceUntilIdle()
        assertEquals(null, viewModel.destination.value)

        entitlement.completeInitialization(isPremium = false)
        advanceUntilIdle()

        assertEquals(StartupDestination.LANGUAGE, viewModel.destination.value)
    }

    private class FakePremiumEntitlement(
        isPremium: Boolean,
        isInitialized: Boolean,
    ) : PremiumEntitlementController {
        private val premiumState = MutableStateFlow(isPremium)
        private val initializedState = MutableStateFlow(isInitialized)

        override val isPremium: StateFlow<Boolean> = premiumState
        override val isInitialized: StateFlow<Boolean> = initializedState

        override fun setPremiumOwned(isOwned: Boolean) {
            premiumState.value = isOwned
            initializedState.value = true
        }

        override fun completeInitialization() {
            initializedState.value = true
        }

        fun completeInitialization(isPremium: Boolean) {
            setPremiumOwned(isPremium)
        }
    }

    private class FakePreferencesRepository(
        private val preferences: MutableStateFlow<AppPreferences>,
    ) : AppPreferencesRepository {
        override fun observePreferences(): Flow<AppPreferences> = preferences
        override suspend fun setThemeMode(themeMode: ThemeMode): AppResult<Unit> =
            AppResult.Success(Unit)

        override suspend fun setLanguageTag(languageTag: String): AppResult<Unit> =
            AppResult.Success(Unit)

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
