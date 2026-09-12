package com.example.framegate.domain.model

/**
 * Las tres mediciones de un frame dentro del ROI.
 * - focus: energía de gradiente (mayor = más nítido).
 * - meanLuma: brillo medio (0..255).
 * - clippedFraction: fracción de píxeles quemados o aplastados (0..1).
 * - motion: diferencia media respecto al frame anterior (0 = quieto).
 */
data class Metrics(
    val focus: Float,
    val meanLuma: Float,
    val clippedFraction: Float,
    val motion: Float,
)
