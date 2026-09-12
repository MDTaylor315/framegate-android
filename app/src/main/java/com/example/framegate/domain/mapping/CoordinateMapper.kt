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
        displayRotation: Int = 0,
        viewWidth: Float,
        viewHeight: Float,
        scaleMode: ScaleMode = ScaleMode.CROP,
        normalizedRoi: NormalizedRoi
    ): CoordinateMapping{
        // Se aplica espejo y rotación al ROI antes de proyectarlo.
        val roi = RoiTransform.transform(normalizedRoi, sensorRotation, displayRotation, isMirrored)

        val bufLeft = (roi.x * bufferWidth).toInt().coerceIn(0, bufferWidth)
        val bufTop = (roi.y * bufferHeight).toInt().coerceIn(0, bufferHeight)
        val bufRight = ((roi.x + roi.width) * bufferWidth).toInt().coerceIn(bufLeft, bufferWidth)
        val bufBottom = ((roi.y + roi.height) * bufferHeight).toInt().coerceIn(bufTop, bufferHeight)

        val bufferRect = BufferRect (
            left = bufLeft,
            right = bufRight,
            top = bufTop,
            bottom = bufBottom
        )

        // La rotación neta decide si el buffer se ve apaisado (se intercambian W/H).
        val rotation = RoiTransform.effectiveRotation(sensorRotation, displayRotation)
        val (effectiveBufferW, effectiveBufferH) = if (RoiTransform.swapsDimensions(rotation)){
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

        // Posición del ROI (ya transformado) dentro de la vista, para el Canvas.
        val viewLeft = offsetX + (roi.x * scaledW)
        val viewTop = offsetY + (roi.y * scaledH)
        val viewRight = viewLeft + (roi.width * scaledW)
        val viewBottom = viewTop + (roi.height * scaledH)
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
