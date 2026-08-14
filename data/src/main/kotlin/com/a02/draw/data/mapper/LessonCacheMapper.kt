package com.a02.draw.data.mapper

import com.a02.draw.data.local.database.dao.LessonWithSteps
import com.a02.draw.data.local.database.entity.LessonEntity
import com.a02.draw.data.local.database.entity.LessonStepEntity
import com.a02.draw.data.remote.dto.RemoteLessonDto
import com.a02.draw.data.remote.dto.RemoteLessonStepDto

internal data class LessonCacheEntities(
    val lessons: List<LessonEntity>,
    val steps: List<LessonStepEntity>,
)

internal fun List<RemoteLessonDto>.toCacheEntities(): LessonCacheEntities = LessonCacheEntities(
    lessons = map { lesson ->
        LessonEntity(
            lessonId = lesson.lessonId,
            name = lesson.name,
            categorySlug = lesson.categorySlug,
            categoryName = lesson.categoryName,
            subcategorySlug = lesson.subcategorySlug,
            totalSteps = lesson.totalSteps,
            coverImageUrl = lesson.coverImageUrl,
        )
    },
    steps = flatMap { lesson ->
        lesson.steps.mapIndexed { position, step ->
            LessonStepEntity(
                lessonId = lesson.lessonId,
                position = position,
                stepNumber = step.stepNumber,
                imageUrl = step.imageUrl,
                storageKey = step.storageKey,
            )
        }
    },
)

internal fun List<LessonWithSteps>.toRemoteLessons(): List<RemoteLessonDto> = map { cached ->
    val lesson = cached.lesson
    RemoteLessonDto(
        lessonId = lesson.lessonId,
        name = lesson.name,
        categorySlug = lesson.categorySlug,
        categoryName = lesson.categoryName,
        subcategorySlug = lesson.subcategorySlug,
        totalSteps = lesson.totalSteps,
        coverImageUrl = lesson.coverImageUrl,
        steps = cached.steps.sortedBy(LessonStepEntity::position).map { step ->
            RemoteLessonStepDto(
                stepNumber = step.stepNumber,
                imageUrl = step.imageUrl,
                storageKey = step.storageKey,
            )
        },
    )
}
