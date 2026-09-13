package com.example.framegate.domain.model

private const val DEG_0 = 0
private const val DEG_90 = 90
private const val DEG_180 = 180
private const val DEG_270 = 270

/**
 * Las cuatro configuraciones de cámara que el revisor cicla para comprobar que el
 * overlay del ROI cae bien en cada rotación/espejo. Se aplican al frame de la
 * fixture (que de por sí no tiene orientación) para ejercitar el mapeo.
 */
enum class CameraConfig(val sensorRotation: Int, val isMirrored: Boolean, val label: String) {
    ROT_0(DEG_0, false, "0°"),
    ROT_90(DEG_90, false, "90°"),
    ROT_180(DEG_180, false, "180°"),
    ROT_270_MIRROR(DEG_270, true, "270° + espejo");

    fun next(): CameraConfig = entries[(ordinal + 1) % entries.size]
}
