package com.example.framegate.domain.fixtures

import com.example.framegate.domain.interfaces.FrameSource
import com.example.framegate.domain.model.FrameData

/**
 * Fuente de frames que reproduce una lista fija de [FrameData] en orden. Es la
 * ruta de revisión principal (sin cámara real). Al agotar los frames, repite
 * desde el inicio para que el loop de la demo no se quede sin datos.
 */
class ReplayFrameSource(private val frames: List<FrameData>) : FrameSource {

    private var index = 0

    override fun getNextFrame(): FrameData? {
        if (frames.isEmpty()) return null
        val frame = frames[index % frames.size]
        index++
        return frame
    }

    companion object {
        private const val DEFAULT_SIZE = 100
        private const val VALID_LUMA: Byte = 100

        /** Fuente simple con un frame uniforme válido, para la demo. */
        fun uniform(): ReplayFrameSource {
            val bytes = FixtureFrameSource.createUniformFrame(DEFAULT_SIZE, DEFAULT_SIZE, VALID_LUMA)
            val frame = FrameData(
                width = DEFAULT_SIZE,
                height = DEFAULT_SIZE,
                rowStride = DEFAULT_SIZE,
                pixelStride = 1,
                sensorRotation = 0,
                isMirrored = false,
                yBuffer = bytes,
                timestampEpochMs = 0L,
            )
            return ReplayFrameSource(listOf(frame))
        }
    }
}
