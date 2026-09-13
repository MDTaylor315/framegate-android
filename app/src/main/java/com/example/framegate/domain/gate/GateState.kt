package com.example.framegate.domain.gate

/** Which measurement is being evaluated by the gate. Used to report blocking metrics. */
enum class Measurement { FOCUS, BRIGHTNESS, MOTION }

/**
 * Gate phase for a step:
 * - Blocked: one or more measurements fail; `failing` indicates which ones (for HUD).
 * - Holding: all pass; accumulating valid frames (held of N required).
 * - Armed: hold window completed; ready to fire capture.
 * - Fired: capture triggered for this step.
 * - Complete: no remaining steps.
 */
sealed interface GatePhase {
    data class Blocked(val failing: Set<Measurement>) : GatePhase
    data class Holding(val held: Int, val required: Int) : GatePhase
    data object Armed : GatePhase
    data object Fired : GatePhase
    data object Complete : GatePhase
}

/**
 * Immutable state of the gate. The reducer receives one state and produces another;
 * no hidden mutable state.
 */
data class GateState(
    val phase: GatePhase = GatePhase.Blocked(emptySet()),
    val stableFrames: Int = 0,
    val stepIndex: Int = 0,
    // Peak focus value observed; serves as reference for evaluating focus ratio.
    val focusBaseline: Float = 0f,
)

/** Verdict per measurement: pass/fail breakdown for a frame. */
data class Verdicts(
    val focusOk: Boolean,
    val brightnessOk: Boolean,
    val motionOk: Boolean,
) {
    val allPass: Boolean get() = focusOk && brightnessOk && motionOk

    val failing: Set<Measurement>
        get() = buildSet {
            if (!focusOk) add(Measurement.FOCUS)
            if (!brightnessOk) add(Measurement.BRIGHTNESS)
            if (!motionOk) add(Measurement.MOTION)
        }
}
