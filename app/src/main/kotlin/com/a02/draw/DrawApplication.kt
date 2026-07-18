package com.a02.draw

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.a02.draw.domain.model.ThemeMode
import com.a02.draw.domain.repository.AppPreferencesRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@HiltAndroidApp
class DrawApplication : Application() {
    @Inject lateinit var preferencesRepository: AppPreferencesRepository

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            preferencesRepository.observePreferences()
                .map { it.themeMode }
                .distinctUntilChanged()
                .collect(::applyTheme)
        }
    }

    private fun applyTheme(themeMode: ThemeMode) {
        val nightMode = when (themeMode) {
            ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }
}
