package com.example.framegate.domain.gate

import com.example.framegate.domain.model.CapturePlan
import com.example.framegate.domain.model.Metrics
import com.example.framegate.domain.model.Thresholds

/**
 * Pure reducer: (state, metrics, plan) -> new state. Arms after N consecutive
 * valid frames; any failing frame resets the counter.
 */
object GateReducer {

    /**
     * Focus is evaluated as a ratio against [focusBaseline] (peak observed focus), not
     * as an absolute value: requires at least focusRatio of the best focus seen in the scene.
     */
    fun evaluate(metrics: Metrics, thresholds: Thresholds, focusBaseline: Float): Verdicts = Verdicts(
        focusOk = metrics.focus >= focusBaseline * thresholds.focusRatio,
        // Brightness requires sufficient mean luma and acceptable clipped pixel fraction.
        brightnessOk = metrics.meanLuma >= thresholds.minBrightness &&
            metrics.clippedFraction <= thresholds.maxClippedFraction,
        motionOk = metrics.motion <= thresholds.maxMotion,
    )

    fun reduce(state: GateState, metrics: Metrics, plan: CapturePlan): GateState {
        val step = plan.steps.getOrNull(state.stepIndex)
        val terminal = state.phase is GatePhase.Fired || state.phase is GatePhase.Complete

        return when {
            step == null -> state.copy(phase = GatePhase.Complete)
            terminal -> state
            else -> {
                // Baseline auto-calibrates with the peak focus observed.
                val baseline = maxOf(state.focusBaseline, metrics.focus)
                val verdicts = evaluate(metrics, step.thresholds, baseline)
                val withBaseline = state.copy(focusBaseline = baseline)
                if (verdicts.allPass) {
                    advanceHold(withBaseline, step.requiredHoldFrames)
                } else {
                    // Bad frame: resets the counter and reports which measurements failed.
                    withBaseline.copy(phase = GatePhase.Blocked(verdicts.failing), stableFrames = 0)
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

    /** Confirms firing of the armed step (side-effect handled by ViewModel). */
    fun fire(state: GateState): GateState =
        if (state.phase is GatePhase.Armed) state.copy(phase = GatePhase.Fired) else state

    /** Advances to the next step after firing; transitions to Complete if no steps remain. */
    fun advanceToNextStep(state: GateState, plan: CapturePlan): GateState {
        val next = state.stepIndex + 1
        return if (next < plan.steps.size) {
            GateState(phase = GatePhase.Blocked(emptySet()), stableFrames = 0, stepIndex = next)
        } else {
            state.copy(phase = GatePhase.Complete)
        }
    }
}
