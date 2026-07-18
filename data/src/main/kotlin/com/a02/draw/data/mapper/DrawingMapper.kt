package com.a02.draw.data.mapper

import com.a02.draw.data.local.database.entity.DrawingEntity
import com.a02.draw.data.remote.dto.DrawingDto
import com.a02.draw.domain.model.Drawing

internal fun DrawingEntity.toDomain(): Drawing = Drawing(
    id = id,
    title = title,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

internal fun Drawing.toEntity(): DrawingEntity = DrawingEntity(
    id = id,
    title = title,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

internal fun DrawingDto.toEntity(): DrawingEntity = DrawingEntity(
    id = id,
    title = title,
    updatedAtEpochMillis = updatedAtEpochMillis,
)
