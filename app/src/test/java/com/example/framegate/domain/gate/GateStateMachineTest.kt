package com.example.framegate.domain.gate

import com.example.framegate.domain.model.CapturePlan
import com.example.framegate.domain.model.CaptureStep
import com.example.framegate.domain.model.Metrics
import com.example.framegate.domain.model.NormalizedRoi
import com.example.framegate.domain.model.StepType
import com.example.framegate.domain.model.Thresholds
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Test

class GateStateMachineTest {

    private lateinit var gateStateMachine: GateStateMachine
    private lateinit var mockPlan: CapturePlan

    @Before
    fun setUp() {
        gateStateMachine = GateStateMachine()

        // Plan simulado que requiere minBrightness = 50.0 y requiredHoldFrames = 2
        val step = CaptureStep(
            id = "step_1",
            type = StepType.SINGLE_FRAME,
            thresholds = Thresholds(minBrightness = 50.0),
            roi = NormalizedRoi(0.25f, 0.30f, 0.50f, 0.40f),
            requiredHoldFrames = 2
        )
        mockPlan = CapturePlan(
            name = "plan_test",
            createdAtIso = "2026-09-11T12:00:00Z",
            scaleFactorRaw = "1.0",
            steps = listOf(step)
        )
    }

    @Test
    fun `en estado Idle el obturador siempre permanece cerrado`() {
        val validMetrics = Metrics(meanLuma = 100f, stdDev = 10f, rms = 100f)
        val result = gateStateMachine.evaluate(validMetrics, mockPlan)

        assertFalse(result.isGateOpen)
        assertTrue(result.status is GateStatus.Idle)
    }

    @Test
    fun `luz baja en estado Armed mantiene el obturador cerrado`() {
        gateStateMachine.arm()

        // Brillo de 30f (menor al mínimo de 50.0)
        val lowLumaMetrics = Metrics(meanLuma = 30f, stdDev = 5f, rms = 30f)
        val result = gateStateMachine.evaluate(lowLumaMetrics, mockPlan)

        assertFalse(result.isGateOpen)
        assertTrue(result.status is GateStatus.Armed)
    }

    @Test
    fun `secuencia de luz constante logra la apertura del obturador y completado`() {
        gateStateMachine.arm()
        val validMetrics = Metrics(meanLuma = 80f, stdDev = 15f, rms = 80f)

        // Frame 1: Luz válida -> Entra a WaitingForStability (1 de 2)
        val result1 = gateStateMachine.evaluate(validMetrics, mockPlan)
        assertFalse(result1.isGateOpen)
        assertTrue(result1.status is GateStatus.WaitingForStability)
        val status1 = result1.status as GateStatus.WaitingForStability
        assertEquals(1, status1.currentStableFrames)

        // Frame 2: Luz válida -> Completa los 2 frames -> GateOpen
        val result2 = gateStateMachine.evaluate(validMetrics, mockPlan)
        assertTrue(result2.isGateOpen)
        assertTrue(result2.status is GateStatus.GateOpen)

        // Frame 3: Siguiente tick -> Transiciona a Capturing
        val result3 = gateStateMachine.evaluate(validMetrics, mockPlan)
        assertTrue(result3.isGateOpen)
        assertTrue(result3.status is GateStatus.Capturing)

        // Frame 4: Siguiente tick -> Transiciona a Completed
        val result4 = gateStateMachine.evaluate(validMetrics, mockPlan)
        assertFalse(result4.isGateOpen)
        assertTrue(result4.status is GateStatus.Completed)
    }

    @Test
    fun `caida de luz durante la estabilizacion reinicia el contador`() {
        gateStateMachine.arm()
        val validMetrics = Metrics(meanLuma = 80f, stdDev = 15f, rms = 80f)
        val lowMetrics = Metrics(meanLuma = 20f, stdDev = 5f, rms = 20f)

        // Frame 1: Luz buena -> (1/2)
        gateStateMachine.evaluate(validMetrics, mockPlan)

        // Frame 2: La luz cae a 20f -> Reinicia contador y regresa a Armed
        val resultDrop = gateStateMachine.evaluate(lowMetrics, mockPlan)
        assertFalse(resultDrop.isGateOpen)
        assertTrue(resultDrop.status is GateStatus.Armed)
    }
}
