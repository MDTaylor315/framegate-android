package com.example.framegate.domain.gate

import com.example.framegate.domain.model.CapturePlan
import com.example.framegate.domain.model.Metrics

class GateStateMachine {
    private var currentStatus: GateStatus = GateStatus.Idle
    private var stableFrameCounter: Int = 0

    fun reset(){
        currentStatus = GateStatus.Idle
        stableFrameCounter = 0
    }

    fun arm(){
        currentStatus = GateStatus.Armed
        stableFrameCounter = 0
    }


    fun evaluate(
        metrics: Metrics,
        plan: CapturePlan,
        stepIndex: Int = 0
    ): GateResult{
        val step = plan.steps.getOrNull(stepIndex)
        if (step == null) {
            currentStatus = GateStatus.Failed("Paso de plan no encontrado")
            return GateResult(
                status = currentStatus,
                isGateOpen = false,
                metrics = metrics,
                statusText = "Error: Paso de plan no encontrado",
                currentStepIndex = stepIndex
            )
        }
       val minBrightness = step.thresholds.minBrightness.toFloat()
        val requiredStability = step.requiredHoldFrames

        val isLumaValid = metrics.meanLuma >= minBrightness

        when(currentStatus){
            is GateStatus.Idle -> {}
            is GateStatus.Armed, is GateStatus.WaitingForStability -> {
                if(isLumaValid){
                    stableFrameCounter++
                    if(stableFrameCounter >= requiredStability){
                        currentStatus = GateStatus.GateOpen
                    }else{
                        currentStatus = GateStatus.WaitingForStability(
                            currentStableFrames = stableFrameCounter,
                            requiredFrames = requiredStability
                        )
                    }
                }else{
                    stableFrameCounter = 0
                    currentStatus = GateStatus.Armed
                }
            }
            is GateStatus.GateOpen -> {
                currentStatus = GateStatus.Capturing
            }
            is GateStatus.Capturing -> {
                currentStatus = GateStatus.Completed
            }
            is GateStatus.Completed ->{}
            is GateStatus.Failed ->{}

        }
        val statusText = when (val s = currentStatus) {
            is GateStatus.Idle -> "Inactivo"
            is GateStatus.Armed -> "Armado - Esperando luz (mín: ${minBrightness.toInt()})"
            is GateStatus.WaitingForStability -> "Estabilizando luz (${s.currentStableFrames}/${s.requiredFrames})"
            is GateStatus.GateOpen -> "¡OBTURADOR ABIERTO!"
            is GateStatus.Capturing -> "Capturando imagen..."
            is GateStatus.Completed -> "Paso Completado Exitosamente"
            is GateStatus.Failed -> "Error: ${s.reason}"
        }
        return GateResult(
            status = currentStatus,
            isGateOpen = currentStatus is GateStatus.GateOpen || currentStatus is GateStatus.Capturing,
            metrics = metrics,
            statusText = statusText,
            currentStepIndex = stepIndex
        )

    }
}
