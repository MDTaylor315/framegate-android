package com.example.framegate.domain.queue

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Constructs the manifest JSON according to contract specifications: epoch-millis timestamps,
 * integer orientation, metrics rounded to 3 decimal places, and scale_factor preserved as
 * a raw unquoted number (avoiding Double precision loss). Keys are emitted in stable order.
 */
object ManifestBuilder {

    private const val MEASUREMENT_SCALE = 3

    private val json = Json { prettyPrint = false }

    fun build(item: QueueItem): String =
        json.encodeToString(JsonObject.serializer(), buildManifestObject(item))

    private fun buildManifestObject(item: QueueItem): JsonObject = buildJsonObject {
        put("idempotency_key", item.idempotencyKey)
        put("capture_id", item.id)
        put("plan_name", item.planName)
        put("captured_at", item.timestampEpochMillis)
        put("orientation", item.orientation)
        put("scale_factor", rawNumber(item.scaleFactorRaw))
        put("region", buildJsonObject {
            put("origin", "top_left")
            put("x", round3(item.roi.x))
            put("y", round3(item.roi.y))
            put("width", round3(item.roi.width))
            put("height", round3(item.roi.height))
        })
        put("measurements", buildJsonObject {
            put("focus", round3(item.metrics.focus))
            put("mean_luma", round3(item.metrics.meanLuma))
            put("clipped_fraction", round3(item.metrics.clippedFraction))
            put("motion", round3(item.metrics.motion))
        })
    }

    private fun round3(value: Float): Double =
        BigDecimal(value.toDouble())
            .setScale(MEASUREMENT_SCALE, RoundingMode.HALF_UP)
            .toDouble()

    // JsonUnquotedLiteral emits the raw string representation (number without quotes),
    // ensuring scale_factor preserves full precision without passing through Double.
    @OptIn(ExperimentalSerializationApi::class)
    private fun rawNumber(raw: String): JsonPrimitive = JsonUnquotedLiteral(raw)
}
