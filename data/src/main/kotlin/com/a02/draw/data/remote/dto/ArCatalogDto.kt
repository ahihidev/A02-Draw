package com.a02.draw.data.remote.dto

data class ContentImageDto(
    val url: String? = null,
    val localKey: String? = null,
)

data class TopicDto(val id: String, val title: String, val image: ContentImageDto)

data class TrendingSearchDto(
    val id: String,
    val title: String,
    val image: ContentImageDto,
    val accentColorHex: String? = null,
)

data class ArtworkDto(
    val id: String,
    val topicId: String,
    val title: String,
    val image: ContentImageDto,
    val tags: List<String> = emptyList(),
    val premium: Boolean = false,
    val difficulty: String? = null,
    val style: String = "color",
    val traceImage: ContentImageDto? = null,
)

data class LessonDto(
    val id: String,
    val categoryId: String,
    val title: String,
    val minutes: Int,
    val completedPercent: Int = 0,
    val image: ContentImageDto,
)

data class LessonCategoryDto(
    val id: String,
    val title: String,
    val difficulty: String,
    val lessonCount: Int,
    val image: ContentImageDto,
)

data class SubscriptionPlanDto(
    val id: String,
    val title: String,
    val subtitle: String,
    val price: String,
    val recommended: Boolean = false,
)

data class SettingItemDto(
    val id: String,
    val title: String,
    val type: String = "link",
    val target: String? = null,
)

data class ArCatalogDto(
    val topics: List<TopicDto> = emptyList(),
    val trendingSearches: List<TrendingSearchDto> = emptyList(),
    val artworks: List<ArtworkDto> = emptyList(),
    val lessons: List<LessonDto> = emptyList(),
    val categories: List<LessonCategoryDto> = emptyList(),
    val plans: List<SubscriptionPlanDto> = emptyList(),
    val settings: List<SettingItemDto> = emptyList(),
)
