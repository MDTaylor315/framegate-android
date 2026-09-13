package com.example.framegate.domain.gate

import com.example.framegate.domain.model.CapturePlan
import com.example.framegate.domain.model.CaptureStep
import com.example.framegate.domain.model.Metrics
import com.example.framegate.domain.model.NormalizedRoi
import com.example.framegate.domain.model.StepType
import com.example.framegate.domain.model.Thresholds
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Corre el GateReducer tick a tick contra gate_cases.json, cuyos estados
 * esperados se derivaron a mano de la fórmula del gate (no de la salida del código).
 */
class GateCasesTest {

    @Serializable
    private data class Cases(
        val thresholds: Thr,
        val holdFrames: Int,
        val cases: List<Case>,
    )

    @Serializable private data class Thr(val focusRatio: Double, val minBrightness: Double, val maxMotion: Double)

    @Serializable
    private data class Case(val name: String, val ticks: List<Tick>)

    @Serializable
    private data class Tick(
        val focus: Float,
        val meanLuma: Float,
        val motion: Float,
        val phase: String,
        val held: Int? = null,
        val failing: List<String>? = null,
    )

    private fun load(): Cases {
        val text = requireNotNull(javaClass.getResourceAsStream("/fixtures/gate_cases.json")) {
            "no está gate_cases.json"
        }.bufferedReader().use { it.readText() }
        return json.decodeFromString(Cases.serializer(), text)
    }

    @Test
    fun `cada caso reproduce el estado esperado tick a tick`() {
        val data = load()
        val plan = planOf(data)

        data.cases.forEach { case ->
            var state = GateState()
            case.ticks.forEachIndexed { index, tick ->
                state = GateReducer.reduce(state, tick.toMetrics(), plan)
                assertEquals(
                    "${case.name} tick $index",
                    expectedPhase(tick, data.holdFrames),
                    state.phase,
                )
            }
        }
    }

    private fun planOf(data: Cases): CapturePlan {
        val step = CaptureStep(
            id = "s1",
            type = StepType.SINGLE_FRAME,
            thresholds = Thresholds(
                focusRatio = data.thresholds.focusRatio,
                minBrightness = data.thresholds.minBrightness,
                maxMotion = data.thresholds.maxMotion,
            ),
            roi = NormalizedRoi(),
            requiredHoldFrames = data.holdFrames,
        )
        return CapturePlan("gate-cases", "2026-01-01T00:00:00Z", "1.0", listOf(step))
    }

    private fun Tick.toMetrics() = Metrics(focus = focus, meanLuma = meanLuma, clippedFraction = 0f, motion = motion)

    private fun expectedPhase(tick: Tick, holdFrames: Int): GatePhase = when (tick.phase) {
        "Holding" -> GatePhase.Holding(requireNotNull(tick.held), holdFrames)
        "Armed" -> GatePhase.Armed
        "Blocked" -> GatePhase.Blocked(tick.failing.orEmpty().map(Measurement::valueOf).toSet())
        else -> error("fase desconocida ${tick.phase}")
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
