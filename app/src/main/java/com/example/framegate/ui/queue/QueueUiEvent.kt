package com.example.framegate.ui.queue

sealed interface QueueUiEvent {
    data class RetryUpload(val recordId: String) : QueueUiEvent
    data object Refresh: QueueUiEvent
}