package com.example.framegate.domain.gate

import com.example.framegate.domain.model.CapturePlan
import com.example.framegate.domain.model.CaptureStep
import com.example.framegate.domain.model.Metrics
import com.example.framegate.domain.model.NormalizedRoi
import com.example.framegate.domain.model.StepType
import com.example.framegate.domain.model.Thresholds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GateStateMachineTest {

    // Thresholds: focus >= 60% of baseline, brightness >= 50, motion <= 15. Hold window of 3 frames.
    private val step = CaptureStep(
        id = "s1",
        type = StepType.SINGLE_FRAME,
        thresholds = Thresholds(focusRatio = 0.6, minBrightness = 50.0, maxMotion = 15.0),
        roi = NormalizedRoi(),
        requiredHoldFrames = 3,
    )
    private val plan = CapturePlan("p", "2026-01-01T00:00:00Z", "1.0", listOf(step))

    private fun good() = Metrics(focus = 100f, meanLuma = 100f, clippedFraction = 0f, motion = 2f)
    private fun blurry() = Metrics(focus = 1f, meanLuma = 100f, clippedFraction = 0f, motion = 2f)
    private fun shaky() = Metrics(focus = 100f, meanLuma = 100f, clippedFraction = 0f, motion = 40f)

    // Sufficient luma but too many clipped/blown-out pixels: brightness is not usable.
    private fun clipped() = Metrics(focus = 100f, meanLuma = 100f, clippedFraction = 0.9f, motion = 2f)

    @Test
    fun `a single good frame transitions to Holding 1 of N`() {
        val state = GateReducer.reduce(GateState(), good(), plan)
        assertEquals(GatePhase.Holding(1, 3), state.phase)
    }

    @Test
    fun `a blurry frame relative to baseline blocks reporting FOCUS failure`() {
        // First a sharp frame sets a high baseline; subsequent blurry frame falls below ratio requirement.
        var state = GateReducer.reduce(GateState(), good(), plan)  // baseline = 100
        state = GateReducer.reduce(state, blurry(), plan)          // 1 < 100*0.6 -> focus fails
        assertTrue(state.phase is GatePhase.Blocked)
        assertEquals(setOf(Measurement.FOCUS), (state.phase as GatePhase.Blocked).failing)
    }

    @Test
    fun `first frame cannot be blurry because it sets its own initial baseline`() {
        // Without prior reference, focus is compared against itself and passes.
        val state = GateReducer.reduce(GateState(), blurry(), plan)
        assertEquals(GatePhase.Holding(1, 3), state.phase)
    }

    @Test
    fun `shaky frame blocks reporting MOTION failure`() {
        val state = GateReducer.reduce(GateState(), shaky(), plan)
        assertEquals(setOf(Measurement.MOTION), (state.phase as GatePhase.Blocked).failing)
    }

    @Test
    fun `frame with excessive clipping blocks reporting BRIGHTNESS failure despite adequate mean luma`() {
        val state = GateReducer.reduce(GateState(), clipped(), plan)
        assertEquals(setOf(Measurement.BRIGHTNESS), (state.phase as GatePhase.Blocked).failing)
    }

    @Test
    fun `after arming and firing advances to next step or completes`() {
        var state = GateState()
        repeat(3) { state = GateReducer.reduce(state, good(), plan) } // Armed
        state = GateReducer.fire(state)
        assertEquals(GatePhase.Fired, state.phase)
        state = GateReducer.advanceToNextStep(state, plan)
        // Only one step in plan -> Complete.
        assertEquals(GatePhase.Complete, state.phase)
    }
}
