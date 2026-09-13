package com.example.framegate.domain.model

data class Thresholds(
    // Fraction of baseline focus (peak seen) required; ratio, not absolute.
    val focusRatio: Double = DEFAULT_FOCUS_RATIO,
    val minBrightness: Double = DEFAULT_MIN_BRIGHTNESS,
    val maxMotion: Double = DEFAULT_MAX_MOTION,
    // Maximum fraction of clipped/blown-out pixels tolerated for valid brightness.
    val maxClippedFraction: Double = DEFAULT_MAX_CLIPPED_FRACTION,
) {
    companion object {
        const val DEFAULT_FOCUS_RATIO = 0.6
        const val DEFAULT_MIN_BRIGHTNESS = 50.0
        const val DEFAULT_MAX_MOTION = 15.0
        const val DEFAULT_MAX_CLIPPED_FRACTION = 0.5
    }
}
