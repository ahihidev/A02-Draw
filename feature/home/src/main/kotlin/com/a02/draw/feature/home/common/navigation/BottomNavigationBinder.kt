package com.a02.draw.feature.home.common.navigation

import android.graphics.Typeface
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import com.a02.draw.feature.home.R
import com.a02.draw.feature.home.common.model.BottomDestination
import com.a02.draw.feature.home.databinding.ViewBottomNavigationBinding

fun ViewBottomNavigationBinding.bindBottomNavigation(
    selected: BottomDestination,
    onDestinationSelected: (BottomDestination) -> Unit,
) {
    var lastClickAt = 0L
    fun select(destination: BottomDestination) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastClickAt < NAVIGATION_DEBOUNCE_MILLIS) return
        lastClickAt = now
        onDestinationSelected(destination)
    }

    navHome.setOnClickListener { select(BottomDestination.HOME) }
    navLearn.setOnClickListener { select(BottomDestination.LEARN) }
    navProfile.setOnClickListener { select(BottomDestination.PROFILE) }
    navSettings.setOnClickListener { select(BottomDestination.SETTINGS) }
    renderBottomNavigation(selected)
}

fun ViewBottomNavigationBinding.renderBottomNavigation(selected: BottomDestination) {
    val selectedColor = ContextCompat.getColor(root.context, R.color.home_purple)
    val defaultColor = ContextCompat.getColor(root.context, R.color.home_muted)
    listOf(
        BottomDestination.HOME to Triple(navHome, navHomeIcon, navHomeLabel),
        BottomDestination.LEARN to Triple(navLearn, navLearnIcon, navLearnLabel),
        BottomDestination.PROFILE to Triple(navProfile, navProfileIcon, navProfileLabel),
        BottomDestination.SETTINGS to Triple(navSettings, navSettingsIcon, navSettingsLabel),
    ).forEach { (destination, views) ->
        val isSelected = destination == selected
        val color = if (isSelected) selectedColor else defaultColor
        views.first.isSelected = isSelected
        views.second.isSelected = isSelected
        views.third.isSelected = isSelected
        views.third.setTextColor(color)
        views.third.setTypeface(null, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
        views.first.animate().cancel()
        views.second.animate().cancel()
        views.third.animate().cancel()
        views.first.scaleX = 1f
        views.first.scaleY = 1f
        views.second.translationY = 0f
        views.second.scaleX = 1f
        views.second.scaleY = 1f
        views.third.alpha = 1f
    }
}

fun NavController.navigateBottom(destination: BottomDestination) {
    val destinationId = when (destination) {
        BottomDestination.HOME -> R.id.mainHomeFragment
        BottomDestination.LEARN -> R.id.learnFragment
        BottomDestination.PROFILE -> R.id.profileFavoriteFragment
        BottomDestination.SETTINGS -> R.id.settingsFragment
    }
    if (currentDestination?.id == destinationId) return
    navigate(
        destinationId,
        null,
        NavOptions.Builder()
            .setLaunchSingleTop(true)
            .setRestoreState(true)
            .setPopUpTo(graph.startDestinationId, false, true)
            .build(),
    )
}

private const val NAVIGATION_DEBOUNCE_MILLIS = 300L
