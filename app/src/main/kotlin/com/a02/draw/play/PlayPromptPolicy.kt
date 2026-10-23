package com.a02.draw.play

internal object PlayPromptPolicy {
    const val UPDATE_DISMISS_COOLDOWN_MILLIS = 24L * 60L * 60L * 1_000L
    const val REVIEW_MINIMUM_AGE_MILLIS = 48L * 60L * 60L * 1_000L
    const val REVIEW_COOLDOWN_MILLIS = 120L * 24L * 60L * 60L * 1_000L
    const val REVIEW_MINIMUM_SESSIONS = 3

    fun canOfferUpdate(nowMillis: Long, lastDismissedAtMillis: Long): Boolean =
        lastDismissedAtMillis <= 0L ||
                nowMillis - lastDismissedAtMillis >= UPDATE_DISMISS_COOLDOWN_MILLIS

    fun canRequestReview(
        nowMillis: Long,
        sessionCount: Int,
        firstMainSessionAtMillis: Long,
        lastReviewRequestAtMillis: Long,
        lastReviewVersionCode: Int,
        currentVersionCode: Int,
    ): Boolean =
        sessionCount >= REVIEW_MINIMUM_SESSIONS &&
                firstMainSessionAtMillis > 0L &&
                nowMillis - firstMainSessionAtMillis >= REVIEW_MINIMUM_AGE_MILLIS &&
                lastReviewVersionCode != currentVersionCode &&
                (lastReviewRequestAtMillis <= 0L ||
                        nowMillis - lastReviewRequestAtMillis >= REVIEW_COOLDOWN_MILLIS)
}
