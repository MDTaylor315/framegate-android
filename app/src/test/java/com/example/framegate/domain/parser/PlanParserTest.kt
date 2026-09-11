package com.example.framegate.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanParserTest {

    private val parser = PlanParser()

    @Test
    fun parse_planMessy_conservaPasosValidosYGeneraDiagnosticos() {
        val messyJson = """
            {
              "plan_name": "Plan de Pruebas Sucio FrameGate",
              "created_at": "2026-09-10T12:00:00",
              "scale_factor": "1.234567890123456789",
              "steps": [
                {
                  "StepId": "step-01",
                  "type": "SINGLE_FRAME",
                  "thresholds": {
                    "MIN_FOCUS": 15.0,
                    "min_brightness": "60.0"
                  },
                  "roi": { "x": 0.2, "y": 0.2, "width": 0.6, "height": 0.6 },
                  "hold_frames": 5
                },
                {
                  "StepId": "step-01",
                  "type": "SINGLE_FRAME",
                  "thresholds": { "MIN_FOCUS": 20.0 },
                  "roi": { "x": 0.1, "y": 0.1, "width": 0.8, "height": 0.8 }
                },
                {
                  "StepId": "step-02-desconocido",
                  "type": "PASO_DESCONOCIDO_FUTURO"
                },
                {
                  "StepId": "step-03-null-thresholds",
                  "type": "SINGLE_FRAME",
                  "thresholds": null
                }
              ]
            }
        """.trimIndent()

        val result = parser.parse(messyJson)

        // 1. Verificamos que el parseo general fue exitoso (Fail-Soft)
        assertTrue(result.isSuccess)
        assertNotNull(result.plan)

        val plan = result.plan!!
        assertEquals("Plan de Pruebas Sucio FrameGate", plan.name)

        // 2. Verificamos que scale_factor conservó sus 18 decimales como String sin perder precisión
        assertEquals("1.234567890123456789", plan.scaleFactorRaw)

        // 3. Verificamos que se saltó el paso desconocido y conservó los 3 pasos utilizables
        assertEquals(3, plan.steps.size)

        // 4. Verificamos que el paso 1 leyó el número enviado como string ("60.0")
        assertEquals(60.0, plan.steps[0].thresholds.minBrightness, 0.001)

        // 5. Verificamos que el paso 3 (thresholds null) aplicó el valor por defecto documentado
        assertEquals(10.0, plan.steps[2].thresholds.minFocus, 0.001)

        // 6. Verificamos que se generaron los diagnósticos en texto plano
        assertTrue(result.diagnostics.isNotEmpty())
        assertTrue(result.diagnostics.any { it.contains("PASO_DESCONOCIDO_FUTURO") })
        assertTrue(result.diagnostics.any { it.contains("thresholds' es null") })
    }
}
