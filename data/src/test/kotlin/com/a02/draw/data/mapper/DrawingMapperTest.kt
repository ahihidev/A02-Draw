package com.a02.draw.data.mapper

import com.a02.draw.data.local.database.entity.DrawingEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class DrawingMapperTest {
    @Test
    fun `entity maps all fields to domain`() {
        val drawing = DrawingEntity(7, "Sketch", 1234).toDomain()

        assertEquals(7, drawing.id)
        assertEquals("Sketch", drawing.title)
        assertEquals(1234, drawing.updatedAtEpochMillis)
    }
}
