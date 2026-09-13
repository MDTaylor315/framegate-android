package com.example.framegate.domain.model

data class Thresholds(
    // Fracción del foco baseline (pico visto) que se exige; ratio, no absoluto.
    val focusRatio: Double = DEFAULT_FOCUS_RATIO,
    val minBrightness: Double = DEFAULT_MIN_BRIGHTNESS,
    val maxMotion: Double = DEFAULT_MAX_MOTION,
    // Fracción máxima de píxeles quemados/aplastados que se tolera para el brillo.
    val maxClippedFraction: Double = DEFAULT_MAX_CLIPPED_FRACTION,
) {
    companion object {
        const val DEFAULT_FOCUS_RATIO = 0.6
        const val DEFAULT_MIN_BRIGHTNESS = 50.0
        const val DEFAULT_MAX_MOTION = 15.0
        const val DEFAULT_MAX_CLIPPED_FRACTION = 0.5
    }
}
