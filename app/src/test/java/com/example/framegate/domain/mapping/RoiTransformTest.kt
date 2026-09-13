package com.example.framegate.domain.mapping

import com.example.framegate.domain.model.NormalizedRoi
import org.junit.Assert.assertEquals
import org.junit.Test

class RoiTransformTest {

    private val tol = 0.0001f

    // Narrow and tall ROI in the top-left corner.
    private val topLeft = NormalizedRoi(x = 0f, y = 0f, width = 0.2f, height = 0.4f)

    private fun assertRoi(expected: NormalizedRoi, actual: NormalizedRoi) {
        assertEquals(expected.x, actual.x, tol)
        assertEquals(expected.y, actual.y, tol)
        assertEquals(expected.width, actual.width, tol)
        assertEquals(expected.height, actual.height, tol)
    }

    @Test
    fun `without rotation or mirroring ROI remains unchanged`() {
        assertRoi(topLeft, RoiTransform.transform(topLeft, sensorRotation = 0))
    }

    @Test
    fun `horizontal mirroring moves ROI to opposite side`() {
        // x' = 1 - 0 - 0.2 = 0.8
        val result = RoiTransform.transform(topLeft, sensorRotation = 0, isMirrored = true)
        assertRoi(NormalizedRoi(x = 0.8f, y = 0f, width = 0.2f, height = 0.4f), result)
    }

    @Test
    fun `90 degrees rotation swaps axes and dimensions`() {
        // 90: x'=1-y-h=0.6, y'=x=0, w'=h=0.4, h'=w=0.2
        val result = RoiTransform.transform(topLeft, sensorRotation = 90)
        assertRoi(NormalizedRoi(x = 0.6f, y = 0f, width = 0.4f, height = 0.2f), result)
    }

    @Test
    fun `180 degrees rotation moves ROI to opposite corner`() {
        // 180: x'=1-x-w=0.8, y'=1-y-h=0.6, identical dimensions
        val result = RoiTransform.transform(topLeft, sensorRotation = 180)
        assertRoi(NormalizedRoi(x = 0.8f, y = 0.6f, width = 0.2f, height = 0.4f), result)
    }

    @Test
    fun `270 degrees rotation swaps axes to opposite side`() {
        // 270: x'=y=0, y'=1-x-w=0.8, w'=h=0.4, h'=w=0.2
        val result = RoiTransform.transform(topLeft, sensorRotation = 270)
        assertRoi(NormalizedRoi(x = 0f, y = 0.8f, width = 0.4f, height = 0.2f), result)
    }

    @Test
    fun `equal sensor and display rotations cancel out (net zero rotation)`() {
        val result = RoiTransform.transform(topLeft, sensorRotation = 90, displayRotation = 90)
        assertRoi(topLeft, result)
    }
}
