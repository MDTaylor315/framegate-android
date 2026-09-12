package com.example.framegate.domain.parser

import com.example.framegate.domain.model.CapturePlan
import com.example.framegate.domain.model.CaptureStep
import com.example.framegate.domain.model.NormalizedRoi
import com.example.framegate.domain.model.StepType
import com.example.framegate.domain.model.Thresholds
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * Decodifica un plan tolerando entradas "sucias" (casing mixto, números como
 * string, nulos, claves desconocidas...). Fail-soft por paso, fail-hard solo si
 * el plan entero es inutilizable. Reporta diagnósticos tipados.
 */
class PlanParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(jsonString: String): PlanParseResult {
        val diagnostics = mutableListOf<Diagnostic>()

        val root = runCatching { json.parseToJsonElement(jsonString).jsonObject }.getOrNull()
        if (root == null) {
            diagnostics += Diagnostic.error("INVALID_JSON", "JSON inválido o vacío.")
            return PlanParseResult(plan = null, diagnostics = diagnostics)
        }

        val createdAt = root.string("created_at") ?: DEFAULT_CREATED_AT
        if (!hasTimezone(createdAt)) {
            diagnostics += Diagnostic.warning(
                "TIMESTAMP_NO_TZ",
                "'created_at' ($createdAt) no tiene zona horaria; se asume UTC.",
            )
        }

        val stepsArray = root["steps"]?.jsonArray
        val steps = stepsArray?.let { parseSteps(it, diagnostics) } ?: emptyList()

        val plan = when {
            stepsArray == null -> {
                diagnostics += Diagnostic.error("NO_STEPS", "El plan no contiene la lista 'steps'.")
                null
            }
            steps.isEmpty() -> {
                diagnostics += Diagnostic.error("NO_USABLE_STEPS", "Ningún paso del plan es utilizable.")
                null
            }
            else -> CapturePlan(
                name = root.string("plan_name") ?: DEFAULT_PLAN_NAME,
                createdAtIso = createdAt,
                scaleFactorRaw = root.string("scale_factor") ?: DEFAULT_SCALE_FACTOR,
                steps = steps,
            )
        }
        return PlanParseResult(plan = plan, diagnostics = diagnostics)
    }

    private fun parseSteps(
        array: List<kotlinx.serialization.json.JsonElement>,
        diagnostics: MutableList<Diagnostic>,
    ): List<CaptureStep> {
        val steps = mutableListOf<CaptureStep>()
        val seenIds = mutableSetOf<String>()

        array.forEachIndexed { index, element ->
            val obj = element.jsonObject
            val id = obj.string("StepId", "step_id", "id") ?: "step-$index"

            if (!seenIds.add(id)) {
                diagnostics += Diagnostic.warning(
                    "DUPLICATE_STEP_ID",
                    "Id de paso duplicado '$id'; se descarta la repetición.",
                )
                return@forEachIndexed
            }

            val step = parseStep(obj, id, diagnostics)
            if (step != null) steps += step
        }
        return steps
    }

    private fun parseStep(obj: JsonObject, id: String, diagnostics: MutableList<Diagnostic>): CaptureStep? {
        val typeRaw = obj.string("type") ?: StepType.SINGLE_FRAME.name
        val type = runCatching { StepType.valueOf(typeRaw) }.getOrNull()
        if (type == null) {
            diagnostics += Diagnostic.warning(
                "UNKNOWN_STEP_TYPE",
                "Paso '$id': tipo desconocido '$typeRaw'; se omite el paso.",
            )
            return null
        }

        return CaptureStep(
            id = id,
            type = type,
            thresholds = parseThresholds(obj["thresholds"] as? JsonObject, id, diagnostics),
            roi = parseRoi(obj["roi"] as? JsonObject, id, diagnostics),
            requiredHoldFrames = obj.int("hold_frames") ?: DEFAULT_HOLD_FRAMES,
        )
    }

    private fun parseThresholds(
        obj: JsonObject?,
        id: String,
        diagnostics: MutableList<Diagnostic>,
    ): Thresholds {
        if (obj == null) {
            diagnostics += Diagnostic.warning(
                "MISSING_THRESHOLDS",
                "Paso '$id': 'thresholds' ausente o null; se usan los valores por defecto.",
            )
            return Thresholds()
        }
        return Thresholds(
            minFocus = obj.number("MIN_FOCUS", "min_focus", "focus") ?: Thresholds.DEFAULT_MIN_FOCUS,
            minBrightness = obj.number("MIN_BRIGHTNESS", "min_brightness", "brightness")
                ?: Thresholds.DEFAULT_MIN_BRIGHTNESS,
            maxMotion = obj.number("MAX_MOTION", "max_motion", "motion") ?: Thresholds.DEFAULT_MAX_MOTION,
        )
    }

    private fun parseRoi(obj: JsonObject?, id: String, diagnostics: MutableList<Diagnostic>): NormalizedRoi {
        if (obj == null) {
            diagnostics += Diagnostic.warning(
                "MISSING_ROI",
                "Paso '$id': 'roi' ausente o null; se usa el ROI completo por defecto.",
            )
            return NormalizedRoi()
        }
        return NormalizedRoi(
            x = obj.number("x")?.toFloat() ?: 0f,
            y = obj.number("y")?.toFloat() ?: 0f,
            width = obj.number("width", "w")?.toFloat() ?: 1f,
            height = obj.number("height", "h")?.toFloat() ?: 1f,
        )
    }

    private fun hasTimezone(timestamp: String): Boolean =
        timestamp.endsWith("Z") || TIMEZONE_OFFSET.containsMatchIn(timestamp)

    // Lee la primera clave presente como texto (soporta casing mixto vía alias).
    private fun JsonObject.string(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { (this[it] as? JsonPrimitive)?.content }

    // Lee un número aceptando también el caso "número enviado como string".
    private fun JsonObject.number(vararg keys: String): Double? =
        keys.firstNotNullOfOrNull { key ->
            (this[key] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.content.toDoubleOrNull() }
        }

    private fun JsonObject.int(key: String): Int? =
        (this[key] as? JsonPrimitive)?.let { it.intOrNull ?: it.content.toIntOrNull() }

    companion object {
        private const val DEFAULT_PLAN_NAME = "Plan sin nombre"
        private const val DEFAULT_CREATED_AT = "1970-01-01T00:00:00Z"
        private const val DEFAULT_SCALE_FACTOR = "1.0"
        private const val DEFAULT_HOLD_FRAMES = 5
        private val TIMEZONE_OFFSET = Regex("""[+-]\d{2}:?\d{2}$""")
    }
}
