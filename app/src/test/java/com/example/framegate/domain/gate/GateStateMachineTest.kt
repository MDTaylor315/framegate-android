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

    // Umbrales: foco>=10, brillo>=50, movimiento<=15. Hold de 3 frames.
    private val step = CaptureStep(
        id = "s1",
        type = StepType.SINGLE_FRAME,
        thresholds = Thresholds(minFocus = 10.0, minBrightness = 50.0, maxMotion = 15.0),
        roi = NormalizedRoi(),
        requiredHoldFrames = 3,
    )
    private val plan = CapturePlan("p", "2026-01-01T00:00:00Z", "1.0", listOf(step))

    private fun good() = Metrics(focus = 100f, meanLuma = 100f, clippedFraction = 0f, motion = 2f)
    private fun blurry() = Metrics(focus = 1f, meanLuma = 100f, clippedFraction = 0f, motion = 2f)
    private fun shaky() = Metrics(focus = 100f, meanLuma = 100f, clippedFraction = 0f, motion = 40f)

    @Test
    fun `un frame bueno pasa a Holding 1 de N`() {
        val state = GateReducer.reduce(GateState(), good(), plan)
        assertEquals(GatePhase.Holding(1, 3), state.phase)
    }

    @Test
    fun `N frames buenos consecutivos arman el gate`() {
        var state = GateState()
        repeat(3) { state = GateReducer.reduce(state, good(), plan) }
        assertEquals(GatePhase.Armed, state.phase)
    }

    @Test
    fun `un frame borroso bloquea reportando FOCUS`() {
        val state = GateReducer.reduce(GateState(), blurry(), plan)
        assertTrue(state.phase is GatePhase.Blocked)
        assertEquals(setOf(Measurement.FOCUS), (state.phase as GatePhase.Blocked).failing)
    }

    @Test
    fun `un frame movido bloquea reportando MOTION`() {
        val state = GateReducer.reduce(GateState(), shaky(), plan)
        assertEquals(setOf(Measurement.MOTION), (state.phase as GatePhase.Blocked).failing)
    }

    @Test
    fun `un frame malo resetea el contador de hold`() {
        var state = GateState()
        state = GateReducer.reduce(state, good(), plan)   // 1/3
        state = GateReducer.reduce(state, good(), plan)   // 2/3
        state = GateReducer.reduce(state, blurry(), plan) // falla -> reset
        assertEquals(0, state.stableFrames)
        assertTrue(state.phase is GatePhase.Blocked)
    }

    @Test
    fun `secuencia jitter no arma antes de N consecutivos`() {
        var state = GateState()
        // bueno, bueno, malo, bueno, bueno, malo... nunca 3 seguidos.
        val sequence = listOf(good(), good(), blurry(), good(), good(), shaky(), good(), good())
        sequence.forEach { state = GateReducer.reduce(state, it, plan) }
        // Tras la secuencia inestable, aún no llegó a 3 consecutivos.
        assertTrue(state.phase !is GatePhase.Armed)
    }

    @Test
    fun `tras armar y disparar avanza al siguiente paso o completa`() {
        var state = GateState()
        repeat(3) { state = GateReducer.reduce(state, good(), plan) } // Armed
        state = GateReducer.fire(state)
        assertEquals(GatePhase.Fired, state.phase)
        state = GateReducer.advanceToNextStep(state, plan)
        // Solo hay un paso -> Complete.
        assertEquals(GatePhase.Complete, state.phase)
    }
}
