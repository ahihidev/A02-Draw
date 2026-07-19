package com.a02.draw.domain.model

/**
 * A backend-friendly asset reference. Production responses should normally provide [url];
 * [localKey] keeps the bundled fixture catalog useful while an API is unavailable.
 */
data class ContentImage(
    val url: String? = null,
    val localKey: String? = null,
)

data class DrawingTopic(
    val id: String,
    val title: String,
    val image: ContentImage,
)

data class TrendingSearch(
    val id: String,
    val title: String,
    val image: ContentImage,
    val accentColorHex: String? = null,
)

data class Artwork(
    val id: String,
    val topicId: String,
    val title: String,
    val image: ContentImage,
    val tags: List<String> = emptyList(),
    val isPremium: Boolean = false,
    val difficulty: String? = null,
    val style: ArtworkStyle = ArtworkStyle.COLOR,
    val traceImage: ContentImage? = null,
)

enum class ArtworkStyle { LINE_SKETCH, COLOR }

data class DrawingLesson(
    val id: String,
    val categoryId: String,
    val title: String,
    val minutes: Int,
    val completedPercent: Int = 0,
    val image: ContentImage,
)

data class LessonCategory(
    val id: String,
    val title: String,
    val difficulty: String,
    val lessonCount: Int,
    val image: ContentImage,
)

data class SubscriptionPlan(
    val id: String,
    val title: String,
    val subtitle: String,
    val price: String,
    val isRecommended: Boolean = false,
)

data class AppSettingItem(
    val id: String,
    val title: String,
    val type: SettingType = SettingType.LINK,
    val target: String? = null,
)

enum class SettingType { LINK, TOGGLE, SHARE, RATE }

data class ArCatalog(
    val topics: List<DrawingTopic>,
    val trendingSearches: List<TrendingSearch> = emptyList(),
    val artworks: List<Artwork>,
    val lessons: List<DrawingLesson>,
    val categories: List<LessonCategory>,
    val plans: List<SubscriptionPlan>,
    val settings: List<AppSettingItem>,
)
