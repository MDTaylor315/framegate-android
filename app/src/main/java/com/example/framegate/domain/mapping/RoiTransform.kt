package com.example.framegate.domain.mapping

import com.example.framegate.domain.model.NormalizedRoi

/**
 * Pure geometry helper: applies horizontal mirroring and rotation to a normalized ROI (0..1),
 * orienting it relative to user visual display coordinates.
 */
object RoiTransform {

    private const val FULL_TURN = 360
    private const val QUARTER = 90
    private const val HALF = 180
    private const val THREE_QUARTER = 270

    // Rotations in degrees (0/90/180/270). Net rotation is sensor minus display orientation.
    fun transform(
        roi: NormalizedRoi,
        sensorRotation: Int,
        displayRotation: Int = 0,
        isMirrored: Boolean = false,
    ): NormalizedRoi {
        val mirrored = if (isMirrored) mirrorHorizontal(roi) else roi
        return rotate(mirrored, effectiveRotation(sensorRotation, displayRotation))
    }

    /** Net rotation (0/90/180/270) experienced by user given sensor and display angles. */
    fun effectiveRotation(sensorRotation: Int, displayRotation: Int): Int =
        ((sensorRotation - displayRotation) % FULL_TURN + FULL_TURN) % FULL_TURN

    /** Returns true if net rotation swaps buffer width and height (90° / 270°). */
    fun swapsDimensions(rotation: Int): Boolean = rotation == QUARTER || rotation == THREE_QUARTER

    // Horizontal mirror: left side becomes right side. x' = 1 - x - width.
    // e.g., x=0.1, w=0.2 -> x'=0.7 (aligned to opposite side).
    private fun mirrorHorizontal(r: NormalizedRoi): NormalizedRoi =
        r.copy(x = 1f - r.x - r.width)

    // Rotates the rectangle in 90° steps. At 90°/270°, width and height swap.
    private fun rotate(r: NormalizedRoi, degrees: Int): NormalizedRoi = when (degrees) {
        QUARTER -> NormalizedRoi(x = 1f - r.y - r.height, y = r.x, width = r.height, height = r.width)
        HALF -> NormalizedRoi(x = 1f - r.x - r.width, y = 1f - r.y - r.height, width = r.width, height = r.height)
        THREE_QUARTER -> NormalizedRoi(x = r.y, y = 1f - r.x - r.width, width = r.height, height = r.width)
        else -> r // 0°: unchanged
    }
}
