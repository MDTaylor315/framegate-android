package com.example.framegate.ui.queue

data class QueueItemUiModel(
    val id: String,
    val timestamp: String,
    val statusText: String,
    val canRetry: Boolean
)

data class QueueUiState(
    val isLoading: Boolean = false,
    val items: List<QueueItemUiModel> = emptyList()
)
