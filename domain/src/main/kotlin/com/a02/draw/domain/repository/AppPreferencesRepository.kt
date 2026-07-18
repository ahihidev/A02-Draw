package com.a02.draw.domain.repository

import com.a02.draw.core.common.result.AppResult
import com.a02.draw.domain.model.AppPreferences
import com.a02.draw.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface AppPreferencesRepository {
    fun observePreferences(): Flow<AppPreferences>
    suspend fun setThemeMode(themeMode: ThemeMode): AppResult<Unit>
    suspend fun setOnboardingCompleted(completed: Boolean): AppResult<Unit>
}
