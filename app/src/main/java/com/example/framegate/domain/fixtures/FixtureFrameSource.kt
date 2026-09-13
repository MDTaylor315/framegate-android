package com.example.framegate.domain.fixtures

object FixtureFrameSource {

    // Returns a uniform Y luminance buffer where all pixels share identical brightness
    fun createUniformFrame(width: Int, height: Int, lumaValue: Byte): ByteArray{
        val buffer  = ByteArray(width*height)
        buffer.fill(lumaValue)
        return buffer
    }

    // Test data: dark area with a bright glare spot in the center
    fun createGlareFrame(width: Int, height: Int): ByteArray {
        val buffer = ByteArray(width * height) { 10.toByte() }
        val centerX = width / 2
        val centerY = height / 2

        // 10x10 bright patch at center
        for (y in (centerY - 5)..(centerY + 5)) {
            for (x in (centerX - 5)..(centerX + 5)) {
                if (x in 0 until width && y in 0 until height) {
                    buffer[y * width + x] = 255.toByte()
                }
            }
        }
        return buffer
    }

    // Left half filled with [left], right half filled with [right]: uneven brightness between frames.
    fun createHalfFrame(width: Int, height: Int, left: Byte, right: Byte): ByteArray {
        val buffer = ByteArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                buffer[y * width + x] = if (x < width / 2) left else right
            }
        }
        return buffer
    }

    // Vertical stripes alternating [lo]/[hi] every [period] columns: high gradient energy.
    fun createStripeFrame(width: Int, height: Int, lo: Byte, hi: Byte, period: Int = 2): ByteArray {
        val buffer = ByteArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                buffer[y * width + x] = if ((x / period) % 2 == 0) hi else lo
            }
        }
        return buffer
    }

    // Flat background [background] with sharp stripes only in the top-left quadrant.
    fun createQuadrantSharpFrame(width: Int, height: Int, background: Byte, lo: Byte, hi: Byte): ByteArray {
        val buffer = ByteArray(width * height) { background }
        for (y in 0 until height / 2) {
            for (x in 0 until width / 2) {
                buffer[y * width + x] = if ((x / 2) % 2 == 0) hi else lo
            }
        }
        return buffer
    }
}
