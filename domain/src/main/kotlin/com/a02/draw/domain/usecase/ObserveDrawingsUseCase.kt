package com.a02.draw.domain.usecase

import com.a02.draw.domain.model.Drawing
import com.a02.draw.domain.repository.DrawingRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveDrawingsUseCase @Inject constructor(
    private val repository: DrawingRepository,
) {
    operator fun invoke(): Flow<List<Drawing>> = repository.observeDrawings()
}
