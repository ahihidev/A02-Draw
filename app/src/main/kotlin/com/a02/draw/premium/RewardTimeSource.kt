package com.a02.draw.premium

import android.os.SystemClock

interface RewardTimeSource {
    fun currentTimeMillis(): Long
    fun elapsedRealtimeMillis(): Long
}

class SystemRewardTimeSource : RewardTimeSource {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()

    override fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()
}
