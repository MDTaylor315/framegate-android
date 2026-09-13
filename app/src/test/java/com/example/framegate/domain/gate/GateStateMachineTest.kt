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

    // Umbrales: foco>=60% del baseline, brillo>=50, movimiento<=15. Hold de 3 frames.
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

    @Test
    fun `un frame bueno pasa a Holding 1 de N`() {
        val state = GateReducer.reduce(GateState(), good(), plan)
        assertEquals(GatePhase.Holding(1, 3), state.phase)
    }

    @Test
    fun `un frame borroso respecto al baseline bloquea reportando FOCUS`() {
        // Primero un frame nítido fija el baseline alto; luego el borroso cae bajo el ratio.
        var state = GateReducer.reduce(GateState(), good(), plan)  // baseline = 100
        state = GateReducer.reduce(state, blurry(), plan)          // 1 < 100*0.6 -> falla foco
        assertTrue(state.phase is GatePhase.Blocked)
        assertEquals(setOf(Measurement.FOCUS), (state.phase as GatePhase.Blocked).failing)
    }

    @Test
    fun `el primer frame no puede estar borroso porque es su propio baseline`() {
        // Sin referencia previa, el foco se compara consigo mismo y pasa.
        val state = GateReducer.reduce(GateState(), blurry(), plan)
        assertEquals(GatePhase.Holding(1, 3), state.phase)
    }

    @Test
    fun `un frame movido bloquea reportando MOTION`() {
        val state = GateReducer.reduce(GateState(), shaky(), plan)
        assertEquals(setOf(Measurement.MOTION), (state.phase as GatePhase.Blocked).failing)
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
