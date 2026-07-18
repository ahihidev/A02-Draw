package com.a02.draw.domain.model

data class AppPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val onboardingCompleted: Boolean = false,
)

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}
