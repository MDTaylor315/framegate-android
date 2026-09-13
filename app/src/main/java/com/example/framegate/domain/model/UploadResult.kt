package com.example.framegate.domain.model

sealed interface UploadResult{

    // 201 Created or 409 Conflict
    data class Success(val statusCode: Int, val message: String) : UploadResult

    // 500, 503, Timeout, etc.
    data class TransientError(val statusCode: Int, val message: String) : UploadResult

    // 400, 422 Client-side error
    data class ClientError(val statusCode: Int, val message: String) : UploadResult
}
