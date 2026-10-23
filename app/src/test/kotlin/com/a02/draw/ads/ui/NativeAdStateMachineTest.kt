package com.a02.draw.ads.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeAdStateMachineTest {
    @Test
    fun `successful current request moves loading state to loaded`() {
        val stateMachine = NativeAdStateMachine()

        val requestToken = stateMachine.startLoading()
        val result = stateMachine.completeLoading(requestToken, wasSuccessful = true)

        assertEquals(NativeAdHostState.LOADED, result)
        assertEquals(NativeAdHostState.LOADED, stateMachine.state)
    }

    @Test
    fun `failed current request moves loading state to hidden`() {
        val stateMachine = NativeAdStateMachine()

        val requestToken = stateMachine.startLoading()
        val result = stateMachine.completeLoading(requestToken, wasSuccessful = false)

        assertEquals(NativeAdHostState.HIDDEN, result)
        assertEquals(NativeAdHostState.HIDDEN, stateMachine.state)
    }

    @Test
    fun `reloading ignores completion from older request`() {
        val stateMachine = NativeAdStateMachine()
        val staleToken = stateMachine.startLoading()
        val currentToken = stateMachine.startLoading()

        assertNull(stateMachine.completeLoading(staleToken, wasSuccessful = true))
        assertEquals(NativeAdHostState.LOADING, stateMachine.state)
        assertEquals(
            NativeAdHostState.LOADED,
            stateMachine.completeLoading(currentToken, wasSuccessful = true),
        )
    }

    @Test
    fun `hiding invalidates pending completion`() {
        val stateMachine = NativeAdStateMachine()
        val requestToken = stateMachine.startLoading()

        stateMachine.hide()

        assertNull(stateMachine.completeLoading(requestToken, wasSuccessful = true))
        assertEquals(NativeAdHostState.HIDDEN, stateMachine.state)
    }

    @Test
    fun `full screen exit is emitted once per reset session`() {
        val dispatcher = NativeFullAdExitDispatcher()
        val exits = mutableListOf<NativeFullAdExitReason>()

        assertTrue(dispatcher.dispatch(NativeFullAdExitReason.USER_CLOSED, exits::add))
        assertFalse(dispatcher.dispatch(NativeFullAdExitReason.LOAD_FAILED, exits::add))
        dispatcher.reset()
        assertTrue(dispatcher.dispatch(NativeFullAdExitReason.LOAD_FAILED, exits::add))

        assertEquals(
            listOf(
                NativeFullAdExitReason.USER_CLOSED,
                NativeFullAdExitReason.LOAD_FAILED,
            ),
            exits,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `single request rejects blank id`() {
        NativeAdRequest.Single("  ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `two floor request rejects blank fallback id`() {
        NativeAdRequest.TwoFloor(highAdUnitId = "high", lowAdUnitId = "")
    }
}
