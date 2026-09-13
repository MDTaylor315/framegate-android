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
    fun `201 response marks item as COMPLETED`() = runBlocking {
        val store = newStore()
        store.put(sampleItem("a"))
        val engine = UploadEngine(store, FakeUploadTransport(), FakeClock())

        engine.drain()

        assertEquals(QueueItemStatus.COMPLETED, store.get("a")!!.status)
    }

    @Test
    fun `409 is treated as success and never retries`() = runBlocking {
        val store = newStore()
        store.put(sampleItem("a"))
        val script = FailureScript(mapOf("key-a" to listOf(Outcome.Conflict)))
        val transport = FakeUploadTransport(script)
        val engine = UploadEngine(store, transport, FakeClock())

        engine.drain()

        assertEquals(QueueItemStatus.COMPLETED, store.get("a")!!.status)
        // Single attempt: 409 does not trigger retries.
        assertEquals(1, transport.requestLog.count { it.idempotencyKey == "key-a" })
    }

    @Test
    fun `422 client error is terminal without automatic retry`() = runBlocking {
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
    fun `transient error retries with backoff and eventually succeeds`() = runBlocking {
        val store = newStore()
        store.put(sampleItem("a"))
        // Fails 500 twice, then succeeds.
        val script = FailureScript(
            mapOf("key-a" to listOf(Outcome.Transient(500), Outcome.Transient(503), Outcome.Stored))
        )
        val transport = FakeUploadTransport(script)
        val clock = FakeClock()
        val engine = UploadEngine(store, transport, clock, retryPolicy = RetryPolicy(), random = { 0.0 })

        engine.drain()

        assertEquals(QueueItemStatus.COMPLETED, store.get("a")!!.status)
        assertEquals(3, transport.requestLog.count { it.idempotencyKey == "key-a" })
        // Backoff occurred (simulated virtual sleep, no real time slept).
        assertTrue(clock.totalSleptMillis > 0)
    }

    @Test
    fun `when attempts are exhausted item becomes terminal FAILED`() = runBlocking {
        val store = newStore()
        store.put(sampleItem("a"))
        // Always 503: never succeeds.
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
    fun `survives process death and delivers exactly once`() = runBlocking {
        // 1) First lifetime: queue item and simulate process crash during upload.
        val store1 = newStore()
        store1.put(sampleItem("a"))
        // Force UPLOADING state in journal (interrupted in-flight upload).
        store1.put(store1.get("a")!!.copy(status = QueueItemStatus.UPLOADING, attempts = 1))

        // 2) Restart: new store replays the SAME journal.
        val store2 = JournalQueueStore(journalFile)
        // In-flight item was demoted to PENDING.
        assertEquals(QueueItemStatus.PENDING, store2.get("a")!!.status)

        val transport = FakeUploadTransport()
        val engine = UploadEngine(store2, transport, FakeClock())
        engine.drain()

        assertEquals(QueueItemStatus.COMPLETED, store2.get("a")!!.status)
        // No duplicates: server stored key exactly once.
        assertEquals(1, transport.requestLog.count { it.idempotencyKey == "key-a" })
    }

    @Test
    fun `manual retry reactivates a FAILED item`() = runBlocking {
        val store = newStore()
        store.put(sampleItem("a").copy(status = QueueItemStatus.FAILED, lastError = "422"))
        val engine = UploadEngine(store, FakeUploadTransport(), FakeClock())

        engine.requestManualRetry("a")
        assertEquals(QueueItemStatus.PENDING, store.get("a")!!.status)

        engine.drain()
        assertEquals(QueueItemStatus.COMPLETED, store.get("a")!!.status)
    }
}
