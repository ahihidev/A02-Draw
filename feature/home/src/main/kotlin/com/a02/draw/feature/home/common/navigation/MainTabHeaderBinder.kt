package com.a02.draw.feature.home.common.navigation

import androidx.core.view.isVisible
import com.a02.draw.core.ui.extensions.applyStatusBarPadding
import com.a02.draw.core.ui.extensions.setDebouncedClickListener
import com.a02.draw.feature.home.databinding.ViewMainTabHeaderBinding

fun ViewMainTabHeaderBinding.bindMainTabHeader(
    onSearch: (() -> Unit)? = null,
    onPremium: (() -> Unit)? = null,
) {
    root.applyStatusBarPadding(lightStatusBarIcons = false)
    searchCard.isVisible = onSearch != null
    premiumButton.isVisible = onPremium != null
    if (onPremium == null) {
        premiumButton.setOnClickListener(null)
        premiumButton.isClickable = false
        premiumButton.isFocusable = false
    } else {
        premiumButton.isClickable = true
        premiumButton.isFocusable = true
        premiumButton.setDebouncedClickListener { onPremium() }
    }
    if (onSearch == null) {
        searchCard.setOnClickListener(null)
        searchCard.isClickable = false
        searchCard.isFocusable = false
    } else {
        searchCard.setDebouncedClickListener { onSearch() }
    }
}

fun ViewMainTabHeaderBinding.renderPremiumShortcut(isPremiumOwned: Boolean) {
    premiumButton.isVisible = premiumButton.isClickable && !isPremiumOwned
}
