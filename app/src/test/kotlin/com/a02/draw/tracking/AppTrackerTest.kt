package com.a02.draw.tracking

import org.junit.Assert.assertNotNull
import org.junit.Test

class AppTrackerTest {
    @Test
    fun `app tracker initializes properly`() {
        val tracker = AppTracker()
        assertNotNull(tracker)
    }
}
