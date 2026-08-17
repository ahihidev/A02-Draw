package com.a02.draw.feature.home

import androidx.lifecycle.SavedStateHandle
import com.a02.draw.feature.home.common.image.ImportedReference
import com.a02.draw.feature.home.common.image.ReferenceImageStore
import com.a02.draw.feature.home.common.session.DefaultDrawingSessionStore
import com.a02.draw.feature.home.screen.webbrowser.DrawingImageSearchUrlBuilder
import com.a02.draw.feature.home.screen.webbrowser.WebBrowserAction
import com.a02.draw.feature.home.screen.webbrowser.WebBrowserEffect
import com.a02.draw.feature.home.screen.webbrowser.WebBrowserViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WebBrowserTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `url builder adds drawing suffix and encodes query`() {
        assertEquals(
            "https://www.google.com/search?tbm=isch&q=cute+cat+for+drawing",
            DrawingImageSearchUrlBuilder.build(" cute cat "),
        )
        assertNull(DrawingImageSearchUrlBuilder.build("  "))
    }

    @Test
    fun `only valid HTTPS image URLs pass validation`() {
        assertTrue(DrawingImageSearchUrlBuilder.isHttpsImageUrl("https://images.example.com/cat.png"))
        assertFalse(DrawingImageSearchUrlBuilder.isHttpsImageUrl("http://example.com/cat.png"))
        assertFalse(DrawingImageSearchUrlBuilder.isHttpsImageUrl("data:image/png;base64,abc"))
    }

    @Test
    fun `default initial query is populated automatically without manual input`() {
        val viewModel = WebBrowserViewModel(
            SavedStateHandle(),
            FakeStore(),
            DefaultDrawingSessionStore(),
        )
        assertEquals("Anime", viewModel.state.value.query)
    }

    @Test
    fun `saved query is respected when present in savedStateHandle`() {
        val handle = SavedStateHandle(mapOf("web_browser.query" to "dragon"))
        val viewModel = WebBrowserViewModel(
            handle,
            FakeStore(),
            DefaultDrawingSessionStore(),
        )
        assertEquals("dragon", viewModel.state.value.query)
    }

    @Test
    fun `successful import updates drawing session and navigates`() = runTest {
        val drawing = DefaultDrawingSessionStore()
        val viewModel = WebBrowserViewModel(SavedStateHandle(), FakeStore(), drawing)
        viewModel.onAction(WebBrowserAction.Search("cat"))
        assertEquals(
            WebBrowserEffect.LoadUrl("https://www.google.com/search?tbm=isch&q=cat+for+drawing"),
            viewModel.effects.first(),
        )
        viewModel.onAction(WebBrowserAction.ImageSelected("https://example.com/cat.png"))
        advanceUntilIdle()
        assertEquals("content://test/web.png", drawing.state.value.pickedImageUri)
        assertEquals(WebBrowserEffect.NavigateToDrawingMode, viewModel.effects.first())
    }

    @Test
    fun `failed import exposes retryable error without navigating`() = runTest {
        val viewModel = WebBrowserViewModel(
            SavedStateHandle(),
            FakeStore(fail = true),
            DefaultDrawingSessionStore()
        )
        viewModel.onAction(WebBrowserAction.ImageSelected("https://example.com/cat.png"))
        advanceUntilIdle()
        assertEquals("import failed", viewModel.state.value.importError)
        assertFalse(viewModel.state.value.isImporting)
    }

    private class FakeStore(private val fail: Boolean = false) : ReferenceImageStore {
        override suspend fun importHttpsImage(url: String, filePrefix: String): ImportedReference {
            if (fail) error("import failed")
            return ImportedReference("content://test/web.png", displayName = "web.png")
        }

        override suspend fun createEmojiComposite(emojis: List<String>) = error("unused")
        override suspend fun copy(sourceUri: String, destinationUri: String) = Unit
    }
}
