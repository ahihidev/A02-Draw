package com.a02.draw.domain.model

data class AppPreferences(
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val languageTag: String? = null,
    val onboardingCompleted: Boolean = false,
    val isPremium: Boolean = false,
    val rewardUnlockedItemIds: Set<String> = emptySet(),
    val rewardPassExpiries: Map<String, Long> = emptyMap(),
    val favoriteArtworkIds: Set<String> = emptySet(),
    val lessonCompletedSteps: Map<String, Int> = emptyMap(),
    val musicEnabled: Boolean = true,
)

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}
