package com.a02.draw.ads.ui

import androidx.annotation.DimenRes
import androidx.annotation.LayoutRes
import com.a02.draw.R

enum class NativeAdFormat(
    @param:LayoutRes internal val layoutResId: Int,
    @param:DimenRes internal val heightResId: Int?,
) {
    MEDIUM(
        layoutResId = R.layout.layout_native_ad_medium,
        heightResId = R.dimen.native_ad_medium_height,
    ),
    LARGE(
        layoutResId = R.layout.layout_native_ad_large,
        heightResId = R.dimen.native_ad_large_height,
    ),
    FULL(
        layoutResId = R.layout.layout_native_ad_full,
        heightResId = null,
    ),
}

sealed interface NativeAdRequest {
    data class Single(val adUnitId: String) : NativeAdRequest {
        init {
            require(adUnitId.isNotBlank()) { "Native ad unit ID must not be blank." }
        }
    }

    data class TwoFloor(
        val highAdUnitId: String,
        val lowAdUnitId: String,
    ) : NativeAdRequest {
        init {
            require(highAdUnitId.isNotBlank()) { "High-floor native ad unit ID must not be blank." }
            require(lowAdUnitId.isNotBlank()) { "Low-floor native ad unit ID must not be blank." }
        }
    }
}

enum class NativeAdHostState {
    LOADING,
    LOADED,
    HIDDEN,
}

enum class NativeFullAdExitReason {
    USER_CLOSED,
    LOAD_FAILED,
}

internal class NativeFullAdExitDispatcher {
    private var hasDispatched = false

    fun reset() {
        hasDispatched = false
    }

    fun dispatch(
        reason: NativeFullAdExitReason,
        listener: ((NativeFullAdExitReason) -> Unit)?,
    ): Boolean {
        if (hasDispatched) return false
        hasDispatched = true
        listener?.invoke(reason)
        return true
    }
}
