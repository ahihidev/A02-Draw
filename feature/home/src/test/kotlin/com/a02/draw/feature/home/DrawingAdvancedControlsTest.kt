package com.a02.draw.feature.home

import androidx.lifecycle.SavedStateHandle
import com.a02.draw.core.common.result.AppResult
import com.a02.draw.domain.model.AppPreferences
import com.a02.draw.domain.model.ThemeMode
import com.a02.draw.domain.repository.AppPreferencesRepository
import com.a02.draw.domain.usecase.UpdateAppPreferencesUseCase
import com.a02.draw.feature.home.common.drawing.DrawingControlAction
import com.a02.draw.feature.home.common.drawing.reduce
import com.a02.draw.feature.home.common.session.DefaultDrawingSessionStore
import com.a02.draw.feature.home.screen.camera.CameraAction
import com.a02.draw.feature.home.screen.camera.CameraEffect
import com.a02.draw.feature.home.screen.camera.CameraViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DrawingAdvancedControlsTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `canvas adjust actions rotate center and reset overlay`() {
        val store = DefaultDrawingSessionStore().apply {
            update {
                copy(
                    overlayOffsetX = 42f,
                    overlayOffsetY = -18f,
                    zoom = 1.8f,
                    overlayFlipped = true,
                )
            }
        }

        store.reduce(DrawingControlAction.RotateOverlay)
        assertEquals(90f, store.state.value.overlayRotationDegrees)

        store.reduce(DrawingControlAction.CenterOverlay)
        assertEquals(0f, store.state.value.overlayOffsetX)
        assertEquals(0f, store.state.value.overlayOffsetY)
        assertEquals(1.8f, store.state.value.zoom)

        store.reduce(DrawingControlAction.ResetOverlayTransform)
        assertEquals(1f, store.state.value.zoom)
        assertEquals(0f, store.state.value.overlayRotationDegrees)
        assertFalse(store.state.value.overlayFlipped)
    }

    @Test
    fun `camera shutter respects selected capture timer`() = runTest {
        val viewModel = cameraViewModel()

        viewModel.onAction(
            CameraAction.Control(DrawingControlAction.SelectCaptureDelay(3)),
        )
        viewModel.onAction(CameraAction.Control(DrawingControlAction.Shutter))
        advanceUntilIdle()

        assertEquals(3, viewModel.state.value.session.captureDelaySeconds)
        assertEquals(CameraEffect.Capture(delaySeconds = 3), viewModel.effects.first())
    }

    @Test
    fun `camera guide toggles without changing canvas grid`() {
        val store = DefaultDrawingSessionStore().apply {
            update { copy(gridSize = 4) }
        }

        store.reduce(DrawingControlAction.ToggleCameraGuide)

        assertTrue(store.state.value.cameraGuideEnabled)
        assertEquals(4, store.state.value.gridSize)
    }

    @Test
    fun `camera lens selection rolls back when device rejects it`() = runTest {
        val viewModel = cameraViewModel()

        viewModel.onAction(CameraAction.Control(DrawingControlAction.ToggleCameraLens))
        advanceUntilIdle()

        assertTrue(viewModel.state.value.session.useFrontCamera)
        assertEquals(CameraEffect.SetCameraLens(useFrontCamera = true), viewModel.effects.first())

        viewModel.onAction(CameraAction.CameraLensRejected)
        assertFalse(viewModel.state.value.session.useFrontCamera)
    }

    private fun cameraViewModel() = CameraViewModel(
        SavedStateHandle(),
        UpdateAppPreferencesUseCase(FakePreferencesRepository()),
    )

    private class FakePreferencesRepository : AppPreferencesRepository {
        override fun observePreferences(): Flow<AppPreferences> = emptyFlow()
        override suspend fun setThemeMode(themeMode: ThemeMode) = AppResult.Success(Unit)
        override suspend fun setLanguageTag(languageTag: String) = AppResult.Success(Unit)
        override suspend fun setOnboardingCompleted(completed: Boolean) = AppResult.Success(Unit)
        override suspend fun setPremium(isPremium: Boolean) = AppResult.Success(Unit)
        override suspend fun setRewardUnlockedItemIds(ids: Set<String>) = AppResult.Success(Unit)
        override suspend fun setFavoriteArtworkIds(ids: Set<String>) = AppResult.Success(Unit)
        override suspend fun setLessonCompletedSteps(lessonId: String, completedSteps: Int) =
            AppResult.Success(Unit)

        override suspend fun setMusicEnabled(enabled: Boolean) = AppResult.Success(Unit)
    }
}
