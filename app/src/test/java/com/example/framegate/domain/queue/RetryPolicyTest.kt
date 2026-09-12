package com.example.framegate.domain.queue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryPolicyTest {

    @Test
    fun `backoff crece exponencialmente sin jitter`() {
        val policy = RetryPolicy(baseDelayMillis = 500, maxDelayMillis = 100_000, jitterFactor = 0.0)
        val noJitter = { 0.0 }

        assertEquals(500L, policy.delayForAttempt(1, noJitter))   // 500 * 2^0
        assertEquals(1000L, policy.delayForAttempt(2, noJitter))  // 500 * 2^1
        assertEquals(2000L, policy.delayForAttempt(3, noJitter))  // 500 * 2^2
        assertEquals(4000L, policy.delayForAttempt(4, noJitter))  // 500 * 2^3
    }

    @Test
    fun `el delay se recorta al maximo configurado`() {
        val policy = RetryPolicy(baseDelayMillis = 500, maxDelayMillis = 1500, jitterFactor = 0.0)
        val noJitter = { 0.0 }

        // 500 * 2^3 = 4000, pero el cap es 1500.
        assertEquals(1500L, policy.delayForAttempt(4, noJitter))
    }

    @Test
    fun `el jitter agrega tiempo dentro del factor configurado`() {
        val policy = RetryPolicy(baseDelayMillis = 1000, maxDelayMillis = 100_000, jitterFactor = 0.5)

        // random=0 -> sin jitter; random~1 -> +50% del delay.
        assertEquals(1000L, policy.delayForAttempt(1) { 0.0 })
        assertEquals(1499L, policy.delayForAttempt(1) { 0.999 })
    }

    @Test
    fun `canRetry respeta el tope de intentos`() {
        val policy = RetryPolicy(maxAttempts = 3)
        assertTrue(policy.canRetry(1))
        assertTrue(policy.canRetry(2))
        assertFalse(policy.canRetry(3))
        assertFalse(policy.canRetry(4))
    }
}
