package com.example.framegate.domain.model

sealed interface UploadResult{

    // 201 Created o 409 Conflict
    data class Success(val statusCode: Int, val message: String) : UploadResult

    // 500, 503 Timeout
    data class TransientError(val statusCode: Int, val message: String) : UploadResult

    //400, 422 Error por parte del cliente
    data class ClientError(val statusCode: Int, val message: String) : UploadResult
}