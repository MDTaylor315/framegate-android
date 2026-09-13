package com.example.framegate.domain.queue

import java.io.File

/**
 * Persists raw capture binary data to disk and returns its file path; the queue stores
 * file paths rather than raw bytes. The target directory is injected for testability without Android dependencies.
 */
class CaptureStore(private val dir: File) {

    fun write(id: String, bytes: ByteArray): String {
        dir.mkdirs()
        val file = File(dir, "$id.bin")
        file.writeBytes(bytes)
        return file.absolutePath
    }

    fun read(path: String): ByteArray {
        val file = File(path)
        return if (file.exists()) file.readBytes() else ByteArray(0)
    }
}
