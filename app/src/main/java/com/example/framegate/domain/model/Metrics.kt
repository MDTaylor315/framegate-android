package com.example.framegate.domain.model

/**
 * The metrics measured from a frame within the ROI.
 * - focus: gradient energy (higher = sharper).
 * - meanLuma: average brightness (0..255).
 * - clippedFraction: fraction of overexposed or underexposed pixels (0..1).
 * - motion: average difference relative to the previous frame (0 = stationary).
 */
data class Metrics(
    val focus: Float,
    val meanLuma: Float,
    val clippedFraction: Float,
    val motion: Float,
)
