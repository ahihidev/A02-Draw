package com.a02.draw.premium

import com.a02.draw.MainDispatcherRule
import com.a02.draw.core.common.result.AppResult
import com.a02.draw.domain.model.AppPreferences
import com.a02.draw.domain.model.ThemeMode
import com.a02.draw.domain.repository.AppPreferencesRepository
import com.kiro.sdk.KiroSdk
import com.kiro.sdk.ads.KiroAds
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PremiumEntitlementManagerTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val ads = mockk<KiroAds>(relaxed = true)

    @Before
    fun setUp() {
        mockkObject(KiroSdk)
        every { KiroSdk.ads } returns ads
        every { KiroSdk.setAdsDisabled(any()) } just Runs
    }

    @After
    fun tearDown() {
        unmockkObject(KiroSdk)
    }

    @Test
    fun `cached premium disables ads while Play resolution is completing`() = runTest {
        val manager = PremiumEntitlementManager(
            FakePreferencesRepository(AppPreferences(isPremium = true)),
        )

        advanceUntilIdle()

        assertTrue(manager.isPremium.value)
        assertFalse(manager.isInitialized.value)
        verify { KiroSdk.setAdsDisabled(true) }
        verify { ads.hideAllActiveAds() }

        manager.completeInitialization()

        assertTrue(manager.isInitialized.value)
        assertTrue(manager.isPremium.value)
    }

    @Test
    fun `temporary Play failure preserves cached premium entitlement`() = runTest {
        val manager = PremiumEntitlementManager(
            FakePreferencesRepository(AppPreferences(isPremium = true)),
        )

        manager.completeInitialization()
        advanceUntilIdle()

        assertTrue(manager.isInitialized.value)
        assertTrue(manager.isPremium.value)
    }

    @Test
    fun `authoritative Play result overrides stale cached premium`() = runTest {
        val repository = FakePreferencesRepository(AppPreferences(isPremium = true))
        val manager = PremiumEntitlementManager(repository)

        manager.setPremiumOwned(false)
        advanceUntilIdle()

        assertTrue(manager.isInitialized.value)
        assertFalse(manager.isPremium.value)
        assertFalse(repository.preferences.value.isPremium)
    }

    private class FakePreferencesRepository(initial: AppPreferences) : AppPreferencesRepository {
        val preferences = MutableStateFlow(initial)

        override fun observePreferences(): Flow<AppPreferences> = preferences

        override suspend fun setPremium(isPremium: Boolean): AppResult<Unit> {
            preferences.value = preferences.value.copy(isPremium = isPremium)
            return AppResult.Success(Unit)
        }

        override suspend fun setThemeMode(themeMode: ThemeMode) = AppResult.Success(Unit)
        override suspend fun setLanguageTag(languageTag: String) = AppResult.Success(Unit)
        override suspend fun setOnboardingCompleted(completed: Boolean) = AppResult.Success(Unit)
        override suspend fun setRewardUnlockedItemIds(ids: Set<String>) = AppResult.Success(Unit)
        override suspend fun setFavoriteArtworkIds(ids: Set<String>) = AppResult.Success(Unit)
        override suspend fun setLessonCompletedSteps(
            lessonId: String,
            completedSteps: Int,
        ) = AppResult.Success(Unit)

        override suspend fun setMusicEnabled(enabled: Boolean) = AppResult.Success(Unit)
    }
}
