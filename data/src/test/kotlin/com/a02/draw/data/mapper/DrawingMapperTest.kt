package com.a02.draw.data.mapper

import com.a02.draw.data.local.database.entity.DrawingEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class DrawingMapperTest {
    @Test
    fun `entity maps all fields to domain`() {
        val drawing = DrawingEntity(
            id = 7,
            title = "Sketch",
            updatedAtEpochMillis = 1234,
            mediaUri = "content://drawing",
            artworkId = "art-1",
            lessonId = "lesson-1",
            lessonMinutes = 25,
            usesCamera = false,
        ).toDomain()

        assertEquals(7, drawing.id)
        assertEquals("Sketch", drawing.title)
        assertEquals(1234, drawing.updatedAtEpochMillis)
        assertEquals("content://drawing", drawing.mediaUri)
        assertEquals("art-1", drawing.artworkId)
        assertEquals("lesson-1", drawing.lessonId)
        assertEquals(25, drawing.lessonMinutes)
        assertEquals(false, drawing.usesCamera)
    }
}
