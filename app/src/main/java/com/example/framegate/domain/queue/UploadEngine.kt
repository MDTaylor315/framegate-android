package com.example.framegate.domain.queue

import com.example.framegate.domain.interfaces.UploadTransport
import com.example.framegate.domain.model.UploadResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class UploadEngine (
    private val queueStore: PersistentQueueStore,
    private val uploadTransport: UploadTransport,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
){
    private var processingJob: Job? = null

    fun startProcessing(){
        if(processingJob?.isActive == true) return

        processingJob = scope.launch {
            processPendingItems()
        }
    }

    suspend fun processPendingItems(){
        val pendingItems = queueStore.getPendingItems()

        for(item in pendingItems){
            queueStore.updateStatus(item.id, QueueItemStatus.UPLOADING)

            val manifestJson = ManifestBuilder.buildManifestJson(
                frameId = item.id,
                planName = item.planName,
                timestampIso = item.timestampIso,
                metrics = item.metrics
            )

            val result = uploadTransport.uploadCapture(
                idempotencyKey = item.id,
                manifestJson = manifestJson,
                jpegBytes = ByteArray(0)
            )

            when (result) {
                is UploadResult.Success ->{
                    queueStore.updateStatus(item.id, QueueItemStatus.COMPLETED)
                }
                is UploadResult.TransientError -> {
                    queueStore.updateStatus(item.id, QueueItemStatus.FAILED, result.message)
                }
                is UploadResult.ClientError -> {
                    queueStore.updateStatus(item.id, QueueItemStatus.FAILED, result.message)
                }
            }
        }
    }

    fun stopProcessing(){
        processingJob?.cancel()
    }
}