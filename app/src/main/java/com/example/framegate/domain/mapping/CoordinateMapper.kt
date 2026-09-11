package com.example.framegate.domain.mapping

import com.example.framegate.domain.model.BufferRect
import com.example.framegate.domain.model.CoordinateMapping
import com.example.framegate.domain.model.NormalizedRoi
import com.example.framegate.domain.model.ViewRect
import kotlin.math.max
import kotlin.math.min

object CoordinateMapper {

    fun mapCoordinates(
        bufferWidth: Int,
        bufferHeight: Int,
        sensorRotation: Int,
        isMirrored: Boolean = false,
        viewWidth: Float,
        viewHeight: Float,
        scaleMode: ScaleMode = ScaleMode.CROP,
        normalizedRoi: NormalizedRoi
    ): CoordinateMapping{
        val bufLeft = (normalizedRoi.x * bufferWidth).toInt().coerceIn(0, bufferWidth)
        val bufTop = (normalizedRoi.y * bufferHeight).toInt().coerceIn(0, bufferHeight)
        val bufRight = ((normalizedRoi.x + normalizedRoi.width) * bufferWidth).toInt().coerceIn(bufLeft, bufferWidth)
        val bufBottom = ((normalizedRoi.y + normalizedRoi.height) * bufferHeight).toInt().coerceIn(bufTop, bufferHeight)

        val bufferRect = BufferRect (
            left = bufLeft,
            right = bufRight,
            top = bufTop,
            bottom = bufBottom
        )

        //Por si el celular está en modo horizontal
         val (effectiveBufferW, effectiveBufferH) = if (sensorRotation == 90 || sensorRotation == 270){
             Pair(bufferHeight.toFloat(),bufferWidth.toFloat())
         } else{
             Pair(bufferWidth.toFloat(), bufferHeight.toFloat())
         }


        val scale = if (scaleMode == ScaleMode.CROP){
            max(viewWidth / effectiveBufferW, viewHeight / effectiveBufferH)
        }else{
            min(viewWidth/effectiveBufferW, viewHeight /effectiveBufferH)
        }

        //Ancho y Alto reales del area a dibujar
        val scaledW = effectiveBufferW * scale
        val scaledH = effectiveBufferH * scale

        //Verifica donde inicia la foto
        val offsetX = (viewWidth - scaledW) / 2.0f
        val offsetY = (viewHeight - scaledH) / 2.0f

        // 5. Calculamos la posición del ROI dentro de la vista Compose (para el Canvas)
        val viewLeft = offsetX + (normalizedRoi.x * scaledW)
        val viewTop = offsetY + (normalizedRoi.y * scaledH)
        val viewRight = viewLeft + (normalizedRoi.width * scaledW)
        val viewBottom = viewTop + (normalizedRoi.height * scaledH)
        val viewRect = ViewRect(
            left = viewLeft,
            top = viewTop,
            right = viewRight,
            bottom = viewBottom
        )
        return CoordinateMapping(
            bufferRect = bufferRect,
            viewRect = viewRect
        )
    }
}