package com.example.framegate.ui.queue

sealed interface QueueUiEffect {
    data class ShowToast(val message: String) : QueueUiEffect
}
