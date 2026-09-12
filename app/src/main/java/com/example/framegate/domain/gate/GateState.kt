package com.example.framegate.domain.gate

import com.example.framegate.domain.model.Metrics

sealed class GateStatus {
    object Idle: GateStatus()
    object Armed : GateStatus()
    data class WaitingForStability(val currentStableFrames: Int, val requiredFrames: Int) : GateStatus()
    object GateOpen : GateStatus()
    object Capturing : GateStatus()
    object Completed : GateStatus()
    data class Failed(val reason: String) : GateStatus()
}

data class GateResult(
    val status: GateStatus,
    val isGateOpen: Boolean,
    val metrics: Metrics,
    val statusText: String,
    val currentStepIndex: Int = 0
)
