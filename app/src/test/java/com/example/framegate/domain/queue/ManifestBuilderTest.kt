package com.example.framegate.domain.queue

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManifestBuilderTest {

    private fun item() = QueueItem(
        id = "cap_001",
        idempotencyKey = "11111111-1111-1111-1111-111111111111",
        timestampEpochMillis = 1_757_592_000_000L,
        planName = "Plan Industrial",
        // More digits than a Double can represent without precision loss.
        scaleFactorRaw = "1.234567890123456789",
        orientation = 90,
        roi = SerializableRoi(0.2f, 0.2f, 0.6f, 0.6f),
        metrics = SerializableMetrics(45.6789f, 123.4567f, 0.1234f, 12.3456f),
    )

    private fun loadGolden(): String =
        requireNotNull(javaClass.getResourceAsStream("/fixtures/manifest_golden.json")) {
            "manifest_golden.json missing"
        }.bufferedReader().use { it.readText() }.trim()

    @Test
    fun `manifest matches golden byte for byte`() {
        assertEquals(loadGolden(), ManifestBuilder.build(item()))
    }

    @Test
    fun `scale_factor preserves all digits without converting through Double`() {
        val manifest = ManifestBuilder.build(item())
        // Literal appears intact and UNQUOTED (number type, not string).
        assertTrue(manifest.contains("\"scale_factor\":1.234567890123456789"))
    }

    @Test
    fun `measurements are rounded to 3 decimal places`() {
        val manifest = ManifestBuilder.build(item())
        assertTrue(manifest.contains("\"mean_luma\":123.457"))
        assertTrue(manifest.contains("\"motion\":12.346"))
    }

    @Test
    fun `manifest contains exactly the fields required by contract`() {
        val manifest = Json.parseToJsonElement(ManifestBuilder.build(item())).jsonObject
        assertEquals(TOP_LEVEL_FIELDS, manifest.keys)
        assertEquals(REGION_FIELDS, manifest["region"]!!.jsonObject.keys)
        assertEquals(MEASUREMENT_FIELDS, manifest["measurements"]!!.jsonObject.keys)
    }

    private companion object {
        val TOP_LEVEL_FIELDS = setOf(
            "idempotency_key", "capture_id", "plan_name", "captured_at",
            "orientation", "scale_factor", "region", "measurements",
        )
        val REGION_FIELDS = setOf("origin", "x", "y", "width", "height")
        val MEASUREMENT_FIELDS = setOf("focus", "mean_luma", "clipped_fraction", "motion")
    }
}
