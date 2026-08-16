package com.a02.draw.ads.app

import android.app.Activity
import android.content.Context
import com.kiro.sdk.KiroSdk
import com.kiro.sdk.ads.AdType
import com.kiro.sdk.ads.KiroAdPool
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

internal interface RewardedAdGateway {
    fun isReady(adUnitId: String): Boolean

    suspend fun load(adUnitId: String): Boolean

    fun show(
        activity: Activity,
        adUnitId: String,
        onRewardEarned: () -> Unit,
        onComplete: () -> Unit,
    )
}

@Singleton
internal class KiroRewardedAdGateway @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : RewardedAdGateway {
    override fun isReady(adUnitId: String): Boolean =
        KiroAdPool.hasAd(AdType.REWARDED, adUnitId)

    override suspend fun load(adUnitId: String): Boolean =
        KiroSdk.ads.loadRewarded(context, adUnitId)

    override fun show(
        activity: Activity,
        adUnitId: String,
        onRewardEarned: () -> Unit,
        onComplete: () -> Unit,
    ) {
        KiroSdk.ads.showRewarded(
            activity,
            adUnitId,
            { _, _ -> onRewardEarned() },
            onComplete,
        )
    }
}
