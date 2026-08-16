package com.a02.draw.ads.ui

internal class NativeAdStateMachine {
    var state: NativeAdHostState = NativeAdHostState.HIDDEN
        private set

    private var activeRequestToken = 0L

    fun startLoading(): Long {
        activeRequestToken += 1L
        state = NativeAdHostState.LOADING
        return activeRequestToken
    }

    fun completeLoading(requestToken: Long, wasSuccessful: Boolean): NativeAdHostState? {
        if (requestToken != activeRequestToken || state != NativeAdHostState.LOADING) return null
        state = if (wasSuccessful) NativeAdHostState.LOADED else NativeAdHostState.HIDDEN
        return state
    }

    fun hide() {
        activeRequestToken += 1L
        state = NativeAdHostState.HIDDEN
    }
}
