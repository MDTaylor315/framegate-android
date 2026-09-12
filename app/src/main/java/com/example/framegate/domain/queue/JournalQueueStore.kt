package com.example.framegate.domain.queue

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Cola persistida como journal append-only: cada cambio de estado es una línea
 * JSON al final del archivo, y el estado se reconstruye reproduciendo el archivo
 * (la última línea de cada id gana). El [journalFile] se inyecta para poder
 * testear la durabilidad sin Android.
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

        // Una subida interrumpida por el cierre de la app queda in-flight; se
        // reencola para que se reintente. Solo estos casos se reescriben.
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
