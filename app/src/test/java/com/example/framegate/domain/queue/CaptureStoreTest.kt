package com.example.framegate.domain.queue

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CaptureStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `writes artifact and reads it back identically`() {
        val store = CaptureStore(tempFolder.newFolder("captures"))
        val bytes = byteArrayOf(1, 2, 3, 4, 5)

        val path = store.write("cap_1", bytes)

        assertTrue(File(path).exists())
        assertArrayEquals(bytes, store.read(path))
    }

    @Test
    fun `artifact survives to be uploaded after reopening store`() = runBlocking {
        val dir = tempFolder.newFolder("data")
        val journal = File(dir, "journal.ndjson")
        val captures = File(dir, "captures")

        // First lifetime: writes the artifact and queues record with its path.
        val store1 = JournalQueueStore(journal)
        val captureStore = CaptureStore(captures)
        val path = captureStore.write("cap_1", byteArrayOf(9, 9, 9))
        store1.put(
            QueueItem(
                id = "cap_1",
                idempotencyKey = "k1",
                timestampEpochMillis = 1L,
                planName = "P",
                scaleFactorRaw = "1.0",
                orientation = 0,
                roi = SerializableRoi(0f, 0f, 1f, 1f),
                metrics = SerializableMetrics(0f, 0f, 0f, 0f),
                artifactPath = path,
            )
        )

        // Second lifetime: new store initialized from same journal; artifact remains on disk.
        val store2 = JournalQueueStore(journal)
        val transport = FakeUploadTransport()
        UploadEngine(store2, transport, FakeClock(), CaptureStore(captures)).drain()

        assertEquals(QueueItemStatus.COMPLETED, store2.get("cap_1")!!.status)
        assertEquals(1, transport.requestLog.count { it.idempotencyKey == "k1" })
    }
}
