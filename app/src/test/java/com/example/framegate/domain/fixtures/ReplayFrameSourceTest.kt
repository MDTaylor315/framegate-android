package com.example.framegate.domain.fixtures

import com.example.framegate.domain.model.FrameData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReplayFrameSourceTest {

    private fun frame(tag: Long) = FrameData(
        width = 2, height = 2, rowStride = 2, pixelStride = 1,
        sensorRotation = 0, isMirrored = false,
        yBuffer = byteArrayOf(0, 0, 0, 0), timestampEpochMs = tag,
    )

    @Test
    fun `emite los frames en orden`() {
        val source = ReplayFrameSource(listOf(frame(1), frame(2)))
        assertEquals(1L, source.getNextFrame()?.timestampEpochMs)
        assertEquals(2L, source.getNextFrame()?.timestampEpochMs)
    }

    @Test
    fun `cicla al llegar al final`() {
        val source = ReplayFrameSource(listOf(frame(1), frame(2)))
        source.getNextFrame() // 1
        source.getNextFrame() // 2
        assertEquals(1L, source.getNextFrame()?.timestampEpochMs) // vuelve al inicio
    }

    @Test
    fun `sin frames devuelve null`() {
        assertNull(ReplayFrameSource(emptyList()).getNextFrame())
    }

    @Test
    fun `la fabrica uniform produce un frame usable`() {
        val frame = ReplayFrameSource.uniform().getNextFrame()
        assertEquals(100, frame?.width)
        assertEquals(1, frame?.pixelStride)
    }
}
