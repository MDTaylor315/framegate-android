package com.example.framegate.ui.capture

sealed interface CaptureUiEffect {
    data class ShowToast(val message: String) : CaptureUiEffect
}
