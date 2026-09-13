package com.example.framegate.domain.interfaces

/** Time abstraction to control backoff behavior in tests. */
interface Clock {
    fun currentTimeMillis(): Long
    suspend fun sleep(millis: Long)
}
