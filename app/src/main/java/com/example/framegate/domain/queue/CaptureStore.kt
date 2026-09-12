package com.example.framegate.domain.queue

import java.io.File

/**
 * Guarda el binario de cada captura en disco y devuelve su ruta; la cola guarda
 * la ruta, no los bytes. El directorio se inyecta para testear sin Android.
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
