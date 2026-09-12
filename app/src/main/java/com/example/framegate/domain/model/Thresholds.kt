package com.example.framegate.domain.model

data class Thresholds(
    val minFocus: Double = DEFAULT_MIN_FOCUS,
    val minBrightness: Double = DEFAULT_MIN_BRIGHTNESS,
    val maxMotion: Double = DEFAULT_MAX_MOTION,
    // Fracción del foco baseline (pico visto) que se exige; ratio, no absoluto.
    val focusRatio: Double = DEFAULT_FOCUS_RATIO,
) {
    companion object {
        const val DEFAULT_MIN_FOCUS = 10.0
        const val DEFAULT_MIN_BRIGHTNESS = 50.0
        const val DEFAULT_MAX_MOTION = 15.0
        const val DEFAULT_FOCUS_RATIO = 0.6
    }
}
