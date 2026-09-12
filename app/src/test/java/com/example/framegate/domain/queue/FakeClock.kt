package com.example.framegate.domain.queue

import com.example.framegate.domain.interfaces.Clock

/**
 * Reloj falso para tests: no duerme en tiempo real, solo acumula el tiempo
 * "dormido" en [totalSleptMillis]. Así los tests de backoff se ejecutan al
 * instante pero podemos verificar cuánto se habría esperado.
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
