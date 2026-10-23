package com.a02.draw.feature.home.screen.webbrowser

import com.a02.draw.core.ui.base.UiEffect
import com.a02.draw.core.ui.base.UiState

data class WebBrowserUiState(
    val query: String = DEFAULT_QUERY,
    val isImporting: Boolean = false,
    val importError: String? = null,
) : UiState {
    companion object {
        const val DEFAULT_QUERY = "Photo"
    }
}

sealed interface WebBrowserAction {
    data class Search(val query: String) : WebBrowserAction
    data class ImageSelected(val url: String) : WebBrowserAction
    data object ClearError : WebBrowserAction
}

sealed interface WebBrowserEffect : UiEffect {
    data class LoadUrl(val url: String) : WebBrowserEffect
    data object NavigateToDrawingMode : WebBrowserEffect
}

object DrawingImageSearchUrlBuilder {
    private const val BASE_URL = "https://www.google.com/search?tbm=isch&q="

    fun build(query: String): String? {
        val clean = query.trim()
        if (clean.isEmpty()) return null
        val encoded = java.net.URLEncoder.encode("$clean for drawing", Charsets.UTF_8.name())
        return BASE_URL + encoded
    }

    fun isHttpsImageUrl(url: String): Boolean = runCatching {
        val parsed = java.net.URI(url)
        parsed.scheme.equals("https", ignoreCase = true) && !parsed.host.isNullOrBlank()
    }.getOrDefault(false)
}
