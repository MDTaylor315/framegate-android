package com.example.framegate.domain.model

data class CapturePlan(
    val name: String,
    val createdAtIso: String,
    val scaleFactorRaw: String,
    val steps: List<CaptureStep>
)
