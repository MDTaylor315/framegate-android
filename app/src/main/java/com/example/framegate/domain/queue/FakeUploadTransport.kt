package com.example.framegate.domain.queue

import com.example.framegate.domain.interfaces.UploadTransport
import com.example.framegate.domain.model.UploadResult
import java.util.concurrent.ConcurrentHashMap

/**
 * Transporte falso con guion de fallos determinístico por registro. El
 * [requestLog] es la evidencia para verificar el "envío exactamente una vez".
 */
class FakeUploadTransport(
    private val script: FailureScript = FailureScript.empty(),
) : UploadTransport {

    private val attemptCounts = ConcurrentHashMap<String, Int>()
    private val _requestLog = mutableListOf<UploadRequest>()
    val requestLog: List<UploadRequest> get() = _requestLog.toList()

    private val stored = mutableSetOf<String>()

    override suspend fun uploadCapture(
        idempotencyKey: String,
        manifestJson: String,
        jpegBytes: ByteArray,
    ): UploadResult {
        val attempt = attemptCounts.merge(idempotencyKey, 1, Int::plus) ?: 1
        _requestLog.add(UploadRequest(idempotencyKey, attempt))

        // Clave ya almacenada -> el reenvío responde 409.
        if (stored.contains(idempotencyKey)) {
            return UploadResult.Success(HTTP_CONFLICT, "409 Conflict: clave ya almacenada")
        }

        return when (val outcome = script.outcomeFor(idempotencyKey, attempt)) {
            is Outcome.Stored -> {
                stored.add(idempotencyKey)
                UploadResult.Success(HTTP_CREATED, "201 Created")
            }
            is Outcome.Conflict -> {
                stored.add(idempotencyKey)
                UploadResult.Success(HTTP_CONFLICT, "409 Conflict")
            }
            is Outcome.Transient -> UploadResult.TransientError(outcome.code, "Error transitorio ${outcome.code}")
            is Outcome.Client -> UploadResult.ClientError(outcome.code, "Error de cliente ${outcome.code}")
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

/** Respuesta a dar por clave e intento. Sin guion, almacena en el primer intento. */
class FailureScript(
    private val scripts: Map<String, List<Outcome>> = emptyMap(),
) {
    fun outcomeFor(key: String, attempt: Int): Outcome {
        val outcomes = scripts[key] ?: return Outcome.Stored
        return outcomes.getOrElse(attempt - 1) { Outcome.Stored }
    }

    companion object {
        fun empty(): FailureScript = FailureScript()
    }
}
