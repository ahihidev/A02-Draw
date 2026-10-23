package com.a02.draw.domain.usecase

import com.a02.draw.core.common.result.AppResult
import com.a02.draw.domain.repository.AppPreferencesRepository
import javax.inject.Inject

class UpdateAppPreferencesUseCase @Inject constructor(
    private val repository: AppPreferencesRepository,
) {
    suspend fun setOnboardingCompleted(completed: Boolean): AppResult<Unit> =
        repository.setOnboardingCompleted(completed)

    suspend fun setLanguageTag(languageTag: String): AppResult<Unit> =
        repository.setLanguageTag(languageTag)

    suspend fun setPremium(isPremium: Boolean): AppResult<Unit> =
        repository.setPremium(isPremium)

    suspend fun setRewardUnlockedItemIds(ids: Set<String>): AppResult<Unit> =
        repository.setRewardUnlockedItemIds(ids)

    suspend fun setRewardPassExpiries(expiries: Map<String, Long>): AppResult<Unit> =
        repository.setRewardPassExpiries(expiries)

    suspend fun setFavorites(ids: Set<String>): AppResult<Unit> =
        repository.setFavoriteArtworkIds(ids)

    suspend fun setLessonCompletedSteps(lessonId: String, completedSteps: Int): AppResult<Unit> =
        repository.setLessonCompletedSteps(lessonId, completedSteps)

    suspend fun setMusicEnabled(enabled: Boolean): AppResult<Unit> =
        repository.setMusicEnabled(enabled)
}
