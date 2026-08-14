package com.a02.draw.feature.home

import androidx.lifecycle.SavedStateHandle
import com.a02.draw.feature.home.common.image.ImportedReference
import com.a02.draw.feature.home.common.image.ReferenceImageStore
import com.a02.draw.feature.home.common.session.DefaultDrawingSessionStore
import com.a02.draw.feature.home.screen.emojimix.EmojiCompositeSpec
import com.a02.draw.feature.home.screen.emojimix.EmojiKitchenCatalog
import com.a02.draw.feature.home.screen.emojimix.EmojiKitchenPair
import com.a02.draw.feature.home.screen.emojimix.EmojiMixHomeFragment
import com.a02.draw.feature.home.screen.emojimix.EmojiMixMode
import com.a02.draw.feature.home.screen.emojimix.EmojiMixPickerAction
import com.a02.draw.feature.home.screen.emojimix.EmojiMixPickerEffect
import com.a02.draw.feature.home.screen.emojimix.EmojiMixPickerFragment
import com.a02.draw.feature.home.screen.emojimix.EmojiMixPickerViewModel
import com.a02.draw.feature.home.screen.emojimix.EmojiMixResultAction
import com.a02.draw.feature.home.screen.emojimix.EmojiMixResultEffect
import com.a02.draw.feature.home.screen.emojimix.EmojiMixResultViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EmojiMixTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `pair keys are normalized and compatible options are filtered`() {
        assertEquals(
            EmojiKitchenPair.normalize("😀", "🔥"),
            EmojiKitchenPair.normalize("🔥", "😀"),
        )
        val compatible = EmojiKitchenCatalog.compatibleWith("😀").map { it.emoji }
        assertTrue("🔥" in compatible)
        assertFalse("😀" in compatible)
        assertNotNull(EmojiKitchenCatalog.find("🔥", "😀"))
    }

    @Test
    fun `expanded catalog exposes all emojis to mix two and mix three`() {
        val originalEmojis = setOf(
            "😀", "😍", "😂", "🥰", "🤔", "😎", "🥳", "😭", "🔥", "🤖", "🐱", "🐶", "🐼",
            "🐸", "👻", "🚀", "🦄", "🌈", "🍕", "⭐", "❤️",
        )
        val available = EmojiKitchenCatalog.options.map { it.emoji }
        val addedEmojis = available.filterNot { it in originalEmojis }

        assertEquals(121, available.distinct().size)
        assertEquals(100, addedEmojis.size)
        assertTrue(
            addedEmojis.all { emoji ->
                EmojiKitchenCatalog.find("😀", emoji)?.resultUrl?.startsWith("https://") == true
            },
        )
        assertTrue(EmojiKitchenCatalog.compatibleWith("😀").map { it.emoji }
            .containsAll(addedEmojis))
    }

    @Test
    fun `picker limits slots and only creates when complete`() = runTest {
        val viewModel = EmojiMixPickerViewModel(
            SavedStateHandle(mapOf(EmojiMixHomeFragment.ARG_MODE to EmojiMixMode.MIX_3.name)),
        )
        listOf("😀", "😍", "😂", "🔥").forEach {
            viewModel.onAction(EmojiMixPickerAction.Toggle(it))
        }
        assertEquals(3, viewModel.state.value.selected.size)
        assertTrue(viewModel.state.value.canCreate)
        viewModel.onAction(EmojiMixPickerAction.Create)
        assertEquals(
            EmojiMixPickerEffect.OpenResult(EmojiMixMode.MIX_3, listOf("😀", "😍", "😂")),
            viewModel.effects.first()
        )
    }

    @Test
    fun `composite specification is stable 1024 square with triangular placements`() {
        assertEquals(1024, EmojiCompositeSpec.SIZE)
        assertEquals(listOf(350 to 410, 674 to 410, 512 to 720), EmojiCompositeSpec.centers)
        assertEquals(3, EmojiCompositeSpec.centers.distinct().size)
    }

    @Test
    fun `result succeeds and draw updates drawing session`() = runTest {
        val store = FakeReferenceImageStore()
        val drawing = DefaultDrawingSessionStore()
        val viewModel = EmojiMixResultViewModel(
            SavedStateHandle(
                mapOf(
                    EmojiMixHomeFragment.ARG_MODE to EmojiMixMode.MIX_2.name,
                    EmojiMixPickerFragment.ARG_INPUTS to arrayOf("😀", "🔥"),
                ),
            ),
            store,
            drawing,
        )
        advanceUntilIdle()
        assertNotNull(viewModel.state.value.result)
        viewModel.onAction(EmojiMixResultAction.Draw)
        assertEquals("content://test/result.png", drawing.state.value.pickedImageUri)
        assertEquals(EmojiMixResultEffect.NavigateToDrawingMode, viewModel.effects.first())
    }

    @Test
    fun `result retry recovers after network failure`() = runTest {
        val store = FakeReferenceImageStore(fail = true)
        val viewModel = EmojiMixResultViewModel(
            SavedStateHandle(
                mapOf(
                    EmojiMixHomeFragment.ARG_MODE to EmojiMixMode.MIX_2.name,
                    EmojiMixPickerFragment.ARG_INPUTS to arrayOf("😀", "🔥"),
                ),
            ),
            store,
            DefaultDrawingSessionStore(),
        )
        advanceUntilIdle()
        assertNotNull(viewModel.state.value.errorMessage)
        store.fail = false
        viewModel.onAction(EmojiMixResultAction.Retry)
        advanceUntilIdle()
        assertNotNull(viewModel.state.value.result)
    }

    private class FakeReferenceImageStore(var fail: Boolean = false) : ReferenceImageStore {
        override suspend fun importHttpsImage(url: String, filePrefix: String): ImportedReference {
            if (fail) error("offline")
            return ImportedReference("content://test/result.png", displayName = "result.png")
        }

        override suspend fun createEmojiComposite(emojis: List<String>) =
            ImportedReference("content://test/composite.png", displayName = "composite.png")

        override suspend fun copy(sourceUri: String, destinationUri: String) = Unit
    }
}
