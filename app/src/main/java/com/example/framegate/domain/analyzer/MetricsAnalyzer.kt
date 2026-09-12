package com.example.framegate.domain.analyzer

import com.example.framegate.domain.model.BufferRect
import com.example.framegate.domain.model.Metrics
import com.example.framegate.domain.model.height
import com.example.framegate.domain.model.width
import java.nio.Buffer
import kotlin.math.sqrt

object MetricsAnalyzer {
    fun analyze(
        //Bytes con info de brillo de la cámara (escala de grises)
        yBuffer: ByteArray,
        rowStride: Int,
        bufferRect: BufferRect
    ): Metrics{
        val width = bufferRect.width
        val height = bufferRect.height
        val totalPixels = width*height

        if(totalPixels <= 0 || yBuffer.isEmpty() || rowStride <= 0){
            return Metrics(meanLuma = 0f, stdDev = 0f, rms = 0f)
        }

        var sumLuma = 0.0
        var sumSquareLuma = 0.0

        for(y in bufferRect.top until bufferRect.bottom){
            val rowOffset = y*rowStride
            for(x in bufferRect.left until bufferRect.right){
                val pixelIndex = rowOffset + x
                if(pixelIndex in yBuffer.indices){
                    val luma = yBuffer[pixelIndex].toInt() and 0xFF
                    sumLuma += luma
                    sumSquareLuma += (luma*luma)
                }
            }
        }

        // 1. Promedio de Luminancia (Brillo)
        val meanLuma = (sumLuma / totalPixels).toFloat()
        // 2. Root Mean Square (RMS)
        //Verifica que haya algún objeto en cámara
        val rms = sqrt(sumSquareLuma / totalPixels).toFloat()
        // 3. Desviación Estándar (Contraste)
        // Una especie de escudo por si hay un brillo muy fuerte
        // que podría compensar la falta de luz en el resto de la imagen
        val variance = (sumSquareLuma / totalPixels) - (meanLuma * meanLuma)
        val stdDev = if (variance > 0) sqrt(variance).toFloat() else 0f
        return Metrics(
            meanLuma = meanLuma,
            stdDev = stdDev,
            rms = rms
        )
    }
}
