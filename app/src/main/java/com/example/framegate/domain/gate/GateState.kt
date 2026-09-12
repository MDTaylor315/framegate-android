package com.example.framegate.domain.gate

/** Qué medición evalúa el gate. Sirve para decir cuál está bloqueando. */
enum class Measurement { FOCUS, BRIGHTNESS, MOTION }

/**
 * Fase del gate para un paso:
 * - Blocked: alguna medición falla; `failing` dice cuáles (para el HUD).
 * - Holding: todas pasan; acumulando frames buenos (held de N).
 * - Armed: se completó la ventana; listo para disparar.
 * - Fired: se disparó la captura de este paso.
 * - Complete: no quedan más pasos.
 */
sealed interface GatePhase {
    data class Blocked(val failing: Set<Measurement>) : GatePhase
    data class Holding(val held: Int, val required: Int) : GatePhase
    data object Armed : GatePhase
    data object Fired : GatePhase
    data object Complete : GatePhase
}

/**
 * Estado inmutable del gate. El reducer recibe uno y devuelve otro; no hay
 * estado mutable escondido.
 */
data class GateState(
    val phase: GatePhase = GatePhase.Blocked(emptySet()),
    val stableFrames: Int = 0,
    val stepIndex: Int = 0,
)

/** Verdict por medición: qué pasó y qué falló en un frame. */
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
