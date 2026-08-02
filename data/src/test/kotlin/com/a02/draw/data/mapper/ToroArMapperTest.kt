package com.a02.draw.data.mapper

import com.a02.draw.data.local.fixture.FixtureArContentDataSource
import com.a02.draw.data.remote.dto.RemoteAssetDto
import com.a02.draw.data.remote.dto.RemoteCategoryDto
import com.a02.draw.data.remote.dto.RemoteLessonDto
import com.a02.draw.data.remote.dto.RemoteLessonStepDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ToroArMapperTest {
    @Test
    fun `remote categories and assets replace only catalog backed content`() {
        val fixture = FixtureArContentDataSource().catalog()
        val result = fixture.withRemoteContent(
            categories = listOf(RemoteCategoryDto("animal", "Animal", 1)),
            assets = listOf(
                RemoteAssetDto(
                    assetId = "asset-1",
                    name = "Dog sketch",
                    categorySlug = "animal",
                    categoryName = "Animal",
                    subcategorySlug = "dog",
                    subcategoryName = "Dog",
                    difficulty = "easy",
                    imageUrl = "https://example.test/dog.webp",
                    storageKey = "animal_dog_color_0_easy.webp",
                ),
            ),
        )

        assertEquals(listOf("animal"), result.topics.map { it.id })
        assertEquals("https://example.test/dog.webp", result.topics.single().image.url)
        assertEquals("asset-1", result.artworks.single().id)
        assertEquals("line_sketch", result.artworks.single().style)
        assertEquals(result.artworks.single().image, result.artworks.single().traceImage)
        assertEquals(fixture.lessons, result.lessons)
        assertEquals(fixture.plans, result.plans)
        assertFalse(result.settings.isEmpty())
    }

    @Test
    fun `remote lessons replace bundled lessons and categories`() {
        val fixture = FixtureArContentDataSource().catalog()
        val animal10 = RemoteLessonDto(
            lessonId = "animal-10",
            name = "Animal 10",
            categorySlug = "animal",
            categoryName = "Animal",
            subcategorySlug = "animal-10",
            totalSteps = 3,
            coverImageUrl = "https://example.test/animal-10.webp",
            steps = listOf(
                RemoteLessonStepDto(1, "https://example.test/thumb.webp", "animal_thumb.webp"),
                RemoteLessonStepDto(1, "https://example.test/step-1.webp", "animal_1.webp"),
                RemoteLessonStepDto(2, "https://example.test/step-2.webp", "animal_2.webp"),
            ),
        )
        val result = fixture.withRemoteLessons(
            listOf(
                animal10,
                animal10.copy(
                    lessonId = "animal-2",
                    name = "Animal 2",
                    subcategorySlug = "animal-2",
                ),
            ),
        )

        val remoteLesson = result.lessons.single { it.id == "animal-10" }
        assertEquals("animal", remoteLesson.categoryId)
        assertEquals(null, remoteLesson.minutes)
        assertEquals(2, remoteLesson.totalSteps)
        assertEquals(listOf(1, 2), remoteLesson.steps.map { it.stepNumber })
        assertFalse(remoteLesson.showInLearningPath)
        assertEquals(
            listOf("animal-2", "animal-10"),
            result.lessons.map { it.id },
        )
        assertFalse(result.lessons.any { it.showInLearningPath })
        assertEquals(listOf("animal"), result.categories.map { it.id })
        assertEquals(2, result.categories.single().lessonCount)
    }
}
