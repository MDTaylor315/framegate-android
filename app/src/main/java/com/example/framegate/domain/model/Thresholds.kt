package com.example.framegate.domain.model

data class Thresholds
    (val minFocus: Double = DEFAULT_MIN_FOCUS,
     val minBrightness: Double = DEFAULT_MIN_BRIGHTNESS,
     val maxMotion: Double = DEFAULT_MAX_MOTION
     )
{
    companion object {
        const val DEFAULT_MIN_FOCUS = 10.0
        const val DEFAULT_MIN_BRIGHTNESS = 50.0
        const val DEFAULT_MAX_MOTION = 15.0
    }
}
