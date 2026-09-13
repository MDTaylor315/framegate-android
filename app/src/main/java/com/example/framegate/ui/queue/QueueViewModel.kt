package com.example.framegate.ui.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.framegate.di.AppGraph
import com.example.framegate.domain.queue.JournalQueueStore
import com.example.framegate.domain.queue.QueueItem
import com.example.framegate.domain.queue.QueueItemStatus
import com.example.framegate.domain.queue.UploadEngine
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * UI state is derived from the persistent queue store and drain status via
 * combine(...).stateIn(...). One-shot UI effects use Channel, separate from state.
 */
class QueueViewModel(
    private val queueStore: JournalQueueStore = AppGraph.queueStore,
    private val uploadEngine: UploadEngine = AppGraph.uploadEngine,
) : ViewModel() {

    private val _isDraining = MutableStateFlow(false)

    val uiState: StateFlow<QueueUiState> = combine(
        queueStore.itemsFlow,
        _isDraining,
    ) { items, isDraining ->
        QueueUiState(
            isLoading = isDraining,
            // Most recent items first, eliminating the need to scroll down.
            items = items.asReversed().map { it.toUiModel(uploadEngine.maxAttempts) },
        )
    }.stateIn(
        scope = viewModelScope,
        // Remains active for a few seconds post-rotation to avoid resetting state.
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
        initialValue = QueueUiState(),
    )

    // RENDEZVOUS + trySend: an effect emitted without an active collector (e.g., during
    // Activity rotation recreation) is dropped rather than buffered and re-emitted.
    private val _uiEffect = Channel<QueueUiEffect>(Channel.RENDEZVOUS)
    val uiEffect = _uiEffect.receiveAsFlow()

    fun onEvent(event: QueueUiEvent) {
        when (event) {
            is QueueUiEvent.RetryUpload -> retryUpload(event.recordId)
            is QueueUiEvent.Refresh -> drain()
        }
    }

    private fun retryUpload(recordId: String) {
        uploadEngine.requestManualRetry(recordId)
        drain()
        sendEffect(QueueUiEffect.ShowToast("Reintentando subida de $recordId"))
    }

    private fun drain() {
        viewModelScope.launch {
            _isDraining.value = true
            try {
                uploadEngine.drain()
            } finally {
                _isDraining.value = false
            }
        }
    }

    private fun sendEffect(effect: QueueUiEffect) {
        _uiEffect.trySend(effect)
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MS = 5000L
    }
}

private fun QueueItem.toUiModel(maxAttempts: Int): QueueItemUiModel = QueueItemUiModel(
    id = id,
    timestamp = timestampEpochMillis.toString(),
    statusText = when (status) {
        QueueItemStatus.PENDING -> if (attempts > 0) "Reintentando (intento $attempts/$maxAttempts)" else "Pendiente"
        QueueItemStatus.UPLOADING -> "Subiendo... (intento $attempts/$maxAttempts)"
        QueueItemStatus.COMPLETED -> "Completado"
        QueueItemStatus.FAILED -> "Error: ${lastError ?: "falló"}"
    },
    canRetry = status == QueueItemStatus.FAILED,
)
