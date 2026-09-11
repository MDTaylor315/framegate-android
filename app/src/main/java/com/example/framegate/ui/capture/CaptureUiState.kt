package com.example.framegate.ui.capture

data class CaptureUiState(
    val isLoading: Boolean = false,
    val planName: String = "",
    val currentStepIndex: Int = 0,
    val totalSteps: Int = 0,
    val gateStatusText: String = "Inactivo",
    val fps: Float = 0f,
    val discardedFrames: Int = 0,
    val diagnostics: List<String> = emptyList(),
    val showDiagnosticsPanel: Boolean = false
)
