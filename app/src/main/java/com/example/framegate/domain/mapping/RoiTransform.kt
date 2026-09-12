package com.example.framegate.domain.mapping

import com.example.framegate.domain.model.NormalizedRoi

/**
 * Geometría pura: aplica espejo y luego rotación a un ROI normalizado (0..1),
 * para dejarlo en las coordenadas que el usuario ve.
 */
object RoiTransform {

    private const val FULL_TURN = 360
    private const val QUARTER = 90
    private const val HALF = 180
    private const val THREE_QUARTER = 270

    // Rotaciones en grados (0/90/180/270). La neta es la diferencia sensor-display.
    fun transform(
        roi: NormalizedRoi,
        sensorRotation: Int,
        displayRotation: Int = 0,
        isMirrored: Boolean = false,
    ): NormalizedRoi {
        val mirrored = if (isMirrored) mirrorHorizontal(roi) else roi
        return rotate(mirrored, effectiveRotation(sensorRotation, displayRotation))
    }

    /** Rotación neta (0/90/180/270) que ve el usuario dado sensor y display. */
    fun effectiveRotation(sensorRotation: Int, displayRotation: Int): Int =
        ((sensorRotation - displayRotation) % FULL_TURN + FULL_TURN) % FULL_TURN

    /** True si la rotación neta deja el buffer apaisado (se intercambian W/H). */
    fun swapsDimensions(rotation: Int): Boolean = rotation == QUARTER || rotation == THREE_QUARTER

    // Espejo horizontal: la izquierda pasa a ser la derecha. x' = 1 - x - width.
    // Ej.: x=0.1,w=0.2 -> x'=0.7 (queda pegado al lado opuesto).
    private fun mirrorHorizontal(r: NormalizedRoi): NormalizedRoi =
        r.copy(x = 1f - r.x - r.width)

    // Rota el rectángulo en pasos de 90°. En 90/270 se intercambian ancho y alto.
    private fun rotate(r: NormalizedRoi, degrees: Int): NormalizedRoi = when (degrees) {
        QUARTER -> NormalizedRoi(x = 1f - r.y - r.height, y = r.x, width = r.height, height = r.width)
        HALF -> NormalizedRoi(x = 1f - r.x - r.width, y = 1f - r.y - r.height, width = r.width, height = r.height)
        THREE_QUARTER -> NormalizedRoi(x = r.y, y = 1f - r.x - r.width, width = r.height, height = r.width)
        else -> r // 0°: sin cambios
    }
}
