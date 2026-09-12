package com.example.framegate.domain.queue

import com.example.framegate.domain.model.Metrics
import kotlinx.serialization.Serializable

// PENDING -> UPLOADING -> COMPLETED; ante fallo -> PENDING (reintento) o FAILED.
enum class QueueItemStatus {
    PENDING,
    UPLOADING,
    COMPLETED,
    FAILED;

    val isTerminal: Boolean
        get() = this == COMPLETED || this == FAILED
}

/**
 * Registro de captura encolado. [idempotencyKey] la genera el cliente y es
 * estable durante toda la vida del registro (incluso tras un crash), de modo que
 * el servidor pueda deduplicar y se suba exactamente una vez.
 */
@Serializable
data class QueueItem(
    val id: String,
    val idempotencyKey: String,
    val timestampEpochMillis: Long,
    val planName: String,
    val scaleFactorRaw: String,
    val orientation: Int,
    val roi: SerializableRoi,
    val metrics: SerializableMetrics,
    val status: QueueItemStatus = QueueItemStatus.PENDING,
    val attempts: Int = 0,
    val lastError: String? = null,
)

/** ROI normalizado, serializable para el journal y el manifest. */
@Serializable
data class SerializableRoi(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)

/** Métricas serializables para el journal y el manifest. */
@Serializable
data class SerializableMetrics(
    val meanLuma: Float,
    val stdDev: Float,
    val rms: Float,
) {
    companion object {
        fun from(metrics: Metrics): SerializableMetrics =
            SerializableMetrics(metrics.meanLuma, metrics.stdDev, metrics.rms)
    }
}
