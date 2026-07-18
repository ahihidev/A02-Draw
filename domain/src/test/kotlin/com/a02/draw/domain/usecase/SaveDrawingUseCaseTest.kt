package com.a02.draw.domain.usecase

import com.a02.draw.core.common.result.AppError
import com.a02.draw.core.common.result.AppResult
import com.a02.draw.domain.model.Drawing
import com.a02.draw.domain.repository.DrawingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SaveDrawingUseCaseTest {
    private val repository = object : DrawingRepository {
        override fun observeDrawings(): Flow<List<Drawing>> = emptyFlow()
        override suspend fun getDrawing(id: Long): AppResult<Drawing> = error("unused")
        override suspend fun saveDrawing(drawing: Drawing): AppResult<Long> = AppResult.Success(1L)
        override suspend fun deleteDrawing(id: Long): AppResult<Unit> = error("unused")
    }

    @Test
    fun `blank title is rejected`() = runTest {
        val result = SaveDrawingUseCase(repository)(Drawing(title = "  ", updatedAtEpochMillis = 0))

        assertEquals(
            AppResult.Failure(AppError.Validation("Drawing title cannot be blank.")),
            result,
        )
    }
}
