package com.example.framegate.ui.capture

sealed interface CaptureUiEvent {
    data object StartCapture : CaptureUiEvent
    data object StopCapture : CaptureUiEvent
    data object CycleCameraConfig : CaptureUiEvent
    data class ViewSizeChanged(val width: Int, val height: Int) : CaptureUiEvent
}
