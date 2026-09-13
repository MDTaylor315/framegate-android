package com.example.framegate.domain.queue

import com.example.framegate.domain.interfaces.Clock

/**
 * Fake clock for testing: does not sleep in real time, accumulating simulated sleep duration
 * in [totalSleptMillis]. Enables instant backoff unit test execution while verifying expected delays.
 */
class FakeClock(private var now: Long = 0L) : Clock {
    var totalSleptMillis: Long = 0L
        private set

    override fun currentTimeMillis(): Long = now

    override suspend fun sleep(millis: Long) {
        totalSleptMillis += millis
        now += millis
    }
}
