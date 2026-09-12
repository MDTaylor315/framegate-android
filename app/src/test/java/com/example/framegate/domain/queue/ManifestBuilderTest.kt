package com.example.framegate.domain.queue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManifestBuilderTest {

    private fun item() = QueueItem(
        id = "cap_001",
        idempotencyKey = "11111111-1111-1111-1111-111111111111",
        timestampEpochMillis = 1_757_592_000_000L,
        planName = "Plan Industrial",
        // Más dígitos de los que un Double puede representar sin perder precisión.
        scaleFactorRaw = "1.234567890123456789",
        orientation = 90,
        roi = SerializableRoi(0.2f, 0.2f, 0.6f, 0.6f),
        metrics = SerializableMetrics(45.6789f, 123.4567f, 0.1234f, 12.3456f),
    )

    @Test
    fun `manifest coincide byte a byte con el golden`() {
        val expected =
            """{"idempotency_key":"11111111-1111-1111-1111-111111111111",""" +
            """"capture_id":"cap_001","plan_name":"Plan Industrial",""" +
            """"captured_at":1757592000000,"orientation":90,""" +
            """"scale_factor":1.234567890123456789,""" +
            """"region":{"origin":"top_left","x":0.2,"y":0.2,"width":0.6,"height":0.6},""" +
            """"measurements":{"focus":45.679,"mean_luma":123.457,"clipped_fraction":0.123,"motion":12.346}}"""

        assertEquals(expected, ManifestBuilder.build(item()))
    }

    @Test
    fun `scale_factor conserva todos sus digitos sin pasar por Double`() {
        val manifest = ManifestBuilder.build(item())
        // El literal aparece intacto y SIN comillas (es número, no string).
        assertTrue(manifest.contains("\"scale_factor\":1.234567890123456789"))
    }

    @Test
    fun `las medidas se redondean a 3 decimales`() {
        val manifest = ManifestBuilder.build(item())
        assertTrue(manifest.contains("\"mean_luma\":123.457"))
        assertTrue(manifest.contains("\"motion\":12.346"))
    }
}
