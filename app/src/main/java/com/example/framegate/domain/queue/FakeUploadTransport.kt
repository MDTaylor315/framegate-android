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

    // Orden de aparición de cada clave, para guiones que fallan por posición
    // (útil en runtime, donde las claves son UUID y no se conocen de antemano).
    private val keyOrder = LinkedHashMap<String, Int>()

    override suspend fun uploadCapture(
        idempotencyKey: String,
        manifestJson: String,
        jpegBytes: ByteArray,
    ): UploadResult {
        val attempt = attemptCounts.merge(idempotencyKey, 1, Int::plus) ?: 1
        val keyIndex = keyOrder.getOrPut(idempotencyKey) { keyOrder.size }
        _requestLog.add(UploadRequest(idempotencyKey, attempt))

        // Clave ya almacenada -> el reenvío responde 409.
        if (stored.contains(idempotencyKey)) {
            return UploadResult.Success(HTTP_CONFLICT, "409 Conflict: clave ya almacenada")
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

/**
 * Respuesta a dar por intento. Admite dos modos: por clave concreta (tests, que
 * conocen las claves) y por orden de aparición de la clave (runtime, donde las
 * claves son UUID). Sin guion aplicable, almacena en el primer intento.
 */
class FailureScript(
    private val byKey: Map<String, List<Outcome>> = emptyMap(),
    private val byOrder: List<List<Outcome>> = emptyList(),
) {
    fun outcomeFor(key: String, keyIndex: Int, attempt: Int): Outcome {
        byKey[key]?.let { return it.getOrElse(attempt - 1) { Outcome.Stored } }
        // Modo por-orden cíclico: el patrón se repite cada byOrder.size claves, para que
        // cada corrida del loop muestre los mismos casos. Al agotar los intentos de un
        // guion se repite su último outcome (así un 422 sigue fallando en el retry manual).
        val outcomes = byOrder.getOrNull(keyIndex % byOrder.size.coerceAtLeast(1)) ?: emptyList()
        return outcomes.getOrElse(attempt - 1) { outcomes.lastOrNull() ?: Outcome.Stored }
    }

    companion object {
        fun empty(): FailureScript = FailureScript()

        /**
         * Guion de demostración para runtime: la 1ª captura sufre un error transitorio
         * que se recupera al reintentar (backoff), y la 2ª un error de cliente 422 que
         * queda terminal (el reintento manual también fallará).
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
