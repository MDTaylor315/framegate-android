package com.example.framegate.domain.mapping

import com.example.framegate.domain.model.NormalizedRoi
import com.example.framegate.domain.model.height
import com.example.framegate.domain.model.width
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.Test

class CoordinateMapperTest {
    @Test
    fun `map unrotated normal ROI correctly computes buffer and view rects`(){
        val roi = NormalizedRoi(x=0.25f, y=0.25f, width = 0.5f, height = 0.5f)
        val mapping =CoordinateMapper.mapCoordinates(
            normalizedRoi = roi,
            bufferWidth = 1920,
            bufferHeight = 1080,
            viewWidth = 1080f,
            viewHeight = 1920f,
            sensorRotation = 0,
            isMirrored = false,
            scaleMode = ScaleMode.CROP
        )

        // Verify Buffer
        // 0.25 * 1920 = 480
        assertEquals(480, mapping.bufferRect.left)
        // (0.25 + 0.5) * 1920 = 1440
        assertEquals(1440, mapping.bufferRect.right)
        // 0.25 * 1080 = 270
        assertEquals(270, mapping.bufferRect.top)
        // (0.25 + 0.50) * 1080 = 810
        assertEquals(810, mapping.bufferRect.bottom)
    }

    @Test
    fun `map 90 degrees rotation swaps effective width and height`() {
        val roi = NormalizedRoi(x = 0.0f, y = 0.0f, width = 1.0f, height = 1.0f)
        val mapping = CoordinateMapper.mapCoordinates(
            bufferWidth = 1920,
            bufferHeight = 1080,
            sensorRotation = 90, // Portrait rotation
            isMirrored = false,
            viewWidth = 1080f,
            viewHeight = 1920f,
            scaleMode = ScaleMode.CROP,
            normalizedRoi = roi
        )
        assertTrue(mapping.viewRect.width > 0f)
        assertTrue(mapping.viewRect.height > 0f)
    }

    @Test
    fun `map in FIT mode generates positive letterbox offsets`() {
        val roi = NormalizedRoi(x = 0.0f, y = 0.0f, width = 1.0f, height = 1.0f)
        val mapping = CoordinateMapper.mapCoordinates(
            bufferWidth = 1920,
            bufferHeight = 1080,
            sensorRotation = 90,
            isMirrored = false,
            viewWidth = 1080f,
            viewHeight = 2400f, // Tall screen ratio
            scaleMode = ScaleMode.FIT,
            normalizedRoi = roi
        )

        // (2400 - 1920) / 2 = 240
        assertEquals(240.0f, mapping.viewRect.top, 0.01f)

    }

    @Test
    fun `map with out of bounds ROI is clamped by coerceIn without crashing`() {
        val invalidRoi = NormalizedRoi(x = -0.5f, y = 0.0f, width = 2.0f, height = 1.0f)
        val mapping = CoordinateMapper.mapCoordinates(
            bufferWidth = 1920,
            bufferHeight = 1080,
            sensorRotation = 0,
            isMirrored = false,
            viewWidth = 1080f,
            viewHeight = 1920f,
            scaleMode = ScaleMode.CROP,
            normalizedRoi = invalidRoi
        )

        assertEquals(0, mapping.bufferRect.left)
        assertEquals(1920, mapping.bufferRect.right)
    }

}
