package com.example.framegate.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanParserTest {

    private val parser = PlanParser()

    private fun loadFixture(name: String): String =
        requireNotNull(javaClass.getResourceAsStream("/fixtures/$name")) { "no está $name" }
            .bufferedReader().use { it.readText() }

    private fun codes(result: PlanParseResult) = result.diagnostics.map { it.code }

    // --- El plan messy completo coincide con plan_expectations.md ---

    @Test
    fun `plan messy es fail-soft y conserva los pasos utilizables`() {
        val result = parser.parse(loadFixture("plan_messy.json"))

        assertTrue(result.isSuccess)
        val plan = requireNotNull(result.plan)
        assertEquals(listOf("step-01", "step-03-null-thresholds"), plan.steps.map { it.id })
    }

    @Test
    fun `scale_factor se conserva como string sin perder precision`() {
        val result = parser.parse(loadFixture("plan_messy.json"))
        assertEquals("1.234567890123456789", result.plan!!.scaleFactorRaw)
    }

    // --- Un test por cada tipo de malformación ---

    @Test
    fun `focus_ratio con casing mixto (FOCUS_RATIO) y como string se resuelve`() {
        val result = parser.parse(loadFixture("plan_messy.json"))
        val step = result.plan!!.steps.first { it.id == "step-01" }
        assertEquals(0.7, step.thresholds.focusRatio, 0.001)
    }

    @Test
    fun `numero enviado como string se interpreta`() {
        val result = parser.parse(loadFixture("plan_messy.json"))
        val step = result.plan!!.steps.first { it.id == "step-01" }
        assertEquals(60.0, step.thresholds.minBrightness, 0.001)
        assertEquals(5, step.requiredHoldFrames) // "5" como string
    }

    @Test
    fun `nullable a veces null usa el default (max_motion null)`() {
        val result = parser.parse(loadFixture("plan_messy.json"))
        val step = result.plan!!.steps.first { it.id == "step-01" }
        assertEquals(15.0, step.thresholds.maxMotion, 0.001)
    }

    @Test
    fun `tipo de paso desconocido se descarta con diagnostico`() {
        val result = parser.parse(loadFixture("plan_messy.json"))
        assertTrue(result.plan!!.steps.none { it.id == "step-02-desconocido" })
        assertTrue(codes(result).contains("UNKNOWN_STEP_TYPE"))
    }

    @Test
    fun `id de paso duplicado descarta la repeticion y conserva el primero`() {
        val result = parser.parse(loadFixture("plan_messy.json"))
        assertEquals(1, result.plan!!.steps.count { it.id == "step-01" })
        assertTrue(codes(result).contains("DUPLICATE_STEP_ID"))
    }

    @Test
    fun `objeto declarado pero null usa defaults (thresholds null)`() {
        val result = parser.parse(loadFixture("plan_messy.json"))
        val step = result.plan!!.steps.first { it.id == "step-03-null-thresholds" }
        assertEquals(0.6, step.thresholds.focusRatio, 0.001)
        assertTrue(codes(result).contains("MISSING_THRESHOLDS"))
    }

    @Test
    fun `timestamp sin timezone se marca y se asume UTC`() {
        val result = parser.parse(loadFixture("plan_messy.json"))
        assertTrue(codes(result).contains("TIMESTAMP_NO_TZ"))
        assertEquals("2026-09-10T12:00:00", result.plan!!.createdAtIso)
    }

    @Test
    fun `claves desconocidas se ignoran sin error`() {
        val result = parser.parse(loadFixture("plan_messy.json"))
        assertFalse(result.diagnostics.any { it.severity == Severity.ERROR })
    }

    // --- El plan limpio no genera advertencias ---

    @Test
    fun `plan limpio parsea sin diagnosticos`() {
        val result = parser.parse(loadFixture("plan_clean.json"))
        assertTrue(result.isSuccess)
        assertEquals(2, result.plan!!.steps.size)
        assertTrue(result.diagnostics.isEmpty())
    }

    // --- Casos fail-hard ---

    @Test
    fun `json invalido es fail-hard con ERROR`() {
        val result = parser.parse("{ esto no es json ")
        assertNull(result.plan)
        assertTrue(codes(result).contains("INVALID_JSON"))
    }

    @Test
    fun `plan sin steps es fail-hard`() {
        val result = parser.parse("""{ "plan_name": "x" }""")
        assertNull(result.plan)
        assertTrue(codes(result).contains("NO_STEPS"))
    }

    @Test
    fun `plan sin pasos utilizables es fail-hard`() {
        val json = """{ "steps": [ { "id": "a", "type": "DESCONOCIDO" } ] }"""
        val result = parser.parse(json)
        assertNull(result.plan)
        assertTrue(codes(result).contains("NO_USABLE_STEPS"))
    }
}
