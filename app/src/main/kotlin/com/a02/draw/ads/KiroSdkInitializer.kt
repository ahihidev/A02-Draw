package com.a02.draw.ads

import android.app.Application
import com.a02.draw.BuildConfig
import com.kiro.sdk.KiroSdk
import com.kiro.sdk.ads.KiroAds
import com.kiro.sdk.billing.KiroBilling
import com.kiro.sdk.tracking.KiroTracker

internal object KiroSdkInitializer {
    fun initialize(application: Application) {
        KiroSdk.init(
            context = application,
            config = createConfig(BuildConfig.DEBUG),
        )
    }

    internal fun createConfig(isDebug: Boolean): KiroSdk.SdkConfig =
        KiroSdk.SdkConfig(
            isDebug = isDebug,
            // SplashActivity gathers consent before any placement is allowed to load.
            enableAds = true,
            adConfig = KiroAds.Config(),
            trackingConfig = KiroTracker.Config(enableFirebase = false),
            billingConfig = KiroBilling.Config(enableBilling = false),
        )
}
