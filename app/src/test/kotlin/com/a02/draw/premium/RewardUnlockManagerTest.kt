package com.a02.draw.premium

import com.a02.draw.MainDispatcherRule
import com.a02.draw.core.common.result.AppResult
import com.a02.draw.core.ui.ads.RewardContentKey
import com.a02.draw.domain.model.AppPreferences
import com.a02.draw.domain.model.ThemeMode
import com.a02.draw.domain.repository.AppPreferencesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RewardUnlockManagerTest {
    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(dispatcher)

    @Test
    fun `artwork is permanent lesson is timed and emoji is per create`() =
        runTest(dispatcher) {
            val clock = FakeRewardTimeSource(currentTime = 1_000_000L)
            val repository = FakePreferencesRepository()
            val manager = RewardUnlockManager(repository, clock)
            runCurrent()

            val artwork = RewardContentKey.Artwork("owl")
            val lesson = RewardContentKey.Lesson("lesson-1", "animals")
            val emoji = RewardContentKey.Emoji("😀")
            manager.grant(artwork)
            manager.grant(lesson)
            manager.grant(emoji)
            runCurrent()

            assertTrue(manager.accessState.value.hasAccess(artwork))
            assertTrue(manager.accessState.value.hasAccess(lesson))
            assertFalse(manager.accessState.value.hasAccess(emoji))
            assertEquals(
                clock.currentTime + RewardUnlockManager.LESSON_PASS_DURATION_MILLIS,
                manager.accessState.value.activePassExpiries[lesson.passKey],
            )
            assertFalse("emoji-mix" in manager.accessState.value.activePassExpiries)
            assertTrue(artwork.legacyItemKey in repository.preferences.value.rewardUnlockedItemIds)
            manager.close()
        }

    @Test
    fun `timed passes survive recreation and expire without interrupting permanent items`() =
        runTest(dispatcher) {
            val clock = FakeRewardTimeSource(currentTime = 2_000_000L)
            val repository = FakePreferencesRepository()
            val first = RewardUnlockManager(repository, clock)
            runCurrent()
            val artwork = RewardContentKey.Artwork("cat")
            val lesson = RewardContentKey.Lesson("lesson-2", "pets")
            first.grant(artwork)
            first.grant(lesson)
            runCurrent()

            val restored = RewardUnlockManager(repository, clock)
            runCurrent()
            assertTrue(restored.accessState.value.hasAccess(artwork))
            assertTrue(restored.accessState.value.hasAccess(lesson))

            clock.currentTime += RewardUnlockManager.LESSON_PASS_DURATION_MILLIS + 1L
            advanceTimeBy(RewardUnlockManager.LESSON_PASS_DURATION_MILLIS + 1L)
            runCurrent()

            assertTrue(restored.accessState.value.hasAccess(artwork))
            assertFalse(restored.accessState.value.hasAccess(lesson))
            first.close()
            restored.close()
        }

    @Test
    fun `legacy emoji access never bypasses the next create reward`() =
        runTest(dispatcher) {
            val clock = FakeRewardTimeSource(currentTime = 3_000_000L)
            val repository = FakePreferencesRepository(
                AppPreferences(
                    rewardUnlockedItemIds = setOf("lesson:legacy", "emoji:⭐"),
                    rewardPassExpiries = mapOf("emoji-mix" to clock.currentTime + 900_000L),
                ),
            )
            val manager = RewardUnlockManager(repository, clock)
            runCurrent()

            assertTrue(
                manager.accessState.value.hasAccess(
                    RewardContentKey.Lesson("legacy", "old-category"),
                ),
            )
            assertFalse(manager.accessState.value.hasAccess(RewardContentKey.Emoji("⭐")))
            manager.close()
        }

    private class FakeRewardTimeSource(
        var currentTime: Long = 0L,
        var elapsedTime: Long = 0L,
    ) : RewardTimeSource {
        override fun currentTimeMillis(): Long = currentTime
        override fun elapsedRealtimeMillis(): Long = elapsedTime
    }

    private class FakePreferencesRepository(
        initial: AppPreferences = AppPreferences(),
    ) : AppPreferencesRepository {
        val preferences = MutableStateFlow(initial)

        override fun observePreferences(): Flow<AppPreferences> = preferences
        override suspend fun setThemeMode(themeMode: ThemeMode) = AppResult.Success(Unit)
        override suspend fun setLanguageTag(languageTag: String) = AppResult.Success(Unit)
        override suspend fun setOnboardingCompleted(completed: Boolean) = AppResult.Success(Unit)
        override suspend fun setPremium(isPremium: Boolean) = AppResult.Success(Unit)
        override suspend fun setFavoriteArtworkIds(ids: Set<String>) = AppResult.Success(Unit)
        override suspend fun setLessonCompletedSteps(lessonId: String, completedSteps: Int) =
            AppResult.Success(Unit)

        override suspend fun setMusicEnabled(enabled: Boolean) = AppResult.Success(Unit)

        override suspend fun setRewardUnlockedItemIds(ids: Set<String>): AppResult<Unit> {
            preferences.value = preferences.value.copy(rewardUnlockedItemIds = ids)
            return AppResult.Success(Unit)
        }

        override suspend fun setRewardPassExpiries(
            expiries: Map<String, Long>,
        ): AppResult<Unit> {
            preferences.value = preferences.value.copy(rewardPassExpiries = expiries)
            return AppResult.Success(Unit)
        }
    }
}
