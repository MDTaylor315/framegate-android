package com.example.framegate.domain.queue

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Persistent queue backed by an append-only journal file: each state change writes
 * a single JSON line to the end of the file, and current state is rebuilt by replaying
 * lines in order (last entry for a given ID wins). [journalFile] is injected to allow
 * testing durability without Android dependencies.
 */
class JournalQueueStore(
    private val journalFile: File,
    private val json: Json = Json,
) {
    private val items = LinkedHashMap<String, QueueItem>()

    private val _itemsFlow = MutableStateFlow<List<QueueItem>>(emptyList())
    val itemsFlow: StateFlow<List<QueueItem>> = _itemsFlow.asStateFlow()

    init {
        recover()
    }

    private fun recover() {
        items.clear()
        if (journalFile.exists()) {
            journalFile.forEachLine { line ->
                if (line.isNotBlank()) {
                    val item = json.decodeFromString(QueueItem.serializer(), line)
                    items[item.id] = item
                }
            }
        }

        // Uploads interrupted by app termination remain in-flight (UPLOADING);
        // re-queue them as PENDING for retry. Only these cases trigger rewritten updates.
        val interrupted = items.values.filter { it.status == QueueItemStatus.UPLOADING }
        interrupted.forEach { put(it.copy(status = QueueItemStatus.PENDING)) }

        notifyListeners()
    }

    fun put(item: QueueItem) {
        appendLine(item)
        items[item.id] = item
        notifyListeners()
    }

    fun getAll(): List<QueueItem> = items.values.toList()

    fun getPending(): List<QueueItem> =
        items.values.filter { it.status == QueueItemStatus.PENDING }

    fun get(id: String): QueueItem? = items[id]

    private fun appendLine(item: QueueItem) {
        journalFile.parentFile?.mkdirs()
        journalFile.appendText(json.encodeToString(QueueItem.serializer(), item) + "\n")
    }

    private fun notifyListeners() {
        _itemsFlow.value = items.values.toList()
    }
}
