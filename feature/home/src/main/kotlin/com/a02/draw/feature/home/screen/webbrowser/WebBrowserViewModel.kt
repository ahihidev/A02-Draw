package com.a02.draw.feature.home.screen.webbrowser

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.a02.draw.core.ui.base.BaseViewModel
import com.a02.draw.feature.home.common.image.ReferenceImageStore
import com.a02.draw.feature.home.common.model.DrawingMode
import com.a02.draw.feature.home.common.session.DrawingSession
import com.a02.draw.feature.home.common.session.DrawingSessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class WebBrowserViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val imageStore: ReferenceImageStore,
    private val drawingSession: DrawingSessionStore,
) : BaseViewModel<WebBrowserUiState, WebBrowserEffect>(
    WebBrowserUiState(query = savedStateHandle[KEY_QUERY] ?: ""),
) {
    fun onAction(action: WebBrowserAction) {
        when (action) {
            is WebBrowserAction.Search -> search(action.query)
            is WebBrowserAction.ImageSelected -> import(action.url)
            WebBrowserAction.ClearError -> updateState { copy(importError = null) }
        }
    }

    private fun search(query: String) {
        val url = DrawingImageSearchUrlBuilder.build(query)
        if (url == null) {
            updateState { copy(importError = "Enter something to search for") }
            return
        }
        val clean = query.trim()
        savedStateHandle[KEY_QUERY] = clean
        updateState { copy(query = clean, importError = null) }
        send(WebBrowserEffect.LoadUrl(url))
    }

    private fun import(url: String) {
        if (state.value.isImporting) return
        if (!DrawingImageSearchUrlBuilder.isHttpsImageUrl(url)) {
            updateState { copy(importError = "Only HTTPS images can be imported") }
            return
        }
        updateState { copy(isImporting = true, importError = null) }
        launchCatching(
            onError = { throwable ->
                updateState {
                    copy(
                        isImporting = false,
                        importError = throwable.message ?: "Could not import image"
                    )
                }
            },
        ) {
            val imported = imageStore.importHttpsImage(url, "web-reference")
            drawingSession.reset(
                DrawingSession(
                    referenceTitle = state.value.query.ifEmpty { "Web image" },
                    pickedImageUri = imported.uri,
                    mode = DrawingMode.CAMERA,
                ),
            )
            updateState { copy(isImporting = false) }
            sendEffect(WebBrowserEffect.NavigateToDrawingMode)
        }
    }

    private fun send(effect: WebBrowserEffect) {
        viewModelScope.launch { sendEffect(effect) }
    }

    private companion object {
        const val KEY_QUERY = "web_browser.query"
    }
}
