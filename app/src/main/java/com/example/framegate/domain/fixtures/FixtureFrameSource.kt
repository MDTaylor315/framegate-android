package com.example.framegate.domain.fixtures

object FixtureFrameSource {

    //Retorna un buffer de luminancia Y parejo, o sea que los pixeles comparten el mismo brillo
    fun createUniformFrame(width: Int, height: Int, lumaValue: Byte): ByteArray{
        val buffer  = ByteArray(width*height)
        buffer.fill(lumaValue)
        return buffer
    }

    //Data de prueba: un pixel oscuro y otro claro
    fun createHighContrastFrame(width: Int, height: Int): ByteArray{
        val buffer = ByteArray(width*height)
        for (y in 0 until height){
            for(x in 0 until width){
                val value = if (x % 2 == 0) 0.toByte() else 255.toByte()
                buffer[y*width+x] = value
            }
        }
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
}
