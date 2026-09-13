package com.example.framegate.domain.queue

import kotlin.math.min
import kotlin.math.pow

/**
 * Exponential backoff with jitter, capped delay, and max attempt limits.
 * Jitter is injected via [random] to allow deterministic testing.
 */
class RetryPolicy(
    private val baseDelayMillis: Long = DEFAULT_BASE_DELAY_MILLIS,
    private val maxDelayMillis: Long = DEFAULT_MAX_DELAY_MILLIS,
    val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    private val jitterFactor: Double = DEFAULT_JITTER_FACTOR,
) {
    fun canRetry(attempts: Int): Boolean = attempts < maxAttempts

    fun delayForAttempt(attempt: Int, random: () -> Double): Long {
        require(attempt >= 1) { "attempt must be >= 1, was $attempt" }

        val exponential = baseDelayMillis * 2.0.pow(attempt - 1)
        val capped = min(exponential, maxDelayMillis.toDouble())
        val jitter = capped * jitterFactor * random()
        return (capped + jitter).toLong()
    }

    companion object {
        const val DEFAULT_BASE_DELAY_MILLIS = 500L
        const val DEFAULT_MAX_DELAY_MILLIS = 30_000L
        const val DEFAULT_MAX_ATTEMPTS = 5
        const val DEFAULT_JITTER_FACTOR = 0.5
    }
}
