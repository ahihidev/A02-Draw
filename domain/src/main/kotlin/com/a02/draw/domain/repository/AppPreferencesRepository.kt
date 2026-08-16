package com.a02.draw.domain.repository

import com.a02.draw.core.common.result.AppResult
import com.a02.draw.domain.model.AppPreferences
import com.a02.draw.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface AppPreferencesRepository {
    fun observePreferences(): Flow<AppPreferences>
    suspend fun setThemeMode(themeMode: ThemeMode): AppResult<Unit>
    suspend fun setLanguageTag(languageTag: String): AppResult<Unit>
    suspend fun setOnboardingCompleted(completed: Boolean): AppResult<Unit>
    suspend fun setPremium(isPremium: Boolean): AppResult<Unit>
    suspend fun setRewardUnlockedItemIds(ids: Set<String>): AppResult<Unit>
    suspend fun setRewardPassExpiries(expiries: Map<String, Long>): AppResult<Unit> =
        AppResult.Success(Unit)
    suspend fun setFavoriteArtworkIds(ids: Set<String>): AppResult<Unit>
    suspend fun setLessonCompletedSteps(lessonId: String, completedSteps: Int): AppResult<Unit>
    suspend fun setMusicEnabled(enabled: Boolean): AppResult<Unit>
}
