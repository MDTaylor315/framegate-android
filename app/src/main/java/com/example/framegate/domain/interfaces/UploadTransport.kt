package com.example.framegate.domain.interfaces

import com.example.framegate.domain.model.UploadResult

interface UploadTransport {
    // Simulates HTTP POST /v1/captures request carrying a JSON manifest and a JPEG payload
    suspend fun uploadCapture(
        idempotencyKey: String,
        manifestJson: String,
        jpegBytes: ByteArray
    ) : UploadResult
}
