package com.example.framegate.domain.queue

import com.example.framegate.domain.interfaces.UploadTransport
import com.example.framegate.domain.model.FrameData
import com.example.framegate.domain.model.UploadResult
import kotlinx.coroutines.delay
import kotlin.random.Random

class SimulatedUploadTransport(
    private val simulatedDelayMs: Long = 500L,
    private val failureProbability: Float = 0.2f
    ): UploadTransport{

    override suspend fun uploadCapture(
        idempotencyKey: String,
        manifestJson: String,
        jpegBytes: ByteArray
    ): UploadResult
    {
        delay(simulatedDelayMs)

        val shouldFail = Random.nextFloat() < failureProbability

        return if(shouldFail){
            UploadResult.TransientError(
                statusCode = 500,
                message ="Error simulado de red HTTP 503 (Timeout)")
        }else{
            UploadResult.Success(
                statusCode = 201,
                message = "Data subida exitosamente"

            )
        }
    }
}
