package com.a02.draw.feature.home

import app.cash.turbine.test
import com.a02.draw.core.common.result.AppResult
import com.a02.draw.domain.model.Drawing
import com.a02.draw.domain.repository.DrawingRepository
import com.a02.draw.domain.usecase.ObserveDrawingsUseCase
import com.a02.draw.domain.usecase.SaveDrawingUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val drawings = MutableStateFlow(
        listOf(Drawing(id = 1, title = "Sketch", updatedAtEpochMillis = 10)),
    )
    private val repository = object : DrawingRepository {
        override fun observeDrawings(): Flow<List<Drawing>> = drawings
        override suspend fun getDrawing(id: Long): AppResult<Drawing> = error("unused")
        override suspend fun saveDrawing(drawing: Drawing): AppResult<Long> = AppResult.Success(2)
        override suspend fun deleteDrawing(id: Long): AppResult<Unit> = error("unused")
    }

    @Test
    fun `drawings are exposed as content state`() = runTest {
        val viewModel = createViewModel()

        advanceUntilIdle()

        assertEquals(false, viewModel.state.value.isLoading)
        assertEquals(drawings.value, viewModel.state.value.drawings)
    }

    @Test
    fun `successful add emits confirmation`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.effects.test {
            viewModel.onAddDrawingClicked()
            advanceUntilIdle()
            assertEquals(HomeEffect.ShowMessage(R.string.drawing_created), awaitItem())
        }
    }

    private fun createViewModel(): HomeViewModel = HomeViewModel(
        observeDrawings = ObserveDrawingsUseCase(repository),
        saveDrawing = SaveDrawingUseCase(repository),
    )
}
