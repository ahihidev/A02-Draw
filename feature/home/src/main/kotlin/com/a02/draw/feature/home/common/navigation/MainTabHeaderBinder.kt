package com.a02.draw.feature.home.common.navigation

import androidx.core.view.isVisible
import com.a02.draw.core.ui.extensions.applyStatusBarPadding
import com.a02.draw.core.ui.extensions.setDebouncedClickListener
import com.a02.draw.feature.home.databinding.ViewMainTabHeaderBinding

fun ViewMainTabHeaderBinding.bindMainTabHeader(onSearch: (() -> Unit)? = null) {
    root.applyStatusBarPadding(lightStatusBarIcons = false)
    searchCard.isVisible = onSearch != null
    if (onSearch == null) {
        searchCard.setOnClickListener(null)
        searchCard.isClickable = false
        searchCard.isFocusable = false
    } else {
        searchCard.setDebouncedClickListener { onSearch() }
    }
}
