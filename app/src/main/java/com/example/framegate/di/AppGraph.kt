package com.example.framegate.di

import com.example.framegate.domain.interfaces.Clock
import com.example.framegate.domain.interfaces.SystemClock
import com.example.framegate.domain.interfaces.UploadTransport
import com.example.framegate.domain.queue.FailureScript
import com.example.framegate.domain.queue.FakeUploadTransport
import com.example.framegate.domain.queue.JournalQueueStore
import com.example.framegate.domain.queue.UploadEngine
import java.io.File

/**
 * Contenedor de dependencias a mano. Comparte una única instancia del store, el
 * transporte, el reloj y el motor de subida entre pantallas. Se inicializa desde
 * la Activity con el directorio de la app.
 */
object AppGraph {

    @Volatile
    private var initialized = false

    lateinit var queueStore: JournalQueueStore
        private set

    lateinit var uploadEngine: UploadEngine
        private set

    private lateinit var transport: UploadTransport
    private lateinit var clock: Clock

    /** Inicializa el grafo con el directorio de archivos de la app. Idempotente. */
    @Synchronized
    fun init(filesDir: File) {
        if (initialized) return

        val journalFile = File(filesDir, "queue-journal.ndjson")
        queueStore = JournalQueueStore(journalFile)
        clock = SystemClock()
        // Sin guion de fallos: en la app real el transporte simplemente almacena.
        transport = FakeUploadTransport(FailureScript.empty())
        uploadEngine = UploadEngine(queueStore, transport, clock)

        initialized = true
    }
}
