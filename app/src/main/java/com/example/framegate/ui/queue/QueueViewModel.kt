package com.example.framegate.ui.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.framegate.di.AppGraph
import com.example.framegate.domain.model.Metrics
import com.example.framegate.domain.queue.PersistentQueueStore
import com.example.framegate.domain.queue.QueueItem
import com.example.framegate.domain.queue.QueueItemStatus
import com.example.framegate.domain.queue.SimulatedUploadTransport
import com.example.framegate.domain.queue.UploadEngine
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class QueueViewModel(
    private val queueStore: PersistentQueueStore = AppGraph.queueStore,
    private val uploadTransport: SimulatedUploadTransport = SimulatedUploadTransport()
) : ViewModel(){

    private val _uiState = MutableStateFlow(QueueUiState())
    val uiState: StateFlow<QueueUiState> = _uiState.asStateFlow()

    private val _uiEffect = Channel<QueueUiEffect>(Channel.BUFFERED)

    private val uploadEngine = UploadEngine(queueStore, uploadTransport)
    val uiEffect = _uiEffect.receiveAsFlow()

    init {
        observeQueue()
    }

    private fun observeQueue(){
        viewModelScope.launch {
            queueStore.itemsFlow.collect{ items ->
                val uiModels = items.map{ item ->
                    QueueItemUiModel(
                        id = item.id,
                        timestamp = item.timestampIso,
                        statusText = when(item.status){
                            QueueItemStatus.PENDING -> "Pendiente"
                            QueueItemStatus.UPLOADING -> "Subiendo..."
                            QueueItemStatus.COMPLETED -> "Completado exitosamente"
                            QueueItemStatus.FAILED -> "Error: ${item.lastError ?: "Falló"}"
                        },
                        canRetry = item.status == QueueItemStatus.FAILED
                    )
                }
                _uiState.value = QueueUiState(isLoading = false,items = uiModels)
            }
        }
    }

    fun onEvent(event: QueueUiEvent){
        when(event){
            is QueueUiEvent.RetryUpload -> retryUpload(event.recordId)
            is QueueUiEvent.Refresh -> refreshQueue()
        }
    }

    private fun retryUpload(recordId: String) {
        if (recordId == "demo_failed") {
            // Si presionó el botón superior, agrega 1 nuevo elemento fallido de prueba
            val demoItem = QueueItem(
                id = "cap_demo_${System.currentTimeMillis() % 1000}",
                timestampIso = "2026-09-11T12:00:00Z",
                planName = "Plan Demo",
                metrics = Metrics(80f, 15f, 80f),
                status = QueueItemStatus.FAILED,
                retryCount = 1,
                lastError = "503 Error simulado de red"
            )
            queueStore.enqueue(demoItem)
            sendEffect(QueueUiEffect.ShowToast("Elemento fallido de prueba agregado"))
        } else {
            queueStore.updateStatus(recordId, QueueItemStatus.PENDING, lastError = null)
            uploadEngine.startProcessing()
            sendEffect(QueueUiEffect.ShowToast("Reintentando subida de $recordId..."))
        }
    }



    private fun refreshQueue(){
        sendEffect(QueueUiEffect.ShowToast("Cola actualizada"))
    }

    private fun sendEffect(effect: QueueUiEffect){
        viewModelScope.launch {
            _uiEffect.send(effect)
        }
    }
}