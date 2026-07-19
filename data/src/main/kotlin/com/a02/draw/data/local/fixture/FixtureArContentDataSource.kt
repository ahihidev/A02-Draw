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
        return ArCatalogDto(
            topics = topics,
            trendingSearches = listOf(
                trending("chibi", "Chibi", "topic_chibi", "#FFB926"),
                trending("pixel", "Pixel", "topic_pixel", "#FF8129"),
                trending("anime", "Anime", "topic_anime", "#F2447D"),
                trending("cartoon", "Cartoon", "topic_cartoon", "#2F95E8"),
            ),
            artworks = artworkTitles.mapIndexed { index, title ->
                ArtworkDto(
                    id = "art-${index + 1}",
                    topicId = topicKeys[index % topicKeys.size],
                    title = title,
                    image = local(assets[index % assets.size]),
                    tags = listOf(title.lowercase(), topicKeys[index % topicKeys.size]),
                    premium = index > 8,
                    difficulty = listOf("Easy", "Medium", "Hard")[index % 3],
                    style = if (index % 2 == 0) "line_sketch" else "color",
                    // The API may provide a dedicated transparent trace layer later. Offline content
                    // deliberately uses the selected artwork itself so the AR overlay can never show
                    // an unrelated fallback image.
                    traceImage = local(assets[index % assets.size]),
                )
            },
            lessons = listOf(
                lesson("artist", "people", "Draw a Little Artist", 20, "topic_chibi"),
                lesson("astronaut", "pixel-art", "Pixel Astronaut", 25, "topic_pixel"),
                lesson("portrait", "portrait", "Anime-style Portrait", 30, "topic_anime"),
                lesson("fox", "animals", "Friendly Cartoon Fox", 20, "topic_cartoon"),
                lesson("bouquet", "nature", "Flower Bouquet", 25, "topic_flower"),
                lesson("kids", "people", "Happy Young Artists", 25, "topic_kids"),
            ),
            categories = listOf(
                category("nature", "Nature", "Easy", "topic_flower"),
                category("people", "People", "Easy", "topic_chibi"),
                category("animals", "Animals", "Medium", "topic_animal"),
                category("pixel-art", "Pixel Art", "Easy", "topic_pixel"),
                category("sports", "Sports", "Medium", "topic_world_cup"),
                category("portrait", "Portrait", "Hard", "topic_anime"),
            ),
            // Billing products must come from the production catalog together with the Play
            // Billing integration. The offline build never advertises prices it cannot charge.
            plans = emptyList(),
            settings = listOf(
                SettingItemDto("gift", "Gift Code Lifetime"),
                SettingItemDto("music", "Music", "toggle"),
                SettingItemDto("help", "Help & FAQs"),
                SettingItemDto("subscription", "Manage Subscription"),
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

    private fun lesson(id: String, category: String, title: String, minutes: Int, asset: String) =
        LessonDto(id, category, title, minutes, image = local(asset))

    private fun category(id: String, title: String, difficulty: String, asset: String) =
        LessonCategoryDto(id, title, difficulty, 15, local(asset))

    private fun local(key: String) = ContentImageDto(localKey = key)
}
