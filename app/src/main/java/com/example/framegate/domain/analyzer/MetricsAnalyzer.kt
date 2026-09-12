package com.example.framegate.domain.analyzer

import com.example.framegate.domain.model.BufferRect
import com.example.framegate.domain.model.Metrics
import com.example.framegate.domain.model.height
import com.example.framegate.domain.model.width
import kotlin.math.abs

/**
 * Mide foco, brillo, clipping y movimiento sobre el plano de luma (planes[0]),
 * solo dentro del ROI y submuestreando (1 de cada 4 píxeles) para no recorrer
 * todo el frame en el hot path. El frame anterior se pasa como parámetro para
 * el movimiento, manteniendo la función pura.
 */
object MetricsAnalyzer {

    private const val SAMPLE_STEP = 2          // 2 en x y 2 en y => 1 de cada 4
    private const val CLIP_LOW = 16            // por debajo: negro aplastado
    private const val CLIP_HIGH = 239          // por encima: blanco quemado

    fun analyze(
        yBuffer: ByteArray,
        rowStride: Int,
        bufferRect: BufferRect,
        pixelStride: Int = 1,
        previousBuffer: ByteArray? = null,
    ): Metrics {
        if (!isUsable(yBuffer, rowStride, bufferRect) || pixelStride < 1) return EMPTY

        val frame = Frame(yBuffer, previousBuffer, rowStride, pixelStride, bufferRect)
        val acc = Accumulator()
        var y = bufferRect.top
        while (y < bufferRect.bottom) {
            sampleRow(frame, y, acc)
            y += SAMPLE_STEP
        }
        return acc.toMetrics(hasPrevious = previousBuffer != null)
    }

    private fun isUsable(yBuffer: ByteArray, rowStride: Int, rect: BufferRect): Boolean =
        yBuffer.isNotEmpty() && rowStride > 0 && rect.width > 0 && rect.height > 0

    private fun sampleRow(frame: Frame, y: Int, acc: Accumulator) {
        val rowOffset = y * frame.rowStride
        var x = frame.rect.left
        while (x < frame.rect.right) {
            // El byte del píxel x respeta el pixelStride (puede no estar pegado al vecino).
            val index = rowOffset + x * frame.pixelStride
            if (index in frame.y.indices) {
                val luma = frame.y[index].toInt() and 0xFF
                acc.addLuma(luma)
                acc.addFocus(luma, neighbourLuma(frame, index, x))
                acc.addMotion(luma, previousLuma(frame.previous, index))
            }
            x += SAMPLE_STEP
        }
    }

    // Agrupa los datos del frame para no pasar tantos parámetros sueltos.
    private class Frame(
        val y: ByteArray,
        val previous: ByteArray?,
        val rowStride: Int,
        val pixelStride: Int,
        val rect: BufferRect,
    )

    // Vecino a la derecha (a SAMPLE_STEP columnas) para la energía de gradiente.
    private fun neighbourLuma(frame: Frame, index: Int, x: Int): Int? {
        val rightIndex = index + SAMPLE_STEP * frame.pixelStride
        val insideRoi = x + SAMPLE_STEP < frame.rect.right
        return if (insideRoi && rightIndex in frame.y.indices) {
            frame.y[rightIndex].toInt() and 0xFF
        } else {
            null
        }
    }

    private fun previousLuma(previousBuffer: ByteArray?, index: Int): Int? =
        if (previousBuffer != null && index in previousBuffer.indices) {
            previousBuffer[index].toInt() and 0xFF
        } else {
            null
        }

    private class Accumulator {
        private var samples = 0
        private var sumLuma = 0.0
        private var clipped = 0
        private var sumGradient = 0.0
        private var sumMotion = 0.0

        fun addLuma(luma: Int) {
            samples++
            sumLuma += luma
            if (luma <= CLIP_LOW || luma >= CLIP_HIGH) clipped++
        }

        fun addFocus(luma: Int, neighbour: Int?) {
            if (neighbour != null) {
                val gradient = luma - neighbour
                sumGradient += gradient.toDouble() * gradient
            }
        }

        fun addMotion(luma: Int, previous: Int?) {
            if (previous != null) sumMotion += abs(luma - previous)
        }

        fun toMetrics(hasPrevious: Boolean): Metrics {
            if (samples == 0) return EMPTY
            return Metrics(
                focus = (sumGradient / samples).toFloat(),
                meanLuma = (sumLuma / samples).toFloat(),
                clippedFraction = clipped.toFloat() / samples,
                motion = if (hasPrevious) (sumMotion / samples).toFloat() else 0f,
            )
        }
    }

    private val EMPTY = Metrics(focus = 0f, meanLuma = 0f, clippedFraction = 0f, motion = 0f)
}
