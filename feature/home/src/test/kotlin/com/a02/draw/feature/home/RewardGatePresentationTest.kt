package com.a02.draw.feature.home

import com.a02.draw.domain.model.Artwork
import com.a02.draw.domain.model.ContentImage
import com.a02.draw.domain.model.DrawingLesson
import com.a02.draw.feature.home.common.component.ArtworkRow
import com.a02.draw.feature.home.common.component.LessonRow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RewardGatePresentationTest {
    @Test
    fun `catalog artwork is reward locked even when backend premium flag is false`() {
        val artwork = Artwork(
            id = "artwork",
            topicId = "topic",
            title = "Artwork",
            image = ContentImage(),
            isPremium = false,
        )

        assertTrue(
            ArtworkRow(
                artwork = artwork,
                isFavorite = false,
                showFavorite = false,
            ).isLocked,
        )
        assertFalse(
            ArtworkRow(
                artwork = artwork,
                isFavorite = false,
                showFavorite = false,
                isLocked = false,
            ).isLocked,
        )
    }

    @Test
    fun `lesson is reward locked by default`() {
        val lesson = DrawingLesson(
            id = "lesson",
            categoryId = "category",
            title = "Lesson",
            minutes = 10,
            image = ContentImage(),
            isPremium = false,
        )

        assertTrue(LessonRow(lesson = lesson, completedSteps = 0).isLocked)
        assertFalse(LessonRow(lesson = lesson, completedSteps = 0, isLocked = false).isLocked)
    }
}
