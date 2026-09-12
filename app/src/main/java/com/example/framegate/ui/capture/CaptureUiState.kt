package com.example.framegate.ui.capture

data class CaptureUiState(
    val gateStatusText: String = "Inactivo",
    val currentStepIndex: Int = 0,
    val fps: Float = 0f,
    // Mediciones del último frame, para el HUD.
    val focus: Float = 0f,
    val brightness: Float = 0f,
    val motion: Float = 0f,
    // Rendimiento del hot path.
    val msPerFrame: Float = 0f,
    val droppedFrames: Int = 0,
)
