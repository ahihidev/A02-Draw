package com.a02.draw.ads.startup

import org.junit.Assert.assertEquals
import org.junit.Test

class AdLoadFailureClassifierTest {
    @Test
    fun `dns failures reported as internal errors are treated as network failures`() {
        assertEquals(
            AdLoadFailure.NETWORK_ERROR,
            classifyAdLoadFailure(
                code = 0,
                message = "Unable to resolve host pubads.g.doubleclick.net: No address associated with hostname",
            ),
        )
    }

    @Test
    fun `non network failures preserve the GMA error code mapping`() {
        assertEquals(AdLoadFailure.INTERNAL_ERROR, classifyAdLoadFailure(0, "SDK failure"))
        assertEquals(AdLoadFailure.INVALID_REQUEST, classifyAdLoadFailure(1, "Bad request"))
        assertEquals(AdLoadFailure.NETWORK_ERROR, classifyAdLoadFailure(2, "Network error"))
        assertEquals(AdLoadFailure.NO_FILL, classifyAdLoadFailure(3, "No fill"))
        assertEquals(AdLoadFailure.UNKNOWN, classifyAdLoadFailure(99, "Unknown"))
    }
}
