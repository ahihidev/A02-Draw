package com.a02.draw.domain.model

data class AppPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val onboardingCompleted: Boolean = false,
    val favoriteArtworkIds: Set<String> = emptySet(),
    val lessonCompletedSteps: Map<String, Int> = emptyMap(),
    val musicEnabled: Boolean = true,
)

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}
