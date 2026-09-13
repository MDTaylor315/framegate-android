package com.example.framegate.domain.fixtures

import com.example.framegate.domain.interfaces.FrameSource
import com.example.framegate.domain.model.FrameData

/**
 * Frame source that replays a fixed list of [FrameData] in sequential order. Used as the
 * primary review pipeline (no live camera required). Upon exhausting frames, loops back
 * to the start so the demo pipeline remains active.
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
        private const val ASSESSMENT_SIZE = 64
        private const val BLOCK = 6
        private const val DARK_LUMA: Byte = 20
        private const val HALF_LEFT: Byte = 10
        private const val HALF_RIGHT: Byte = 35
        private const val MID_LUMA: Byte = 120
        private const val STRIPE_LO: Byte = 40
        private const val STRIPE_HI = 210.toByte()

        /** Simple source with a single uniform valid frame, for demo purposes. */
        fun uniform(): ReplayFrameSource {
            val bytes = FixtureFrameSource.createUniformFrame(DEFAULT_SIZE, DEFAULT_SIZE, VALID_LUMA)
            return ReplayFrameSource(listOf(frameOf(bytes, DEFAULT_SIZE)))
        }

        /**
         * 24-frame sequence for assessment evaluation (shaky/dark, transition, sharp/centred,
         * sharp-in-one-quadrant). Serves as the main review path; expected verdicts are in
         * expected_verdicts.csv. Frames and metrics documented in metrics_reference.md.
         */
        fun assessment(): ReplayFrameSource {
            val s = ASSESSMENT_SIZE
            val frames = buildList {
                repeat(BLOCK) { i -> add(frameOf(darkFrame(s, i), s)) }
                repeat(BLOCK) { add(frameOf(FixtureFrameSource.createUniformFrame(s, s, MID_LUMA), s)) }
                repeat(BLOCK) { add(frameOf(FixtureFrameSource.createStripeFrame(s, s, STRIPE_LO, STRIPE_HI), s)) }
                repeat(BLOCK) { add(frameOf(quadrantFrame(s), s)) }
            }
            return ReplayFrameSource(frames)
        }

        // Dark/shaky block: uniform and split frames alternate to generate motion.
        private fun darkFrame(size: Int, index: Int): ByteArray =
            if (index % 2 == 0) {
                FixtureFrameSource.createUniformFrame(size, size, DARK_LUMA)
            } else {
                FixtureFrameSource.createHalfFrame(size, size, HALF_LEFT, HALF_RIGHT)
            }

        private fun quadrantFrame(size: Int): ByteArray =
            FixtureFrameSource.createQuadrantSharpFrame(size, size, MID_LUMA, STRIPE_LO, STRIPE_HI)

        private fun frameOf(bytes: ByteArray, size: Int): FrameData = FrameData(
            width = size,
            height = size,
            rowStride = size,
            pixelStride = 1,
            sensorRotation = 0,
            isMirrored = false,
            yBuffer = bytes,
            timestampEpochMs = 0L,
        )
    }
}
