package com.a02.draw.core.ui.ads

import android.app.Activity
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.StateFlow

enum class AppAdPlacement {
    CHOOSE_TOPIC,
    HOME,
    TOPIC_DETAIL,
    SEARCH,
    LEARN,
    LEARN_DETAIL,
    SETTING,
    SELECT_MODE,
    APP_GENERIC,
}

enum class AppNativeAdFormat {
    MEDIUM,
    LARGE,
}

sealed interface RewardContentKey {
    val legacyItemKey: String

    data class Artwork(val id: String) : RewardContentKey {
        init {
            require(id.isNotBlank()) { "Artwork id must not be blank." }
        }

        override val legacyItemKey: String = "artwork:$id"
    }

    data class Lesson(
        val lessonId: String,
        val categoryId: String,
    ) : RewardContentKey {
        init {
            require(lessonId.isNotBlank()) { "Lesson id must not be blank." }
            require(categoryId.isNotBlank()) { "Lesson category id must not be blank." }
        }

        override val legacyItemKey: String = "lesson:$lessonId"
        val passKey: String = "lesson-category:$categoryId"
    }

    data class Emoji(val value: String) : RewardContentKey {
        init {
            require(value.isNotBlank()) { "Emoji value must not be blank." }
        }

        override val legacyItemKey: String = "emoji:$value"
    }
}

data class RewardAccessState(
    val permanentItemKeys: Set<String> = emptySet(),
    val activePassExpiries: Map<String, Long> = emptyMap(),
) {
    fun hasAccess(content: RewardContentKey): Boolean = when (content) {
        is RewardContentKey.Artwork -> content.legacyItemKey in permanentItemKeys
        is RewardContentKey.Lesson ->
            content.legacyItemKey in permanentItemKeys || content.passKey in activePassExpiries

        // Emoji Mix is a per-create reward. Never let a persisted pass or legacy item key
        // bypass the rewarded ad for the next generated mix.
        is RewardContentKey.Emoji -> false
    }
}

enum class RewardUnlockResult {
    GRANTED,
    DECLINED,
    NOT_READY,
    NOT_COMPLETED,
    BUSY,
}

interface PremiumEntitlementController {
    val isPremium: StateFlow<Boolean>
    val isInitialized: StateFlow<Boolean>

    /** Called after Billing has authoritatively resolved whether Premium is currently owned. */
    fun setPremiumOwned(isOwned: Boolean)

    /** Finishes startup entitlement resolution without overwriting the last confirmed state. */
    fun completeInitialization()
}

interface RewardUnlockStore {
    val accessState: StateFlow<RewardAccessState>

    fun grant(content: RewardContentKey)
}

interface AppAdsController {
    val isPremium: StateFlow<Boolean>
    val rewardAccessState: StateFlow<RewardAccessState>

    /** Idempotently removes active ads and cached inventory after Premium is activated. */
    fun releaseAdsForPremium()

    fun preloadMainAds()

    fun attachNative(
        container: ViewGroup,
        placement: AppAdPlacement,
        format: AppNativeAdFormat,
        lifecycleOwner: LifecycleOwner,
    )

    fun detachNative(container: ViewGroup)

    fun attachDrawingBanner(container: ViewGroup, lifecycleOwner: LifecycleOwner)

    fun detachDrawingBanner(container: ViewGroup)

    /** Runs [action] exactly once, whether an ad is shown, skipped, or fails. */
    fun runNavigationInterstitial(
        activity: Activity,
        isEligible: Boolean = true,
        action: () -> Unit,
    )

    /** Used only after the user presses Continue on the Welcome Back surface. */
    fun runBackgroundInterstitial(activity: Activity, action: () -> Unit)

    /** Prevents a document picker, chooser, or other external flow from looking like app resume. */
    fun suppressNextBackgroundInterstitial()

    /** Shows an explicit opt-in prompt and invokes [onUnlocked] only after a real reward. */
    fun requestRewardedUnlock(
        activity: Activity,
        lifecycleOwner: LifecycleOwner,
        content: RewardContentKey,
        itemName: CharSequence,
        onResult: (RewardUnlockResult) -> Unit,
    )

    fun isUnlocked(content: RewardContentKey): Boolean =
        isPremium.value || rewardAccessState.value.hasAccess(content)
}
