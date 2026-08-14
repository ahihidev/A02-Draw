package com.a02.draw.data.local.fixture

import com.a02.draw.data.remote.dto.ArCatalogDto
import com.a02.draw.data.remote.dto.ArtworkDto
import com.a02.draw.data.remote.dto.ContentImageDto
import com.a02.draw.data.remote.dto.LessonCategoryDto
import com.a02.draw.data.remote.dto.LessonDto
import com.a02.draw.data.remote.dto.SettingItemDto
import com.a02.draw.data.remote.dto.TopicDto
import com.a02.draw.data.remote.dto.TrendingSearchDto
import javax.inject.Inject
import javax.inject.Singleton

/** Offline-first data with the exact same contract expected from GET /v1/catalog. */
@Singleton
class FixtureArContentDataSource @Inject constructor() {
    fun catalog(): ArCatalogDto {
        val topics = listOf(
            topic("chibi", "Chibi", "topic_chibi"),
            topic("pixel", "Pixel", "topic_pixel"),
            topic("anime", "Anime", "topic_anime"),
            topic("cartoon", "Cartoon", "topic_cartoon"),
            topic("world-cup", "World Cup", "topic_world_cup"),
            topic("bricks", "Lego", "topic_bricks"),
            topic("animal", "Animal", "topic_animal"),
            topic("flower", "Flower", "topic_flower"),
            topic("kids", "For Kids", "topic_kids"),
        )
        val artworkTitles = listOf(
            "Little Artist", "Pixel Astronaut", "Anime Artist", "Cartoon Fox", "Football Champion",
            "Brick Rocket", "Golden Puppy", "Flower Bouquet", "Happy Artists", "Creative Portrait",
            "Space Explorer", "Fox Friend",
        )
        val topicKeys = topics.map { it.id }
        val assets = listOf(
            "topic_chibi",
            "topic_pixel",
            "topic_anime",
            "topic_cartoon",
            "topic_world_cup",
            "topic_bricks",
            "topic_animal",
            "topic_flower",
            "topic_kids",
        )
        val baseArtworks = artworkTitles.mapIndexed { index, title ->
            val topicId = topicKeys[index % topicKeys.size]
            val seriesTag = if (topicId == "anime") {
                if (index % 2 == 0) "jujutsu-kaisen" else "one-piece"
            } else {
                null
            }
            ArtworkDto(
                id = "art-${index + 1}",
                topicId = topicId,
                title = title,
                image = local(assets[index % assets.size]),
                tags = listOfNotNull(title.lowercase(), topicId, seriesTag),
                premium = index > 8,
                difficulty = listOf("Easy", "Medium", "Hard")[index % 3],
                style = if (index % 2 == 0) "line_sketch" else "color",
                // The API may provide a dedicated transparent trace layer later. Offline content
                // deliberately uses the selected artwork itself so the AR overlay can never show
                // an unrelated fallback image.
                traceImage = local(assets[index % assets.size]),
            )
        }
        val animeExtras = listOf(
            animeArtwork(13, "Yuji Itadori", "topic_chibi", "jujutsu-kaisen"),
            animeArtwork(14, "Satoru Gojo", "topic_pixel", "jujutsu-kaisen"),
            animeArtwork(15, "Monkey D. Luffy", "topic_world_cup", "one-piece"),
            animeArtwork(16, "Roronoa Zoro", "topic_animal", "one-piece"),
            animeArtwork(17, "Doraemon Adventure", "topic_cartoon", "doraemon"),
            animeArtwork(18, "Doraemon Friends", "topic_kids", "doraemon"),
        )
        return ArCatalogDto(
            topics = topics,
            trendingSearches = listOf(
                trending("chibi", "Chibi", "topic_chibi", "#FFB926"),
                trending("pixel", "Pixel", "topic_pixel", "#FF8129"),
                trending("anime", "Anime", "topic_anime", "#F2447D"),
                trending("cartoon", "Cartoon", "topic_cartoon", "#2F95E8"),
            ),
            artworks = baseArtworks + animeExtras,
            lessons = listOf(
                lesson("ramen", "people", "Naruto Eats Ramen", 40, "topic_chibi"),
                lesson(
                    "doraemon",
                    "animals",
                    "Doraemon",
                    25,
                    "topic_cartoon",
                    completedPercent = 40,
                    completedLessons = 4,
                    totalLessons = 10,
                ),
                lesson("sasuke", "portrait", "Sasuke", 30, "topic_anime"),
                lesson("cinematic", "pixel-art", "Cinematic", 30, "topic_world_cup"),
                lesson("art-direction", "sports", "Art Direction", 25, "topic_flower"),
                lesson(
                    "christmas-tree",
                    "nature",
                    "Draw Christmas Tree",
                    40,
                    "topic_flower",
                    showInLearningPath = false,
                ),
                lesson(
                    "palm-tree",
                    "nature",
                    "Draw Palm Tree",
                    25,
                    "topic_animal",
                    showInLearningPath = false,
                ),
                lesson(
                    "leaf",
                    "nature",
                    "A Leaf",
                    10,
                    "topic_flower",
                    showInLearningPath = false,
                ),
                lesson(
                    "rainbow",
                    "nature",
                    "A Rainbow",
                    30,
                    "topic_kids",
                    showInLearningPath = false,
                ),
                lesson(
                    "flower-garden",
                    "nature",
                    "A Flower Garden",
                    25,
                    "topic_flower",
                    showInLearningPath = false,
                ),
                lesson(
                    "potted-plants",
                    "nature",
                    "A potted plants",
                    25,
                    "topic_animal",
                    showInLearningPath = false,
                ),
            ),
            categories = listOf(
                category("people", "People", "Easy", "topic_chibi"),
                category("animals", "Animal", "Medium", "topic_cartoon"),
                category("portrait", "Sasuke", "Easy", "topic_anime"),
                category("pixel-art", "Cinematic", "Hard", "topic_world_cup"),
                category("sports", "Art Direction", "Easy", "topic_flower"),
                category("nature", "Plant", "Easy", "topic_flower"),
            ),
            plans = emptyList(),
            settings = listOf(
                SettingItemDto("music", "Music", "toggle"),
                SettingItemDto("help", "Help & FAQs"),
                SettingItemDto("update", "Update version"),
                SettingItemDto("share", "Share to your friends", "share"),
                SettingItemDto("rate", "Rate 5 stars", "rate"),
                SettingItemDto("feedback", "Feedback"),
                SettingItemDto("privacy", "Privacy policy"),
                SettingItemDto("terms", "Terms of use"),
            ),
        )
    }

    private fun topic(id: String, title: String, asset: String) = TopicDto(id, title, local(asset))
    private fun trending(id: String, title: String, asset: String, accentColorHex: String) =
        TrendingSearchDto(id, title, local(asset), accentColorHex)

    private fun lesson(
        id: String,
        category: String,
        title: String,
        minutes: Int,
        asset: String,
        completedPercent: Int = 0,
        completedLessons: Int = 0,
        totalLessons: Int = 1,
        showInLearningPath: Boolean = false,
    ) = LessonDto(
        id = id,
        categoryId = category,
        title = title,
        minutes = minutes,
        completedPercent = completedPercent,
        image = local(asset),
        completedLessons = completedLessons,
        totalLessons = totalLessons,
        showInLearningPath = showInLearningPath,
    )

    private fun category(id: String, title: String, difficulty: String, asset: String) =
        LessonCategoryDto(id, title, difficulty, 15, local(asset))

    private fun animeArtwork(id: Int, title: String, asset: String, seriesTag: String) = ArtworkDto(
        id = "art-$id",
        topicId = "anime",
        title = title,
        image = local(asset),
        tags = listOf(title.lowercase(), "anime", seriesTag),
        premium = id >= 17,
        difficulty = listOf("Easy", "Medium", "Hard")[id % 3],
        style = if (id % 2 == 0) "line_sketch" else "color",
        traceImage = local(asset),
    )

    private fun local(key: String) = ContentImageDto(localKey = key)
}
