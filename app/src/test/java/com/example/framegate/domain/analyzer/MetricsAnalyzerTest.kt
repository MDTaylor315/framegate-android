package com.example.framegate.domain.analyzer

import com.example.framegate.domain.fixtures.FixtureFrameSource
import com.example.framegate.domain.model.BufferRect
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.Test

class MetricsAnalyzerTest {

    @Test
    fun `analizar frame uniforme devuelve desviacion estandar cero`() {
        val width = 100
        val height = 100
        val lumaValue: Byte = 120.toByte() // Brillo constante de 120
        val frame = FixtureFrameSource.createUniformFrame(width, height, lumaValue)
        val roi = BufferRect(0, 0, width, height)

        val metrics = MetricsAnalyzer.analyze(
            yBuffer = frame,
            rowStride = width,
            bufferRect = roi
        )

        // En un cuadro uniforme:
        // meanLuma debe ser exactamente 120.0
        assertEquals(120.0f, metrics.meanLuma, 0.01f)
        // stdDev debe ser 0.0 (cero variación/contraste)
        assertEquals(0.0f, metrics.stdDev, 0.01f)
        // rms debe ser 120.0
        assertEquals(120.0f, metrics.rms, 0.01f)
    }

    @Test
    fun `analizar frame de alto contraste devuelve desviacion estandar alta`() {
        val width = 100
        val height = 100
        val frame = FixtureFrameSource.createHighContrastFrame(width, height)
        val roi = BufferRect(0, 0, width, height)

        val metrics = MetricsAnalyzer.analyze(
            yBuffer = frame,
            rowStride = width,
            bufferRect = roi
        )

        // Promedio de 0 y 255 debe ser aprox 127.5
        assertEquals(127.5f, metrics.meanLuma, 0.5f)
        // stdDev (contraste) debe ser alto (aprox 127.5)
        assertTrue(metrics.stdDev > 100.0f)
    }

    @Test
    fun `analizar frame con destello produce RMS mayor que el promedio`() {
        val width = 100
        val height = 100
        val frame = FixtureFrameSource.createGlareFrame(width, height)
        val roi = BufferRect(0, 0, width, height)

        val metrics = MetricsAnalyzer.analyze(
            yBuffer = frame,
            rowStride = width,
            bufferRect = roi
        )

        // El rms debe ser estrictamente mayor que el meanLuma por el peso del destello
        assertTrue(metrics.rms > metrics.meanLuma)
    }

    @Test
    fun `analizar buffer vacio o invalido no hace crash y retorna ceros`() {
        val emptyFrame = ByteArray(0)
        val roi = BufferRect(0, 0, 100, 100)

        val metrics = MetricsAnalyzer.analyze(
            yBuffer = emptyFrame,
            rowStride = 100,
            bufferRect = roi
        )

        assertEquals(0.0f, metrics.meanLuma, 0.01f)
        assertEquals(0.0f, metrics.stdDev, 0.01f)
        assertEquals(0.0f, metrics.rms, 0.01f)
    }
}
