package com.a02.draw.data.mapper

import com.a02.draw.data.local.database.dao.LessonWithSteps
import com.a02.draw.data.remote.dto.RemoteLessonDto
import com.a02.draw.data.remote.dto.RemoteLessonStepDto
import org.junit.Assert.assertEquals
import org.junit.Test

class LessonCacheMapperTest {
    @Test
    fun `cache round trip preserves API order and duplicate step numbers`() {
        val remote = RemoteLessonDto(
            lessonId = "animal-10",
            name = "Animal 10",
            categorySlug = "animal",
            categoryName = "Animal",
            subcategorySlug = "animal-10",
            totalSteps = 3,
            coverImageUrl = "https://example.test/cover.webp",
            steps = listOf(
                RemoteLessonStepDto(1, "https://example.test/thumb.webp", "animal_thumb.webp"),
                RemoteLessonStepDto(1, "https://example.test/1.webp", "animal_1.webp"),
                RemoteLessonStepDto(2, "https://example.test/2.webp", "animal_2.webp"),
            ),
        )

        val entities = listOf(remote).toCacheEntities()
        val roundTrip = listOf(
            LessonWithSteps(entities.lessons.single(), entities.steps.reversed()),
        ).toRemoteLessons().single()

        assertEquals(remote, roundTrip)
        assertEquals(listOf(0, 1, 2), entities.steps.map { it.position })
    }
}
