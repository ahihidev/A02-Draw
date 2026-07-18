package com.a02.draw.data.mapper

import com.a02.draw.core.common.result.AppError
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test

class ErrorMapperTest {
    @Test
    fun `io exception maps to network error`() {
        assertEquals(AppError.Network, IOException("offline").toAppError())
    }
}
