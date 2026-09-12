package com.example.framegate.domain.interfaces

/** Abstracción del tiempo, para poder controlar el backoff en los tests. */
interface Clock {
    fun currentTimeMillis(): Long
    suspend fun sleep(millis: Long)
}
