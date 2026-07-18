package com.a02.draw.data.repository

import com.a02.draw.core.common.coroutines.DispatcherProvider
import com.a02.draw.core.common.result.AppError
import com.a02.draw.core.common.result.AppResult
import com.a02.draw.data.local.database.dao.DrawingDao
import com.a02.draw.data.local.database.entity.DrawingEntity
import com.a02.draw.data.mapper.toAppError
import com.a02.draw.data.mapper.toDomain
import com.a02.draw.data.mapper.toEntity
import com.a02.draw.domain.model.Drawing
import com.a02.draw.domain.repository.DrawingRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

@Singleton
class DefaultDrawingRepository @Inject constructor(
    private val drawingDao: DrawingDao,
    private val dispatchers: DispatcherProvider,
) : DrawingRepository {
    override fun observeDrawings(): Flow<List<Drawing>> = drawingDao.observeAll()
        .onStart { seedIfEmpty() }
        .map { entities -> entities.map(DrawingEntity::toDomain) }
        .flowOn(dispatchers.io)

    override suspend fun getDrawing(id: Long): AppResult<Drawing> = withContext(dispatchers.io) {
        try {
            drawingDao.getById(id)?.toDomain()?.let { AppResult.Success(it) }
                ?: AppResult.Failure(AppError.Validation("Drawing not found."))
        } catch (throwable: Throwable) {
            AppResult.Failure(throwable.toAppError())
        }
    }

    override suspend fun saveDrawing(drawing: Drawing): AppResult<Long> = execute {
        drawingDao.save(drawing.toEntity())
    }

    override suspend fun deleteDrawing(id: Long): AppResult<Unit> = execute {
        drawingDao.deleteById(id)
    }

    private suspend fun seedIfEmpty() = withContext(dispatchers.io) {
        if (drawingDao.count() == 0) {
            val now = System.currentTimeMillis()
            drawingDao.insertAll(
                listOf(
                    DrawingEntity(title = "Welcome sketch", updatedAtEpochMillis = now),
                    DrawingEntity(title = "UI wireframe", updatedAtEpochMillis = now - 3_600_000),
                    DrawingEntity(title = "Ideas", updatedAtEpochMillis = now - 86_400_000),
                ),
            )
        }
    }

    private suspend fun <T> execute(block: suspend () -> T): AppResult<T> = withContext(dispatchers.io) {
        try {
            AppResult.Success(block())
        } catch (throwable: Throwable) {
            AppResult.Failure(throwable.toAppError())
        }
    }
}
