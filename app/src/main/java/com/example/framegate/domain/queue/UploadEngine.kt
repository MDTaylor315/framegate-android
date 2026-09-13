package com.example.framegate.domain.queue

import com.example.framegate.domain.interfaces.Clock
import com.example.framegate.domain.interfaces.UploadTransport
import com.example.framegate.domain.model.UploadResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Drains the queue sequentially applying server contract rules: 201/409 = success,
 * 400/422 = terminal failure with no retry, 500/503/timeout = transient retry with
 * backoff. [clock] is injected to allow testing backoff delays without real waiting.
 */
class UploadEngine(
    private val store: JournalQueueStore,
    private val transport: UploadTransport,
    private val clock: Clock,
    private val captureStore: CaptureStore? = null,
    private val retryPolicy: RetryPolicy = RetryPolicy(),
    private val random: () -> Double = Math::random,
) {
    // Max attempts allowed, used by UI to show "attempt N/max".
    val maxAttempts: Int get() = retryPolicy.maxAttempts

    // Serializes drain execution: even if triggered concurrently (capture flow and Queue UI),
    // only one drain loop processes the queue at a time, preventing duplicate uploads.
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
            // Attempt is persisted before the network call: if process dies here,
            // item remains UPLOADING and is re-queued as PENDING upon restart.
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
                        store.put(current.toFailed("Attempts exhausted. Last error: ${result.statusCode}"))
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
