package com.example.framegate.domain.analyzer

import com.example.framegate.domain.fixtures.FixtureFrameSource
import com.example.framegate.domain.model.BufferRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetricsAnalyzerTest {

    private val width = 100
    private val height = 100
    private val roi = BufferRect(0, 0, width, height)

    @Test
    fun `uniform frame has luma equal to value and zero focus`() {
        val frame = FixtureFrameSource.createUniformFrame(width, height, 120.toByte())

        val metrics = MetricsAnalyzer.analyze(frame, width, roi)

        assertEquals(120f, metrics.meanLuma, 0.5f)
        // With no variation across neighbors, gradient energy is zero.
        assertEquals(0f, metrics.focus, 0.01f)
    }

    @Test
    fun `frame with edges has higher focus than uniform frame`() {
        val uniform = FixtureFrameSource.createUniformFrame(width, height, 100.toByte())
        val withEdges = FixtureFrameSource.createGlareFrame(width, height) // bright patch over dark background

        val focoUniforme = MetricsAnalyzer.analyze(uniform, width, roi).focus
        val focoConBordes = MetricsAnalyzer.analyze(withEdges, width, roi).focus

        // Patch edges generate gradient energy; uniform frame does not.
        assertEquals(0f, focoUniforme, 0.01f)
        assertTrue(focoConBordes > focoUniforme)
    }

    @Test
    fun `frame with glare reports clipped pixel fraction`() {
        val frame = FixtureFrameSource.createGlareFrame(width, height)

        val metrics = MetricsAnalyzer.analyze(frame, width, roi)

        assertTrue(metrics.clippedFraction > 0f)
    }

    @Test
    fun `without previous frame motion is zero`() {
        val frame = FixtureFrameSource.createUniformFrame(width, height, 100.toByte())

        val metrics = MetricsAnalyzer.analyze(frame, width, roi, previousBuffer = null)

        assertEquals(0f, metrics.motion, 0.01f)
    }

    @Test
    fun `two identical frames report zero motion`() {
        val frame = FixtureFrameSource.createUniformFrame(width, height, 100.toByte())
        val previous = FixtureFrameSource.createUniformFrame(width, height, 100.toByte())

        val metrics = MetricsAnalyzer.analyze(frame, width, roi, previousBuffer = previous)

        assertEquals(0f, metrics.motion, 0.01f)
    }

    @Test
    fun `different frames report motion proportional to difference`() {
        val current = FixtureFrameSource.createUniformFrame(width, height, 150.toByte())
        val previous = FixtureFrameSource.createUniformFrame(width, height, 100.toByte())

        val metrics = MetricsAnalyzer.analyze(current, width, roi, previousBuffer = previous)

        // Every pixel changed by 50 -> average difference 50.
        assertEquals(50f, metrics.motion, 0.5f)
    }

    @Test
    fun `with pixelStride 2 reads only luma bytes ignoring interleaved bytes`() {
        // Interleaved row: luma=100 at even indices, garbage=250 at odd indices.
        val w = 4
        val h = 1
        val buffer = ByteArray(w * 2) { i -> if (i % 2 == 0) 100.toByte() else 250.toByte() }
        val roi = BufferRect(0, 0, w, h)

        // rowStride = w*2 (interleaved row), pixelStride = 2.
        val metrics = MetricsAnalyzer.analyze(buffer, rowStride = w * 2, bufferRect = roi, pixelStride = 2)

        // Must read only the 100s (luma), skipping the 250s (interleaved data).
        assertEquals(100f, metrics.meanLuma, 0.5f)
    }

    @Test
    fun `empty buffer does not crash and returns zeros`() {
        val metrics = MetricsAnalyzer.analyze(ByteArray(0), width, roi)

        assertEquals(0f, metrics.meanLuma, 0.01f)
        assertEquals(0f, metrics.focus, 0.01f)
        assertEquals(0f, metrics.motion, 0.01f)
    }
}
