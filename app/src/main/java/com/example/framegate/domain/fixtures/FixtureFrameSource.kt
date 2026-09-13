package com.example.framegate.domain.fixtures

object FixtureFrameSource {

    //Retorna un buffer de luminancia Y parejo, o sea que los pixeles comparten el mismo brillo
    fun createUniformFrame(width: Int, height: Int, lumaValue: Byte): ByteArray{
        val buffer  = ByteArray(width*height)
        buffer.fill(lumaValue)
        return buffer
    }

    //Data de prueba: Area oscura con un brillo en el centro
    fun createGlareFrame(width: Int, height: Int): ByteArray {
        val buffer = ByteArray(width * height) { 10.toByte() }
        val centerX = width / 2
        val centerY = height / 2

        // Parche brillante de 10x10 en el centro
        for (y in (centerY - 5)..(centerY + 5)) {
            for (x in (centerX - 5)..(centerX + 5)) {
                if (x in 0 until width && y in 0 until height) {
                    buffer[y * width + x] = 255.toByte()
                }
            }
        }
        return buffer
    }

    // Mitad izquierda con [left], mitad derecha con [right]: brillo dispar entre frames.
    fun createHalfFrame(width: Int, height: Int, left: Byte, right: Byte): ByteArray {
        val buffer = ByteArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                buffer[y * width + x] = if (x < width / 2) left else right
            }
        }
        return buffer
    }

    // Franjas verticales que alternan [lo]/[hi] cada [period] columnas: mucho gradiente.
    fun createStripeFrame(width: Int, height: Int, lo: Byte, hi: Byte, period: Int = 2): ByteArray {
        val buffer = ByteArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                buffer[y * width + x] = if ((x / period) % 2 == 0) hi else lo
            }
        }
        return buffer
    }

    // Fondo plano [background] con franjas nítidas solo en el cuadrante superior-izquierdo.
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
