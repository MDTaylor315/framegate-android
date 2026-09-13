package com.example.framegate.domain.model

private const val DEG_0 = 0
private const val DEG_90 = 90
private const val DEG_180 = 180
private const val DEG_270 = 270

/**
 * The four camera configurations cycled by the reviewer to verify that the
 * ROI overlay aligns correctly in every rotation/mirroring mode. Applied to fixture
 * frames (which otherwise lack orientation metadata) to exercise mapping logic.
 */
enum class CameraConfig(val sensorRotation: Int, val isMirrored: Boolean, val label: String) {
    ROT_0(DEG_0, false, "0°"),
    ROT_90(DEG_90, false, "90°"),
    ROT_180(DEG_180, false, "180°"),
    ROT_270_MIRROR(DEG_270, true, "270° + espejo");

    fun next(): CameraConfig = entries[(ordinal + 1) % entries.size]
}
