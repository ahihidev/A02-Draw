package com.a02.draw.domain.usecase

import com.a02.draw.core.common.result.AppResult
import com.a02.draw.domain.repository.DrawingRepository
import javax.inject.Inject

class DeleteDrawingUseCase @Inject constructor(
    private val repository: DrawingRepository,
) {
    suspend operator fun invoke(id: Long): AppResult<Unit> = repository.deleteDrawing(id)
}
