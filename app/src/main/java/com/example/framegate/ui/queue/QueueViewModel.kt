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
 * El estado se deriva de la cola persistida y del flag de drenaje con
 * combine(...).stateIn(...). Los efectos van por Channel, fuera del estado.
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
            // Más recientes primero, para no tener que desplazarse hasta el final.
            items = items.asReversed().map { it.toUiModel(uploadEngine.maxAttempts) },
        )
    }.stateIn(
        scope = viewModelScope,
        // Sigue activo unos segundos tras rotar para no reiniciar el estado.
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
        initialValue = QueueUiState(),
    )

    // RENDEZVOUS + trySend: un efecto emitido sin colector activo (p. ej. durante
    // la recreación por rotación) se descarta en vez de bufferizarse y reemitirse.
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
