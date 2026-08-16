package com.a02.draw.ads.startup

import android.content.Context
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnPaidEventListener
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd
import com.kiro.sdk.event.KiroLogEventManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

internal sealed interface AdLoadResult<out T> {
    data class Success<T>(val ad: T) : AdLoadResult<T>
    data class Failure(val reason: AdLoadFailure, val message: String) : AdLoadResult<Nothing>
}

internal interface StartupAdGateway {
    fun loadInterstitial(adUnitId: String, callback: (AdLoadResult<InterstitialAd>) -> Unit)
    fun loadNative(adUnitId: String, callback: (AdLoadResult<NativeAd>) -> Unit)
}

@Singleton
internal class GmaStartupAdGateway @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : StartupAdGateway {
    private val interstitialQueue = AdUnitSingleFlightQueue()
    private val nativeQueue = AdUnitSingleFlightQueue()

    override fun loadInterstitial(
        adUnitId: String,
        callback: (AdLoadResult<InterstitialAd>) -> Unit,
    ) {
        interstitialQueue.enqueue(adUnitId) { complete ->
            InterstitialAd.load(
                context,
                adUnitId,
                AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        ad.onPaidEventListener = OnPaidEventListener { value ->
                            KiroLogEventManager.logPaidAdImpression(
                                context,
                                value,
                                adUnitId,
                                FORMAT_INTERSTITIAL,
                            )
                        }
                        callback(AdLoadResult.Success(ad))
                        complete()
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        callback(AdLoadResult.Failure(error.toFailure(), error.toString()))
                        complete()
                    }
                },
            )
        }
    }

    override fun loadNative(adUnitId: String, callback: (AdLoadResult<NativeAd>) -> Unit) {
        nativeQueue.enqueue(adUnitId) { complete ->
            val loader = AdLoader.Builder(context, adUnitId)
                .forNativeAd { ad ->
                    ad.setOnPaidEventListener { value ->
                        KiroLogEventManager.logPaidAdImpression(
                            context,
                            value,
                            adUnitId,
                            FORMAT_NATIVE,
                        )
                    }
                    callback(AdLoadResult.Success(ad))
                    complete()
                }
                .withAdListener(object : AdListener() {
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        callback(AdLoadResult.Failure(error.toFailure(), error.toString()))
                        complete()
                    }

                    override fun onAdClicked() {
                        KiroLogEventManager.logClickAdsEvent(adUnitId)
                    }
                })
                .build()
            loader.loadAd(AdRequest.Builder().build())
        }
    }

    private fun LoadAdError.toFailure(): AdLoadFailure =
        classifyAdLoadFailure(code = code, message = message)

    private companion object {
        const val FORMAT_INTERSTITIAL = "interstitial"
        const val FORMAT_NATIVE = "native"
    }
}

internal fun classifyAdLoadFailure(code: Int, message: String): AdLoadFailure {
    val isDnsFailure = message.contains("Unable to resolve host", ignoreCase = true) ||
            message.contains("No address associated with hostname", ignoreCase = true)
    if (isDnsFailure) return AdLoadFailure.NETWORK_ERROR

    return when (code) {
        0 -> AdLoadFailure.INTERNAL_ERROR
        1 -> AdLoadFailure.INVALID_REQUEST
        2 -> AdLoadFailure.NETWORK_ERROR
        3 -> AdLoadFailure.NO_FILL
        else -> AdLoadFailure.UNKNOWN
    }
}
