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
import kotlinx.serialization.json.jsonPrimitive

class PlanParser {

    private val jsonConfig = Json{
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    fun parse(jsonString: String): PlanParseResult{
        val diagnostics = mutableListOf<String>()

        return try{
            val rootElement = jsonConfig.parseToJsonElement(jsonString).jsonObject

            val planName = rootElement.str("plan_name") ?: "Plan Sin Nombre"
            val createdAt = rootElement.str("created_at") ?: "1970-01-01T00:00:00Z"

            if(!createdAt.endsWith("Z") && !createdAt.contains("+")){
                diagnostics.add("Advertencia: 'created_at' ($createdAt) no especifica zona horaria. Se asume UTC.")
            }

            val scaleFactorRaw = rootElement.str("scale_factor") ?: "1.0"


            val stepsArray = rootElement["steps"]?.jsonArray ?: run{
                diagnostics.add("Error fatal: No se encontró la lista 'steps' en el JSON.")
                return PlanParseResult(plan = null, diagnostics = diagnostics, isSuccess = false)
            }

            val validSteps =mutableListOf<CaptureStep>()
            val seenStepIds = mutableSetOf<String>()

            for((index, stepElement) in stepsArray.withIndex()){
                val stepObj = stepElement.jsonObject

                val stepId = stepObj.str("StepId", "step_id", "id") ?: "step-$index"

                if(seenStepIds.contains(stepId)){
                    diagnostics.add("Advertencia en paso index $index: ID de paso duplicado '$stepId'.")
                }
                seenStepIds.add(stepId)

                val typeRaw = stepObj.str("type") ?: "SINGLE_FRAME"
                val stepType = try{
                    StepType.valueOf(typeRaw)
                }catch(e: IllegalArgumentException){
                    diagnostics.add("Fail-Soft paso '$stepId': Tipo de paso desconocido '$typeRaw'. Se salta el paso")
                    continue
                }

                val thresholds = parseObjectSafely(stepObj, "thresholds", stepId, diagnostics, { Thresholds() }) {
                    parseThresholds(it, stepId, diagnostics)
                }


                val roi = parseObjectSafely(stepObj, "roi", stepId, diagnostics, { NormalizedRoi() }) {
                    parseRoi(it)
                }


                val holdFrames = stepObj["hold_frames"]?.jsonPrimitive?.intOrNull
                    ?: stepObj["hold_frames"]?.jsonPrimitive?.content?.toIntOrNull()
                    ?: 5

                validSteps.add(
                    CaptureStep(
                        id = stepId,
                        type = stepType,
                        thresholds = thresholds,
                        roi = roi,
                        requiredHoldFrames = holdFrames
                    )
                )
            }

            if(validSteps.isEmpty()){
                diagnostics.add("Error fatal: No se encontraron pasos válidos utilizables en el plan.")
                PlanParseResult(plan = null, diagnostics = diagnostics, isSuccess = false)
            }else{
                val plan = CapturePlan(
                    name = planName,
                    createdAtIso = createdAt,
                    scaleFactorRaw = scaleFactorRaw,
                    steps = validSteps
                )
                PlanParseResult(plan = plan, diagnostics = diagnostics, isSuccess = true)

            }


        }catch (e: Exception){
            diagnostics.add("Error fatal al parsear sintaxis del Json: ${e.localizedMessage}")
            PlanParseResult(plan = null, diagnostics = diagnostics, isSuccess = false)
        }
    }

    private fun findStringKey(obj: JsonObject, vararg keys: String): String?{
        for(key in keys){
            val element = obj[key]
            if (element != null) return element.jsonPrimitive.content
        }
        return null
    }

    private fun parseThresholds(obj: JsonObject, stepId: String, diagnostics: MutableList<String>): Thresholds {
        return Thresholds(
            minFocus = obj.num(arrayOf("MIN_FOCUS", "min_focus", "focus"), "min_focus", stepId, Thresholds.DEFAULT_MIN_FOCUS, diagnostics),
            minBrightness = obj.num(arrayOf("MIN_BRIGHTNESS", "min_brightness", "brightness"), "min_brightness", stepId, Thresholds.DEFAULT_MIN_BRIGHTNESS, diagnostics),
            maxMotion = obj.num(arrayOf("MAX_MOTION", "max_motion", "motion"), "max_motion", stepId, Thresholds.DEFAULT_MAX_MOTION, diagnostics)
        )
    }

    private fun parseDoubleValue(obj: JsonObject, vararg keys: String): Double? {
        for (key in keys) {
            val element = obj[key] ?: continue
            val prim = element.jsonPrimitive
            return prim.doubleOrNull ?: prim.content.toDoubleOrNull()
        }
        return null
    }

    private fun parseRoi(obj: JsonObject): NormalizedRoi {
        val x = parseDoubleValue(obj, "x")?.toFloat() ?: 0.0f
        val y = parseDoubleValue(obj, "y")?.toFloat() ?: 0.0f
        val w = parseDoubleValue(obj, "width", "w")?.toFloat() ?: 1.0f
        val h = parseDoubleValue(obj, "height", "h")?.toFloat() ?: 1.0f
        return NormalizedRoi(x = x, y = y, width = w, height = h)
    }

    ///////////////////////////////////////////////
    private inline fun <T> parseObjectSafely(
        stepObj: JsonObject,
        key: String,
        stepId: String,
        diagnostics: MutableList<String>,
        default: () -> T,
        parser: (JsonObject) -> T
    ): T {
        val element = stepObj[key]
        val obj = if (element != null && element !is JsonPrimitive) element.jsonObject else null

        return if (obj != null) {
            parser(obj)
        } else {
            diagnostics.add("Advertencia en paso '$stepId': '$key' es null u omiso. Se aplica valor por defecto.")
            default()
        }
    }

    private fun JsonObject.str(vararg keys: String): String? {
        for (key in keys) {
            val element = this[key] ?: continue
            if (element !is JsonPrimitive) continue
            return element.jsonPrimitive.content
        }
        return null
    }

    private fun JsonObject.num(
        keys: Array<String>,
        name: String,
        stepId: String,
        default: Double,
        diagnostics: MutableList<String>
    ): Double {
        val value = keys.firstNotNullOfOrNull { key ->
            (this[key] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.content.toDoubleOrNull() }
        }
        if (value == null) {
            diagnostics.add("Advertencia en paso '$stepId': '$name' no especificado. Se usa por defecto $default.")
        }
        return value ?: default
    }


}
