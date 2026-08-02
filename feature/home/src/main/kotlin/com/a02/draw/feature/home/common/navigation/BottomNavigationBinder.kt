package com.a02.draw.feature.home.common.navigation

import android.graphics.Typeface
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import com.a02.draw.core.ui.extensions.setDebouncedClickListener
import com.a02.draw.feature.home.R
import com.a02.draw.feature.home.common.model.BottomDestination
import com.a02.draw.feature.home.databinding.ViewBottomNavigationBinding

fun ViewBottomNavigationBinding.bindBottomNavigation(
    selected: BottomDestination,
    onDestinationSelected: (BottomDestination) -> Unit,
) {
    navHome.setDebouncedClickListener { onDestinationSelected(BottomDestination.HOME) }
    navLearn.setDebouncedClickListener { onDestinationSelected(BottomDestination.LEARN) }
    navProfile.setDebouncedClickListener { onDestinationSelected(BottomDestination.PROFILE) }
    navSettings.setDebouncedClickListener { onDestinationSelected(BottomDestination.SETTINGS) }
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
    if (destination == BottomDestination.HOME) {
        popBackStack(R.id.mainHomeFragment, false)
        return
    }
    navigate(
        destinationId,
        null,
        NavOptions.Builder()
            .setLaunchSingleTop(true)
            .setPopUpTo(R.id.mainHomeFragment, false)
            .build(),
    )
}
