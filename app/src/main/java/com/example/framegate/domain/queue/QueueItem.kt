package com.example.framegate.domain.queue

import com.example.framegate.domain.model.Metrics

enum class QueueItemStatus {
    PENDING,
    UPLOADING,
    COMPLETED,
    FAILED
}

data class QueueItem(
    val id: String,
    val timestampIso: String,
    val planName: String,
    val metrics: Metrics,
    val status: QueueItemStatus = QueueItemStatus.PENDING,
    val retryCount: Int = 0,
    val lastError: String? = null
)
