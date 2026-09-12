package com.example.framegate.ui.capture

sealed interface CaptureUiEvent {
    data object StartCapture : CaptureUiEvent
    data object PauseCapture : CaptureUiEvent
    data object ToggleDiagnostics : CaptureUiEvent
}
