package com.example.framegate.domain.interfaces

import kotlinx.coroutines.delay

class SystemClock : Clock {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
    override suspend fun sleep(millis: Long) = delay(millis)
}
