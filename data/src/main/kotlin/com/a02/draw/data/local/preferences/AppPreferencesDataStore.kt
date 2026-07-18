package com.a02.draw.data.local.preferences

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.a02.draw.core.common.coroutines.DispatcherProvider
import com.a02.draw.core.common.result.AppError
import com.a02.draw.core.common.result.AppResult
import com.a02.draw.domain.model.AppPreferences
import com.a02.draw.domain.model.ThemeMode
import com.a02.draw.domain.repository.AppPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

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

    override suspend fun setOnboardingCompleted(completed: Boolean): AppResult<Unit> = update {
        it[Keys.ONBOARDING_COMPLETED] = completed
    }

    private suspend fun update(
        transform: suspend (androidx.datastore.preferences.core.MutablePreferences) -> Unit,
    ): AppResult<Unit> = withContext(dispatchers.io) {
        try {
            context.appPreferencesDataStore.edit(transform)
            AppResult.Success(Unit)
        } catch (throwable: IOException) {
            AppResult.Failure(AppError.Database(throwable.message))
        }
    }

    private fun toDomain(preferences: Preferences): AppPreferences {
        val theme = preferences[Keys.THEME_MODE]
            ?.let { value -> ThemeMode.entries.firstOrNull { it.name == value } }
            ?: ThemeMode.SYSTEM
        return AppPreferences(
            themeMode = theme,
            onboardingCompleted = preferences[Keys.ONBOARDING_COMPLETED] ?: false,
        )
    }

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }
}
