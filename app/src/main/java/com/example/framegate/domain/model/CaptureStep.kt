package com.example.framegate.domain.model

data class CaptureStep(
    val id: String,
    val type: StepType,
    val thresholds: Thresholds,
    val roi: NormalizedRoi,
    val requiredHoldFrames: Int = 5
)
