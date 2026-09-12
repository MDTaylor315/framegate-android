package com.example.framegate.domain.interfaces

import com.example.framegate.domain.model.UploadResult

interface UploadTransport {
    //Simula envio HTTP POST /v1/captures con un JSON y un JPEG
    suspend fun uploadCapture(
        idempotencyKey: String,
        manifestJson: String,
        jpegBytes: ByteArray
    ) : UploadResult
}
