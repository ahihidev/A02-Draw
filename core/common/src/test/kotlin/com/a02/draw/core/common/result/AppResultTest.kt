package com.a02.draw.core.common.result

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AppResultTest {
    @Test
    fun `map transforms successful value`() {
        val result = AppResult.Success(2).map { it * 3 }

        assertEquals(AppResult.Success(6), result)
    }

    @Test
    fun `map preserves failure`() {
        val failure = AppResult.Failure(AppError.Network)

        assertSame(failure, failure.map { "unused" })
    }
}
