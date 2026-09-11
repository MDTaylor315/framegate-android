package com.example.framegate.domain.queue

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

class PersistentQueueStore {
    private val itemsMap = ConcurrentHashMap<String, QueueItem>()

    private val _itemsFlow = MutableStateFlow<List<QueueItem>>(emptyList())
    val itemsFlow: StateFlow<List<QueueItem>> = _itemsFlow.asStateFlow()

    fun enqueue(item: QueueItem){
        itemsMap[item.id] = item
        notifyListeners()
    }

    fun getAllItems() : List<QueueItem> {
        return itemsMap.values.toList()
    }

    fun getPendingItems(): List<QueueItem> {
        return itemsMap.values.filter {
            it.status == QueueItemStatus.PENDING || it.status == QueueItemStatus.FAILED
        }
    }

    fun updateStatus(id: String, newStatus: QueueItemStatus, lastError: String? = null){
        val currentItem = itemsMap[id] ?: return
        val updatedItem = currentItem.copy(
            status = newStatus,
            retryCount = if(newStatus == QueueItemStatus.FAILED) currentItem.retryCount+1 else currentItem.retryCount,
            lastError = lastError
        )
        itemsMap[id] = updatedItem
        notifyListeners()
    }

    fun clearCompleted(){
        val completedIds = itemsMap.values.filter { it.status == QueueItemStatus.COMPLETED }.map { it.id }
        completedIds.forEach { id -> itemsMap.remove(id) }
        notifyListeners()
    }

    private fun notifyListeners() {
        _itemsFlow.value = itemsMap.values.toList()
    }
}