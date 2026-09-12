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
    fun `frame uniforme tiene brillo igual al valor y foco cero`() {
        val frame = FixtureFrameSource.createUniformFrame(width, height, 120.toByte())

        val metrics = MetricsAnalyzer.analyze(frame, width, roi)

        assertEquals(120f, metrics.meanLuma, 0.5f)
        // Sin variación entre vecinos, no hay energía de gradiente.
        assertEquals(0f, metrics.focus, 0.01f)
    }

    @Test
    fun `un frame con bordes tiene mas foco que uno uniforme`() {
        val uniform = FixtureFrameSource.createUniformFrame(width, height, 100.toByte())
        val withEdges = FixtureFrameSource.createGlareFrame(width, height) // parche claro sobre fondo oscuro

        val focoUniforme = MetricsAnalyzer.analyze(uniform, width, roi).focus
        val focoConBordes = MetricsAnalyzer.analyze(withEdges, width, roi).focus

        // Los bordes del parche generan energía de gradiente; el uniforme no.
        assertEquals(0f, focoUniforme, 0.01f)
        assertTrue(focoConBordes > focoUniforme)
    }

    @Test
    fun `frame con destello reporta pixeles clippeados`() {
        val frame = FixtureFrameSource.createGlareFrame(width, height)

        val metrics = MetricsAnalyzer.analyze(frame, width, roi)

        assertTrue(metrics.clippedFraction > 0f)
    }

    @Test
    fun `sin frame anterior el movimiento es cero`() {
        val frame = FixtureFrameSource.createUniformFrame(width, height, 100.toByte())

        val metrics = MetricsAnalyzer.analyze(frame, width, roi, previousBuffer = null)

        assertEquals(0f, metrics.motion, 0.01f)
    }

    @Test
    fun `dos frames identicos no reportan movimiento`() {
        val frame = FixtureFrameSource.createUniformFrame(width, height, 100.toByte())
        val previous = FixtureFrameSource.createUniformFrame(width, height, 100.toByte())

        val metrics = MetricsAnalyzer.analyze(frame, width, roi, previousBuffer = previous)

        assertEquals(0f, metrics.motion, 0.01f)
    }

    @Test
    fun `frames distintos reportan movimiento proporcional a la diferencia`() {
        val current = FixtureFrameSource.createUniformFrame(width, height, 150.toByte())
        val previous = FixtureFrameSource.createUniformFrame(width, height, 100.toByte())

        val metrics = MetricsAnalyzer.analyze(current, width, roi, previousBuffer = previous)

        // Cada píxel cambió en 50 -> diferencia media 50.
        assertEquals(50f, metrics.motion, 0.5f)
    }

    @Test
    fun `con pixelStride 2 lee solo los bytes de luma, ignorando los intercalados`() {
        // Fila entrelazada: luma=100 en posiciones pares, basura=250 en impares.
        val w = 4
        val h = 1
        val buffer = ByteArray(w * 2) { i -> if (i % 2 == 0) 100.toByte() else 250.toByte() }
        val roi = BufferRect(0, 0, w, h)

        // rowStride = w*2 (la fila entrelazada), pixelStride = 2.
        val metrics = MetricsAnalyzer.analyze(buffer, rowStride = w * 2, bufferRect = roi, pixelStride = 2)

        // Debe leer solo los 100 (luma), no los 250 (intercalados).
        assertEquals(100f, metrics.meanLuma, 0.5f)
    }

    @Test
    fun `buffer vacio no crashea y retorna ceros`() {
        val metrics = MetricsAnalyzer.analyze(ByteArray(0), width, roi)

        assertEquals(0f, metrics.meanLuma, 0.01f)
        assertEquals(0f, metrics.focus, 0.01f)
        assertEquals(0f, metrics.motion, 0.01f)
    }
}
