package com.a02.draw.data.mapper

import com.a02.draw.data.remote.dto.ArCatalogDto
import com.a02.draw.data.remote.dto.ContentImageDto
import com.a02.draw.domain.model.AppSettingItem
import com.a02.draw.domain.model.ArCatalog
import com.a02.draw.domain.model.Artwork
import com.a02.draw.domain.model.ArtworkStyle
import com.a02.draw.domain.model.ContentImage
import com.a02.draw.domain.model.DrawingLesson
import com.a02.draw.domain.model.DrawingLessonStep
import com.a02.draw.domain.model.DrawingTopic
import com.a02.draw.domain.model.LessonCategory
import com.a02.draw.domain.model.SettingType
import com.a02.draw.domain.model.SubscriptionPlan
import com.a02.draw.domain.model.TrendingSearch

internal fun ArCatalogDto.toDomain(): ArCatalog = ArCatalog(
    topics = topics.map { DrawingTopic(it.id, it.title, it.image.toDomain()) },
    trendingSearches = trendingSearches.map {
        TrendingSearch(it.id, it.title, it.image.toDomain(), it.accentColorHex)
    },
    artworks = artworks.map {
        Artwork(
            id = it.id,
            topicId = it.topicId,
            title = it.title,
            image = it.image.toDomain(),
            tags = it.tags,
            isPremium = it.premium,
            difficulty = it.difficulty,
            style = if (it.style.equals("line_sketch", ignoreCase = true) ||
                it.style.equals("line-sketch", ignoreCase = true)
            ) {
                ArtworkStyle.LINE_SKETCH
            } else {
                ArtworkStyle.COLOR
            },
            traceImage = it.traceImage?.toDomain(),
        )
    },
    lessons = lessons.map {
        DrawingLesson(
            id = it.id,
            categoryId = it.categoryId,
            title = it.title,
            minutes = it.minutes,
            completedPercent = it.completedPercent.coerceIn(0, 100),
            image = it.image.toDomain(),
            completedLessons = it.completedLessons.coerceAtLeast(0),
            totalLessons = it.totalLessons.coerceAtLeast(1),
            showInLearningPath = it.showInLearningPath,
            totalSteps = it.totalSteps.coerceAtLeast(0),
            steps = it.steps.map { step ->
                DrawingLessonStep(step.stepNumber, step.image.toDomain())
            },
        )
    },
    categories = categories.map {
        LessonCategory(it.id, it.title, it.difficulty, it.lessonCount, it.image.toDomain())
    },
    plans = plans.map {
        SubscriptionPlan(it.id, it.title, it.subtitle, it.price, it.recommended)
    },
    settings = settings.map {
        AppSettingItem(
            id = it.id,
            title = it.title,
            type = when (it.type.lowercase()) {
                "toggle" -> SettingType.TOGGLE
                "share" -> SettingType.SHARE
                "rate" -> SettingType.RATE
                else -> SettingType.LINK
            },
            target = it.target,
        )
    },
)

private fun ContentImageDto.toDomain() = ContentImage(url = url, localKey = localKey)
