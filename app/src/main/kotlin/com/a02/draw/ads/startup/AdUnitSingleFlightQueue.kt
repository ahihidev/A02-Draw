package com.a02.draw.ads.startup

import java.util.concurrent.atomic.AtomicBoolean

/** Serializes requests sharing one ad-unit ID while allowing different IDs to load in parallel. */
internal class AdUnitSingleFlightQueue {
    private val pending = mutableMapOf<String, ArrayDeque<((() -> Unit) -> Unit)>>()
    private val activeIds = mutableSetOf<String>()

    fun enqueue(adUnitId: String, start: (() -> Unit) -> Unit) {
        pending.getOrPut(adUnitId, ::ArrayDeque).addLast(start)
        startNext(adUnitId)
    }

    fun isActive(adUnitId: String): Boolean = adUnitId in activeIds

    fun pendingCount(adUnitId: String): Int = pending[adUnitId]?.size.orEmpty()

    private fun startNext(adUnitId: String) {
        if (adUnitId in activeIds) return
        val queue = pending[adUnitId] ?: return
        val request = queue.removeFirstOrNull() ?: run {
            pending.remove(adUnitId)
            return
        }
        activeIds += adUnitId
        val completed = AtomicBoolean(false)
        request {
            if (!completed.compareAndSet(false, true)) return@request
            activeIds -= adUnitId
            if (queue.isEmpty()) pending.remove(adUnitId)
            startNext(adUnitId)
        }
    }

    private fun Int?.orEmpty(): Int = this ?: 0
}
