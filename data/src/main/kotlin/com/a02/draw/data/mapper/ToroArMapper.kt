package com.a02.draw.data.mapper

import com.a02.draw.data.remote.dto.ArCatalogDto
import com.a02.draw.data.remote.dto.ArtworkDto
import com.a02.draw.data.remote.dto.ContentImageDto
import com.a02.draw.data.remote.dto.LessonCategoryDto
import com.a02.draw.data.remote.dto.LessonDto
import com.a02.draw.data.remote.dto.LessonStepDto
import com.a02.draw.data.remote.dto.RemoteAssetDto
import com.a02.draw.data.remote.dto.RemoteCategoryDto
import com.a02.draw.data.remote.dto.RemoteLessonDto
import com.a02.draw.data.remote.dto.TopicDto
import com.a02.draw.data.remote.dto.TrendingSearchDto

internal fun ArCatalogDto.withRemoteContent(
    categories: List<RemoteCategoryDto>,
    assets: List<RemoteAssetDto>,
): ArCatalogDto {
    val firstAssetByCategory = assets.associateBy(RemoteAssetDto::categorySlug)
    val fallbackImages = topics.associate { it.id to it.image }
    val defaultFallback = topics.firstOrNull()?.image ?: ContentImageDto(localKey = "topic_chibi")
    val remoteTopics = categories.map { category ->
        TopicDto(
            id = category.slug,
            title = category.name,
            image = firstAssetByCategory[category.slug]?.toContentImage()
                ?: fallbackImages[category.slug]
                ?: defaultFallback,
        )
    }
    val accents = listOf("#FFB926", "#FF8129", "#F2447D", "#2F95E8")
    val remoteTrending = remoteTopics.take(accents.size).mapIndexed { index, topic ->
        TrendingSearchDto(
            id = topic.id,
            title = topic.title,
            image = topic.image,
            accentColorHex = accents[index],
        )
    }
    return copy(
        topics = remoteTopics.ifEmpty { topics },
        trendingSearches = remoteTrending.ifEmpty { trendingSearches },
        artworks = assets.map(RemoteAssetDto::toArtwork),
    )
}

internal fun ArCatalogDto.withRemoteLessons(remoteLessons: List<RemoteLessonDto>): ArCatalogDto {
    if (remoteLessons.isEmpty()) return this
    val apiLessons = remoteLessons
        .sortedWith(
            compareBy<RemoteLessonDto>(RemoteLessonDto::categorySlug)
                .thenBy { it.lessonId.substringAfterLast('-').toIntOrNull() ?: Int.MAX_VALUE }
                .thenBy(RemoteLessonDto::name),
        )
        .map(RemoteLessonDto::toLesson)
    val apiCategories = remoteLessons
        .groupBy(RemoteLessonDto::categorySlug)
        .map { (categorySlug, lessons) ->
            LessonCategoryDto(
                id = categorySlug,
                title = lessons.first().categoryName,
                difficulty = "All levels",
                lessonCount = lessons.size,
                image = ContentImageDto(url = lessons.first().coverImageUrl),
            )
        }
    return copy(
        lessons = apiLessons,
        categories = apiCategories,
    )
}

private fun RemoteLessonDto.toLesson(): LessonDto {
    val instructionSteps = steps
        .filterNot { it.storageKey.contains("_thumb", ignoreCase = true) }
        .map { step ->
            LessonStepDto(
                stepNumber = step.stepNumber,
                image = ContentImageDto(url = step.imageUrl),
            )
        }
        .ifEmpty {
            listOf(LessonStepDto(1, ContentImageDto(url = coverImageUrl)))
        }
    return LessonDto(
        id = lessonId,
        categoryId = categorySlug,
        title = name,
        minutes = null,
        image = ContentImageDto(url = coverImageUrl),
        totalLessons = 1,
        showInLearningPath = false,
        totalSteps = instructionSteps.size,
        steps = instructionSteps,
    )
}

private fun RemoteAssetDto.toArtwork(): ArtworkDto {
    val image = toContentImage()
    return ArtworkDto(
        id = assetId,
        topicId = categorySlug,
        title = name,
        image = image,
        tags = listOfNotNull(
            name,
            categorySlug,
            categoryName,
            subcategorySlug,
            subcategoryName,
        ).map(String::lowercase).distinct(),
        premium = false,
        difficulty = difficulty,
        style = if (storageKey?.contains("_color_0_", ignoreCase = true) == true) {
            "line_sketch"
        } else {
            "color"
        },
        traceImage = image,
    )
}

private fun RemoteAssetDto.toContentImage() = ContentImageDto(url = imageUrl)
