package com.example.framegate.domain.queue

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class UploadEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var journalFile: File

    @Before
    fun setUp() {
        journalFile = tempFolder.newFile("journal.ndjson")
    }

    private fun newStore() = JournalQueueStore(journalFile)

    private fun sampleItem(id: String, key: String = "key-$id") = QueueItem(
        id = id,
        idempotencyKey = key,
        timestampEpochMillis = 1_000L,
        planName = "Plan Test",
        scaleFactorRaw = "1.0",
        orientation = 0,
        roi = SerializableRoi(0f, 0f, 1f, 1f),
        metrics = SerializableMetrics(50f, 100f, 0f, 5f),
    )

    @Test
    fun `respuesta 201 marca el item como COMPLETED`() = runBlocking {
        val store = newStore()
        store.put(sampleItem("a"))
        val engine = UploadEngine(store, FakeUploadTransport(), FakeClock())

        engine.drain()

        assertEquals(QueueItemStatus.COMPLETED, store.get("a")!!.status)
    }

    @Test
    fun `409 se trata como exito y nunca reintenta`() = runBlocking {
        val store = newStore()
        store.put(sampleItem("a"))
        val script = FailureScript(mapOf("key-a" to listOf(Outcome.Conflict)))
        val transport = FakeUploadTransport(script)
        val engine = UploadEngine(store, transport, FakeClock())

        engine.drain()

        assertEquals(QueueItemStatus.COMPLETED, store.get("a")!!.status)
        // Un solo intento: 409 no dispara reintento.
        assertEquals(1, transport.requestLog.count { it.idempotencyKey == "key-a" })
    }

    @Test
    fun `error de cliente 422 es terminal sin reintento automatico`() = runBlocking {
        val store = newStore()
        store.put(sampleItem("a"))
        val script = FailureScript(mapOf("key-a" to listOf(Outcome.Client(422))))
        val transport = FakeUploadTransport(script)
        val engine = UploadEngine(store, transport, FakeClock())

        engine.drain()

        assertEquals(QueueItemStatus.FAILED, store.get("a")!!.status)
        assertEquals(1, transport.requestLog.count { it.idempotencyKey == "key-a" })
    }

    @Test
    fun `error transitorio reintenta con backoff y luego tiene exito`() = runBlocking {
        val store = newStore()
        store.put(sampleItem("a"))
        // Falla 500 dos veces, luego almacena.
        val script = FailureScript(
            mapOf("key-a" to listOf(Outcome.Transient(500), Outcome.Transient(503), Outcome.Stored))
        )
        val transport = FakeUploadTransport(script)
        val clock = FakeClock()
        val engine = UploadEngine(store, transport, clock, retryPolicy = RetryPolicy(), random = { 0.0 })

        engine.drain()

        assertEquals(QueueItemStatus.COMPLETED, store.get("a")!!.status)
        assertEquals(3, transport.requestLog.count { it.idempotencyKey == "key-a" })
        // Hubo backoff (esperó tiempo virtual, sin dormir de verdad).
        assertTrue(clock.totalSleptMillis > 0)
    }

    @Test
    fun `al agotar intentos el item queda FAILED terminal`() = runBlocking {
        val store = newStore()
        store.put(sampleItem("a"))
        // Siempre 503: nunca almacena.
        val script = FailureScript(mapOf("key-a" to List(10) { Outcome.Transient(503) }))
        val transport = FakeUploadTransport(script)
        val engine = UploadEngine(
            store, transport, FakeClock(),
            retryPolicy = RetryPolicy(maxAttempts = 3), random = { 0.0 },
        )

        engine.drain()

        assertEquals(QueueItemStatus.FAILED, store.get("a")!!.status)
        assertEquals(3, transport.requestLog.count { it.idempotencyKey == "key-a" })
    }

    @Test
    fun `sobrevive a muerte del proceso y envia exactamente una vez`() = runBlocking {
        // 1) Primera "vida": encolar y simular que el proceso muere mientras subía.
        val store1 = newStore()
        store1.put(sampleItem("a"))
        // Forzar estado UPLOADING en el journal (subida en vuelo interrumpida).
        store1.put(store1.get("a")!!.copy(status = QueueItemStatus.UPLOADING, attempts = 1))

        // 2) Relanzar: un nuevo store relee el MISMO journal.
        val store2 = JournalQueueStore(journalFile)
        // El item in-flight fue degradado a PENDING.
        assertEquals(QueueItemStatus.PENDING, store2.get("a")!!.status)

        val transport = FakeUploadTransport()
        val engine = UploadEngine(store2, transport, FakeClock())
        engine.drain()

        assertEquals(QueueItemStatus.COMPLETED, store2.get("a")!!.status)
        // Nada duplicado: el servidor almacenó la clave una sola vez.
        assertEquals(1, transport.requestLog.count { it.idempotencyKey == "key-a" })
    }

    @Test
    fun `retry manual reactiva un item FAILED`() = runBlocking {
        val store = newStore()
        store.put(sampleItem("a").copy(status = QueueItemStatus.FAILED, lastError = "422"))
        val engine = UploadEngine(store, FakeUploadTransport(), FakeClock())

        engine.requestManualRetry("a")
        assertEquals(QueueItemStatus.PENDING, store.get("a")!!.status)

        engine.drain()
        assertEquals(QueueItemStatus.COMPLETED, store.get("a")!!.status)
    }
}
