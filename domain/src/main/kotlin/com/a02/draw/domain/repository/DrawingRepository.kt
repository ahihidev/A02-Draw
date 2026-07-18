package com.a02.draw.domain.repository

import com.a02.draw.core.common.result.AppResult
import com.a02.draw.domain.model.Drawing
import kotlinx.coroutines.flow.Flow

interface DrawingRepository {
    fun observeDrawings(): Flow<List<Drawing>>
    suspend fun getDrawing(id: Long): AppResult<Drawing>
    suspend fun saveDrawing(drawing: Drawing): AppResult<Long>
    suspend fun deleteDrawing(id: Long): AppResult<Unit>
}
