package com.a02.draw.domain.usecase

import com.a02.draw.core.common.result.AppError
import com.a02.draw.core.common.result.AppResult
import com.a02.draw.domain.model.Drawing
import com.a02.draw.domain.repository.DrawingRepository
import javax.inject.Inject

class SaveDrawingUseCase @Inject constructor(
    private val repository: DrawingRepository,
) {
    suspend operator fun invoke(drawing: Drawing): AppResult<Long> {
        if (drawing.title.isBlank()) {
            return AppResult.Failure(AppError.Validation("Drawing title cannot be blank."))
        }
        return repository.saveDrawing(drawing.copy(title = drawing.title.trim()))
    }
}
