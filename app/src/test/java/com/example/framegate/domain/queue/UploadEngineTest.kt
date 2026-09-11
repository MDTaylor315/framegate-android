package com.example.framegate.domain.queue

import com.example.framegate.domain.model.Metrics
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

class UploadEngineTest {
    private lateinit var queueStore: PersistentQueueStore

    @Before
    fun setUp(){
        queueStore = PersistentQueueStore()
    }

    @Test
    fun `procesar envio exitoso cambia el estado a COMPLETED`() = runBlocking {
        val successTransport = SimulatedUploadTransport(simulatedDelayMs = 10L, failureProbability = 0.0f)
        val engine = UploadEngine(queueStore, successTransport)

        // Captura de prueba
        val item = QueueItem(
            id = "cap_001",
            timestampIso = "2026-09-11T12:00:00Z",
            planName = "Plan Test",
            metrics = Metrics(meanLuma = 100f, stdDev = 20f, rms = 100f)
        )

        queueStore.enqueue(item)
        engine.processPendingItems()

        val items = queueStore.getAllItems()
        assertEquals(1, items.size)
        assertEquals(QueueItemStatus.COMPLETED, items.first().status)
    }

    @Test
    fun `procesar envio fallido cambia el estado a FAILED e incrementa reintentos`() = runBlocking {
        // 100% de falla simulada (error 503)
        val failureTransport = SimulatedUploadTransport(simulatedDelayMs = 10L, failureProbability = 1.0f)
        val engine = UploadEngine(queueStore, failureTransport)
        val item = QueueItem(
            id = "cap_002",
            timestampIso = "2026-09-11T12:00:00Z",
            planName = "Plan Test",
            metrics = Metrics(meanLuma = 100f, stdDev = 20f, rms = 100f)
        )
        queueStore.enqueue(item)

        engine.processPendingItems()

        val items = queueStore.getAllItems()
        assertEquals(1, items.size)
        assertEquals(QueueItemStatus.FAILED, items.first().status)
        assertEquals(1, items.first().retryCount)
        assertTrue(items.first().lastError?.contains("503") == true)
    }

    @Test
    fun `clearCompleted remueve unicamente los elementos completados`() = runBlocking {
        val successTransport = SimulatedUploadTransport(simulatedDelayMs = 10L, failureProbability = 0.0f)
        val engine = UploadEngine(queueStore, successTransport)
        val item1 = QueueItem("cap_001", "2026-09-11T12:00:00Z", "Plan Test", Metrics(100f, 10f, 100f))
        val item2 = QueueItem("cap_002", "2026-09-11T12:00:00Z", "Plan Test", Metrics(100f, 10f, 100f))
        queueStore.enqueue(item1)
        queueStore.enqueue(item2)

        engine.processPendingItems()

        queueStore.clearCompleted()

        assertTrue(queueStore.getAllItems().isEmpty())
    }
}