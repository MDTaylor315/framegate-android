package com.example.framegate.domain.queue

import com.example.framegate.domain.interfaces.UploadTransport
import com.example.framegate.domain.model.UploadResult
import java.util.concurrent.ConcurrentHashMap

/**
 * Fake transport implementation using a deterministic per-record failure script.
 * [requestLog] serves as proof of audit for verifying exactly-once delivery.
 */
class FakeUploadTransport(
    private val script: FailureScript = FailureScript.empty(),
) : UploadTransport {

    private val attemptCounts = ConcurrentHashMap<String, Int>()
    private val _requestLog = mutableListOf<UploadRequest>()
    val requestLog: List<UploadRequest> get() = _requestLog.toList()

    private val stored = mutableSetOf<String>()

    // Sequential appearance order of each key, for scripts that trigger failures by position
    // (useful at runtime where keys are dynamically generated UUIDs).
    private val keyOrder = LinkedHashMap<String, Int>()

    override suspend fun uploadCapture(
        idempotencyKey: String,
        manifestJson: String,
        jpegBytes: ByteArray,
    ): UploadResult {
        val attempt = attemptCounts.merge(idempotencyKey, 1, Int::plus) ?: 1
        val keyIndex = keyOrder.getOrPut(idempotencyKey) { keyOrder.size }
        _requestLog.add(UploadRequest(idempotencyKey, attempt))

        // Key already stored -> re-sending returns 409 Conflict.
        if (stored.contains(idempotencyKey)) {
            return UploadResult.Success(HTTP_CONFLICT, "409 Conflict: key already stored")
        }

        return when (val outcome = script.outcomeFor(idempotencyKey, keyIndex, attempt)) {
            is Outcome.Stored -> {
                stored.add(idempotencyKey)
                UploadResult.Success(HTTP_CREATED, "201 Created")
            }
            is Outcome.Conflict -> {
                stored.add(idempotencyKey)
                UploadResult.Success(HTTP_CONFLICT, "409 Conflict")
            }
            is Outcome.Transient -> UploadResult.TransientError(outcome.code, "Transient error ${outcome.code}")
            is Outcome.Client -> UploadResult.ClientError(outcome.code, "Client error ${outcome.code}")
        }
    }

    companion object {
        const val HTTP_CREATED = 201
        const val HTTP_CONFLICT = 409
    }
}

data class UploadRequest(val idempotencyKey: String, val attempt: Int)

sealed interface Outcome {
    data object Stored : Outcome
    data object Conflict : Outcome
    data class Transient(val code: Int) : Outcome
    data class Client(val code: Int) : Outcome
}

/**
 * Scripted response per attempt. Supports two modes: by specific key (unit tests,
 * which know exact keys) and by sequential key order (runtime, where keys are UUIDs).
 * With no matching script, stores successfully on the first attempt.
 */
class FailureScript(
    private val byKey: Map<String, List<Outcome>> = emptyMap(),
    private val byOrder: List<List<Outcome>> = emptyList(),
) {
    fun outcomeFor(key: String, keyIndex: Int, attempt: Int): Outcome {
        byKey[key]?.let { return it.getOrElse(attempt - 1) { Outcome.Stored } }
        // Cyclic order mode: pattern repeats every byOrder.size keys, ensuring each loop
        // iteration presents identical failure sequences. Upon exhausting scripted attempts,
        // repeats the last outcome (so a 422 error continues to fail on manual retry).
        val outcomes = byOrder.getOrNull(keyIndex % byOrder.size.coerceAtLeast(1)) ?: emptyList()
        return outcomes.getOrElse(attempt - 1) { outcomes.lastOrNull() ?: Outcome.Stored }
    }

    companion object {
        fun empty(): FailureScript = FailureScript()

        /**
         * Demo script for runtime: 1st capture encounters a transient error that recovers
         * upon retry (backoff), and 2nd encounters a client error 422 that remains terminal
         * (manual retries will also fail).
         */
        fun demo(): FailureScript = FailureScript(
            byOrder = listOf(
                listOf(Outcome.Transient(HTTP_SERVER_ERROR), Outcome.Transient(HTTP_SERVER_ERROR), Outcome.Stored),
                listOf(Outcome.Client(HTTP_UNPROCESSABLE)),
            ),
        )

        private const val HTTP_SERVER_ERROR = 500
        private const val HTTP_UNPROCESSABLE = 422
    }
}
