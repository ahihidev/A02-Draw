package com.a02.draw.play

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayPromptPolicyTest {
    @Test
    fun `update can be offered again only after dismiss cooldown`() {
        val dismissedAt = 1_000L

        assertFalse(
            PlayPromptPolicy.canOfferUpdate(
                nowMillis = dismissedAt + PlayPromptPolicy.UPDATE_DISMISS_COOLDOWN_MILLIS - 1L,
                lastDismissedAtMillis = dismissedAt,
            ),
        )
        assertTrue(
            PlayPromptPolicy.canOfferUpdate(
                nowMillis = dismissedAt + PlayPromptPolicy.UPDATE_DISMISS_COOLDOWN_MILLIS,
                lastDismissedAtMillis = dismissedAt,
            ),
        )
    }

    @Test
    fun `review waits for enough sessions and app age`() {
        val firstSessionAt = 1_000L
        val eligibleAt = firstSessionAt + PlayPromptPolicy.REVIEW_MINIMUM_AGE_MILLIS

        assertFalse(
            canRequestReview(
                nowMillis = eligibleAt,
                sessionCount = PlayPromptPolicy.REVIEW_MINIMUM_SESSIONS - 1,
                firstSessionAtMillis = firstSessionAt,
            ),
        )
        assertFalse(
            canRequestReview(
                nowMillis = eligibleAt - 1L,
                sessionCount = PlayPromptPolicy.REVIEW_MINIMUM_SESSIONS,
                firstSessionAtMillis = firstSessionAt,
            ),
        )
        assertTrue(
            canRequestReview(
                nowMillis = eligibleAt,
                sessionCount = PlayPromptPolicy.REVIEW_MINIMUM_SESSIONS,
                firstSessionAtMillis = firstSessionAt,
            ),
        )
    }

    @Test
    fun `review is not requested twice for the same version`() {
        val firstSessionAt = 1_000L
        val now = firstSessionAt + PlayPromptPolicy.REVIEW_MINIMUM_AGE_MILLIS

        assertFalse(
            canRequestReview(
                nowMillis = now,
                sessionCount = PlayPromptPolicy.REVIEW_MINIMUM_SESSIONS,
                firstSessionAtMillis = firstSessionAt,
                lastReviewVersionCode = CURRENT_VERSION,
            ),
        )
    }

    private fun canRequestReview(
        nowMillis: Long,
        sessionCount: Int,
        firstSessionAtMillis: Long,
        lastReviewVersionCode: Int = 0,
    ): Boolean = PlayPromptPolicy.canRequestReview(
        nowMillis = nowMillis,
        sessionCount = sessionCount,
        firstMainSessionAtMillis = firstSessionAtMillis,
        lastReviewRequestAtMillis = 0L,
        lastReviewVersionCode = lastReviewVersionCode,
        currentVersionCode = CURRENT_VERSION,
    )

    private companion object {
        const val CURRENT_VERSION = 7
    }
}
