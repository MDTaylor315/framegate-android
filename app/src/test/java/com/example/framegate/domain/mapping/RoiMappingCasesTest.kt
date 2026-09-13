package com.example.framegate.domain.mapping

import com.example.framegate.domain.model.NormalizedRoi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Corre CoordinateMapper contra las coordenadas golden de roi_mapping_cases.json,
 * calculadas a mano desde la fórmula (no desde la salida del código). Tolerancia 0.5 px.
 */
class RoiMappingCasesTest {

    @Serializable
    private data class Cases(
        val buffer: Size,
        val view: SizeF,
        val roi: Roi,
        val cases: List<Case>,
    )

    @Serializable private data class Size(val width: Int, val height: Int)
    @Serializable private data class SizeF(val width: Float, val height: Float)
    @Serializable private data class Roi(val x: Float, val y: Float, val width: Float, val height: Float)
    @Serializable private data class RectI(val left: Int, val top: Int, val right: Int, val bottom: Int)
    @Serializable private data class RectF(val left: Float, val top: Float, val right: Float, val bottom: Float)

    @Serializable
    private data class Case(
        val name: String,
        val sensorRotation: Int,
        val displayRotation: Int,
        val mirrored: Boolean,
        val bufferRect: RectI,
        val viewRect: RectF,
    )

    private fun load(): Cases {
        val text = requireNotNull(javaClass.getResourceAsStream("/fixtures/roi_mapping_cases.json")) {
            "no está roi_mapping_cases.json"
        }.bufferedReader().use { it.readText() }
        return json.decodeFromString(Cases.serializer(), text)
    }

    @Test
    fun `las 4 configuraciones coinciden con el golden a 0,5 px`() {
        val data = load()
        val roi = NormalizedRoi(data.roi.x, data.roi.y, data.roi.width, data.roi.height)

        data.cases.forEach { case ->
            val mapping = CoordinateMapper.mapCoordinates(
                bufferWidth = data.buffer.width,
                bufferHeight = data.buffer.height,
                sensorRotation = case.sensorRotation,
                isMirrored = case.mirrored,
                displayRotation = case.displayRotation,
                viewWidth = data.view.width,
                viewHeight = data.view.height,
                scaleMode = ScaleMode.CROP,
                normalizedRoi = roi,
            )

            assertEquals("${case.name} buffer.left", case.bufferRect.left, mapping.bufferRect.left)
            assertEquals("${case.name} buffer.top", case.bufferRect.top, mapping.bufferRect.top)
            assertEquals("${case.name} buffer.right", case.bufferRect.right, mapping.bufferRect.right)
            assertEquals("${case.name} buffer.bottom", case.bufferRect.bottom, mapping.bufferRect.bottom)

            assertEquals("${case.name} view.left", case.viewRect.left, mapping.viewRect.left, TOLERANCE)
            assertEquals("${case.name} view.top", case.viewRect.top, mapping.viewRect.top, TOLERANCE)
            assertEquals("${case.name} view.right", case.viewRect.right, mapping.viewRect.right, TOLERANCE)
            assertEquals("${case.name} view.bottom", case.viewRect.bottom, mapping.viewRect.bottom, TOLERANCE)
        }
    }

    private companion object {
        const val TOLERANCE = 0.5f
        val json = Json { ignoreUnknownKeys = true }
    }
}
