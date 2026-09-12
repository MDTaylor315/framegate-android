package com.example.framegate.domain.gate

import com.example.framegate.domain.model.CapturePlan
import com.example.framegate.domain.model.Metrics
import com.example.framegate.domain.model.Thresholds

/**
 * Reducer puro del gate: dado el estado actual, las métricas del frame y el plan,
 * devuelve el nuevo estado. No guarda estado interno.
 *
 * El obturador solo se arma tras N frames consecutivos en que las tres
 * mediciones pasan. Cualquier frame que falle resetea el contador (así son N
 * consecutivos reales, contados en frames, no en tiempo).
 */
object GateReducer {

    fun evaluate(metrics: Metrics, thresholds: Thresholds): Verdicts = Verdicts(
        focusOk = metrics.focus >= thresholds.minFocus,
        brightnessOk = metrics.meanLuma >= thresholds.minBrightness,
        motionOk = metrics.motion <= thresholds.maxMotion,
    )

    fun reduce(state: GateState, metrics: Metrics, plan: CapturePlan): GateState {
        val step = plan.steps.getOrNull(state.stepIndex)
        val terminal = state.phase is GatePhase.Fired || state.phase is GatePhase.Complete

        return when {
            step == null -> state.copy(phase = GatePhase.Complete)
            terminal -> state
            else -> {
                val verdicts = evaluate(metrics, step.thresholds)
                if (verdicts.allPass) {
                    advanceHold(state, step.requiredHoldFrames)
                } else {
                    // Frame malo: resetea el contador y reporta qué mediciones fallan.
                    state.copy(phase = GatePhase.Blocked(verdicts.failing), stableFrames = 0)
                }
            }
        }
    }

    private fun advanceHold(state: GateState, required: Int): GateState {
        val held = state.stableFrames + 1
        return if (held >= required) {
            state.copy(phase = GatePhase.Armed, stableFrames = held)
        } else {
            state.copy(phase = GatePhase.Holding(held, required), stableFrames = held)
        }
    }

    /** Confirma el disparo del paso armado (efecto del lado del ViewModel). */
    fun fire(state: GateState): GateState =
        if (state.phase is GatePhase.Armed) state.copy(phase = GatePhase.Fired) else state

    /** Avanza al siguiente paso tras disparar; Complete si no quedan más. */
    fun advanceToNextStep(state: GateState, plan: CapturePlan): GateState {
        val next = state.stepIndex + 1
        return if (next < plan.steps.size) {
            GateState(phase = GatePhase.Blocked(emptySet()), stableFrames = 0, stepIndex = next)
        } else {
            state.copy(phase = GatePhase.Complete)
        }
    }
}
