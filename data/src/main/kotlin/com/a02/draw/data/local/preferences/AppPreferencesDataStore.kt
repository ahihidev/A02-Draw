package com.a02.draw.data.local.preferences

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.a02.draw.core.common.coroutines.DispatcherProvider
import com.a02.draw.core.common.result.AppError
import com.a02.draw.core.common.result.AppResult
import com.a02.draw.domain.model.AppPreferences
import com.a02.draw.domain.model.ThemeMode
import com.a02.draw.domain.repository.AppPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appPreferencesDataStore by preferencesDataStore(name = "app_preferences")

@Singleton
class AppPreferencesDataStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) : AppPreferencesRepository {
    override fun observePreferences(): Flow<AppPreferences> = context.appPreferencesDataStore.data
        .catch { throwable ->
            if (throwable is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
            else throw throwable
        }
        .map(::toDomain)
        .flowOn(dispatchers.io)

    override suspend fun setThemeMode(themeMode: ThemeMode): AppResult<Unit> = update {
        it[Keys.THEME_MODE] = themeMode.name
    }

    override suspend fun setLanguageTag(languageTag: String): AppResult<Unit> = update {
        it[Keys.LANGUAGE_TAG] = languageTag
    }

    override suspend fun setOnboardingCompleted(completed: Boolean): AppResult<Unit> = update {
        it[Keys.ONBOARDING_COMPLETED] = completed
    }

    override suspend fun setPremium(isPremium: Boolean): AppResult<Unit> = update {
        it[Keys.IS_PREMIUM] = isPremium
    }

    override suspend fun setRewardUnlockedItemIds(ids: Set<String>): AppResult<Unit> = update {
        it[Keys.REWARD_UNLOCKED_ITEM_IDS] = ids
    }

    override suspend fun setRewardPassExpiries(expiries: Map<String, Long>): AppResult<Unit> =
        update {
            it[Keys.REWARD_PASS_EXPIRIES] = encodeLongMap(expiries)
        }

    override suspend fun setFavoriteArtworkIds(ids: Set<String>): AppResult<Unit> = update {
        it[Keys.FAVORITE_ARTWORK_IDS] = ids
    }

    override suspend fun setLessonCompletedSteps(
        lessonId: String,
        completedSteps: Int,
    ): AppResult<Unit> = update { preferences ->
        val progress = decodeLessonProgress(preferences[Keys.LESSON_COMPLETED_STEPS].orEmpty())
            .toMutableMap()
        if (completedSteps <= 0) progress.remove(lessonId)
        else progress[lessonId] = maxOf(progress[lessonId] ?: 0, completedSteps)
        preferences[Keys.LESSON_COMPLETED_STEPS] = encodeLessonProgress(progress)
    }

    override suspend fun setMusicEnabled(enabled: Boolean): AppResult<Unit> = update {
        it[Keys.MUSIC_ENABLED] = enabled
    }

    private suspend fun update(
        transform: suspend (androidx.datastore.preferences.core.MutablePreferences) -> Unit,
    ): AppResult<Unit> = withContext(dispatchers.io) {
        try {
            context.appPreferencesDataStore.edit(transform)
            AppResult.Success(Unit)
        } catch (throwable: CancellationException) {
            throw throwable
        } catch (throwable: Throwable) {
            AppResult.Failure(AppError.Database(throwable.message))
        }
    }

    private fun toDomain(preferences: Preferences): AppPreferences {
        val theme = preferences[Keys.THEME_MODE]
            ?.let { value -> ThemeMode.entries.firstOrNull { it.name == value } }
            ?: ThemeMode.SYSTEM
        return AppPreferences(
            themeMode = theme,
            languageTag = preferences[Keys.LANGUAGE_TAG],
            onboardingCompleted = preferences[Keys.ONBOARDING_COMPLETED] ?: false,
            isPremium = preferences[Keys.IS_PREMIUM] ?: false,
            rewardUnlockedItemIds = preferences[Keys.REWARD_UNLOCKED_ITEM_IDS].orEmpty(),
            rewardPassExpiries = decodeLongMap(
                preferences[Keys.REWARD_PASS_EXPIRIES].orEmpty(),
            ),
            favoriteArtworkIds = preferences[Keys.FAVORITE_ARTWORK_IDS].orEmpty(),
            lessonCompletedSteps = decodeLessonProgress(
                preferences[Keys.LESSON_COMPLETED_STEPS].orEmpty(),
            ),
            musicEnabled = preferences[Keys.MUSIC_ENABLED] ?: true,
        )
    }

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE_TAG = stringPreferencesKey("language_tag")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val IS_PREMIUM = booleanPreferencesKey("is_premium")
        val REWARD_UNLOCKED_ITEM_IDS = stringSetPreferencesKey("reward_unlocked_item_ids")
        val REWARD_PASS_EXPIRIES = stringSetPreferencesKey("reward_pass_expiries")
        val FAVORITE_ARTWORK_IDS = stringSetPreferencesKey("favorite_artwork_ids")
        val LESSON_COMPLETED_STEPS = stringSetPreferencesKey("lesson_completed_steps")
        val MUSIC_ENABLED = booleanPreferencesKey("music_enabled")
    }
}

private fun encodeLessonProgress(progress: Map<String, Int>): Set<String> =
    progress.mapTo(mutableSetOf()) {
        "${it.key}=${it.value}"
    }

private fun decodeLessonProgress(values: Set<String>): Map<String, Int> = buildMap {
    values.forEach { value ->
        val separator = value.lastIndexOf('=')
        if (separator <= 0) return@forEach
        val steps = value.substring(separator + 1).toIntOrNull() ?: return@forEach
        if (steps > 0) put(value.substring(0, separator), steps)
    }
}

private fun encodeLongMap(values: Map<String, Long>): Set<String> =
    values.mapTo(mutableSetOf()) { (key, value) -> "$key=$value" }

private fun decodeLongMap(values: Set<String>): Map<String, Long> = buildMap {
    values.forEach { value ->
        val separator = value.lastIndexOf('=')
        if (separator <= 0) return@forEach
        val timestamp = value.substring(separator + 1).toLongOrNull() ?: return@forEach
        if (timestamp > 0L) put(value.substring(0, separator), timestamp)
    }
}
