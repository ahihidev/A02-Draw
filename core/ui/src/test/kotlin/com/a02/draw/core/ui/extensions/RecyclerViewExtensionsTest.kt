package com.a02.draw.core.ui.extensions

import org.junit.Assert.assertEquals
import org.junit.Test

class RecyclerViewExtensionsTest {
    @Test
    fun `span count grows with available width`() {
        assertEquals(
            4,
            calculateAdaptiveSpanCount(
                availableWidth = 720,
                minimumItemWidth = 160,
                minimumSpanCount = 2,
            ),
        )
    }

    @Test
    fun `span count keeps the requested minimum on narrow widths`() {
        assertEquals(
            2,
            calculateAdaptiveSpanCount(
                availableWidth = 240,
                minimumItemWidth = 160,
                minimumSpanCount = 2,
            ),
        )
    }
}
