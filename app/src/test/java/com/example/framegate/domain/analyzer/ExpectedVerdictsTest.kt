package com.example.framegate.domain.analyzer

import com.example.framegate.domain.fixtures.ReplayFrameSource
import com.example.framegate.domain.model.BufferRect
import com.example.framegate.domain.model.FrameData
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Reproduce los 24 frames del assessment, corre MetricsAnalyzer con el ROI de cada
 * fila y compara métricas y verdicts contra expected_verdicts.csv. El CSV se derivó
 * a mano de la fórmula (metrics_reference.md), no de la salida del código.
 */
class ExpectedVerdictsTest {

    private data class Row(
        val frame: Int,
        val roi: FloatArray,
        val focus: Float,
        val meanLuma: Float,
        val clippedFraction: Float,
        val motion: Float,
        val focusOk: Boolean,
        val brightnessOk: Boolean,
        val motionOk: Boolean,
        val allPass: Boolean,
    )

    private fun loadRows(): List<Row> {
        val text = requireNotNull(javaClass.getResourceAsStream("/fixtures/expected_verdicts.csv")) {
            "no está expected_verdicts.csv"
        }.bufferedReader().use { it.readText() }
        return text.trim().lines().drop(1).map { line ->
            val c = line.split(",")
            Row(
                frame = c[0].toInt(),
                roi = floatArrayOf(c[1].toFloat(), c[2].toFloat(), c[3].toFloat(), c[4].toFloat()),
                focus = c[5].toFloat(),
                meanLuma = c[6].toFloat(),
                clippedFraction = c[7].toFloat(),
                motion = c[8].toFloat(),
                focusOk = c[9].toBoolean(),
                brightnessOk = c[10].toBoolean(),
                motionOk = c[11].toBoolean(),
                allPass = c[12].toBoolean(),
            )
        }
    }

    @Test
    fun `los 24 frames producen los verdicts del csv`() {
        val rows = loadRows()
        val frames = drainFrames()
        assertEquals("cantidad de frames", rows.size, frames.size)

        var baseline = 0f
        var previous: ByteArray? = null
        rows.forEachIndexed { index, row ->
            val frame = frames[index]
            val roi = toBufferRect(row.roi, frame.width, frame.height)
            val metrics = MetricsAnalyzer.analyze(frame.yBuffer, frame.rowStride, roi, frame.pixelStride, previous)

            assertEquals("frame ${row.frame} focus", row.focus, metrics.focus, TOLERANCE)
            assertEquals("frame ${row.frame} mean_luma", row.meanLuma, metrics.meanLuma, TOLERANCE)
            assertEquals("frame ${row.frame} clipped", row.clippedFraction, metrics.clippedFraction, TOLERANCE)
            assertEquals("frame ${row.frame} motion", row.motion, metrics.motion, TOLERANCE)

            baseline = maxOf(baseline, metrics.focus)
            val focusOk = metrics.focus >= baseline * FOCUS_RATIO
            val brightnessOk = metrics.meanLuma >= MIN_BRIGHTNESS
            val motionOk = metrics.motion <= MAX_MOTION
            assertEquals("frame ${row.frame} focus_ok", row.focusOk, focusOk)
            assertEquals("frame ${row.frame} brightness_ok", row.brightnessOk, brightnessOk)
            assertEquals("frame ${row.frame} motion_ok", row.motionOk, motionOk)
            assertEquals("frame ${row.frame} all_pass", row.allPass, focusOk && brightnessOk && motionOk)

            previous = frame.yBuffer
        }
    }

    private fun drainFrames(): List<FrameData> = buildList {
        val source = ReplayFrameSource.assessment()
        repeat(EXPECTED_COUNT) { add(requireNotNull(source.getNextFrame())) }
    }

    private fun toBufferRect(roi: FloatArray, width: Int, height: Int): BufferRect {
        val left = (roi[0] * width).toInt()
        val top = (roi[1] * height).toInt()
        val right = ((roi[0] + roi[2]) * width).toInt()
        val bottom = ((roi[1] + roi[3]) * height).toInt()
        return BufferRect(left = left, top = top, right = right, bottom = bottom)
    }

    private companion object {
        const val TOLERANCE = 0.01f
        const val FOCUS_RATIO = 0.6f
        const val MIN_BRIGHTNESS = 50f
        const val MAX_MOTION = 15f
        const val EXPECTED_COUNT = 24
    }
}
