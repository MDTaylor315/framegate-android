package com.example.framegate.domain.queue

import com.example.framegate.domain.interfaces.Clock
import com.example.framegate.domain.interfaces.UploadTransport
import com.example.framegate.domain.model.UploadResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Drena la cola en serie aplicando el contrato del servidor: 201/409 = éxito,
 * 400/422 = fallo terminal sin reintento, 500/503/timeout = reintento con
 * backoff. El [clock] se inyecta para testear el backoff sin esperas reales.
 */
class UploadEngine(
    private val store: JournalQueueStore,
    private val transport: UploadTransport,
    private val clock: Clock,
    private val captureStore: CaptureStore? = null,
    private val retryPolicy: RetryPolicy = RetryPolicy(),
    private val random: () -> Double = Math::random,
) {
    // Tope de intentos, para que la UI pueda mostrar "intento N/max".
    val maxAttempts: Int get() = retryPolicy.maxAttempts

    // Serializa el drenaje: aunque se invoque desde varios sitios (captura y Queue),
    // solo una pasada procesa la cola a la vez, evitando enviar un item dos veces.
    private val drainMutex = Mutex()

    suspend fun drain() = drainMutex.withLock {
        for (item in store.getPending()) {
            processItem(item)
        }
    }

    private suspend fun processItem(initial: QueueItem) {
        var current = initial
        var draining = true

        while (draining) {
            // Se persiste el intento antes de la red: si el proceso muere aquí,
            // el item queda UPLOADING y al relanzar se reencola.
            current = current.copy(
                status = QueueItemStatus.UPLOADING,
                attempts = current.attempts + 1,
            )
            store.put(current)

            val jpegBytes = captureStore?.read(current.artifactPath) ?: ByteArray(0)
            val result = transport.uploadCapture(
                idempotencyKey = current.idempotencyKey,
                manifestJson = ManifestBuilder.build(current),
                jpegBytes = jpegBytes,
            )

            when (result) {
                is UploadResult.Success -> {
                    store.put(current.copy(status = QueueItemStatus.COMPLETED, lastError = null))
                    draining = false
                }

                is UploadResult.ClientError -> {
                    store.put(current.toFailed("${result.statusCode}: ${result.message}"))
                    draining = false
                }

                is UploadResult.TransientError -> {
                    if (retryPolicy.canRetry(current.attempts)) {
                        store.put(
                            current.copy(
                                status = QueueItemStatus.PENDING,
                                lastError = "${result.statusCode}: ${result.message}",
                            )
                        )
                        clock.sleep(retryPolicy.delayForAttempt(current.attempts, random))
                        current = store.get(current.id) ?: current.also { draining = false }
                    } else {
                        store.put(current.toFailed("Intentos agotados. Último error ${result.statusCode}"))
                        draining = false
                    }
                }
            }
        }
    }

    private fun QueueItem.toFailed(error: String): QueueItem =
        copy(status = QueueItemStatus.FAILED, lastError = error)

    fun requestManualRetry(id: String) {
        val item = store.get(id) ?: return
        if (item.status == QueueItemStatus.FAILED) {
            store.put(item.copy(status = QueueItemStatus.PENDING, lastError = null))
        }
    }
}
