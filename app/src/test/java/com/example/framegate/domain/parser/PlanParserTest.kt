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
        requireNotNull(javaClass.getResourceAsStream("/fixtures/$name")) { "$name not found" }
            .bufferedReader().use { it.readText() }

    private fun codes(result: PlanParseResult) = result.diagnostics.map { it.code }

    // --- Messy plan matches expectations ---

    @Test
    fun `messy plan is fail-soft and preserves usable steps`() {
        val result = parser.parse(loadFixture("plan_messy.json"))

        assertTrue(result.isSuccess)
        val plan = requireNotNull(result.plan)
        assertEquals(listOf("step-01", "step-03-null-thresholds"), plan.steps.map { it.id })
    }

    @Test
    fun `scale_factor is preserved as string without losing precision`() {
        val result = parser.parse(loadFixture("plan_messy.json"))
        assertEquals("1.234567890123456789", result.plan!!.scaleFactorRaw)
    }

    // --- Tests for each malformation case ---

    @Test
    fun `focus_ratio with mixed casing (FOCUS_RATIO) and string format is resolved`() {
        val result = parser.parse(loadFixture("plan_messy.json"))
        val step = result.plan!!.steps.first { it.id == "step-01" }
        assertEquals(0.7, step.thresholds.focusRatio, 0.001)
    }

    @Test
    fun `number sent as string is properly parsed`() {
        val result = parser.parse(loadFixture("plan_messy.json"))
        val step = result.plan!!.steps.first { it.id == "step-01" }
        assertEquals(60.0, step.thresholds.minBrightness, 0.001)
        assertEquals(5, step.requiredHoldFrames) // "5" as string
    }

    @Test
    fun `nullable key when null uses default value (max_motion null)`() {
        val result = parser.parse(loadFixture("plan_messy.json"))
        val step = result.plan!!.steps.first { it.id == "step-01" }
        assertEquals(15.0, step.thresholds.maxMotion, 0.001)
    }

    @Test
    fun `unknown step type is discarded with diagnostic`() {
        val result = parser.parse(loadFixture("plan_messy.json"))
        assertTrue(result.plan!!.steps.none { it.id == "step-02-desconocido" })
        assertTrue(codes(result).contains("UNKNOWN_STEP_TYPE"))
    }

    @Test
    fun `duplicate step id discards repetition and keeps first`() {
        val result = parser.parse(loadFixture("plan_messy.json"))
        assertEquals(1, result.plan!!.steps.count { it.id == "step-01" })
        assertTrue(codes(result).contains("DUPLICATE_STEP_ID"))
    }

    @Test
    fun `declared object when null uses default values (thresholds null)`() {
        val result = parser.parse(loadFixture("plan_messy.json"))
        val step = result.plan!!.steps.first { it.id == "step-03-null-thresholds" }
        assertEquals(0.6, step.thresholds.focusRatio, 0.001)
        assertTrue(codes(result).contains("MISSING_THRESHOLDS"))
    }

    @Test
    fun `timestamp without timezone flags diagnostic and assumes UTC`() {
        val result = parser.parse(loadFixture("plan_messy.json"))
        assertTrue(codes(result).contains("TIMESTAMP_NO_TZ"))
        assertEquals("2026-09-10T12:00:00", result.plan!!.createdAtIso)
    }

    @Test
    fun `unknown keys are ignored without error`() {
        val result = parser.parse(loadFixture("plan_messy.json"))
        assertFalse(result.diagnostics.any { it.severity == Severity.ERROR })
    }

    // --- Clean plan produces zero warnings ---

    @Test
    fun `clean plan parses with empty diagnostics`() {
        val result = parser.parse(loadFixture("plan_clean.json"))
        assertTrue(result.isSuccess)
        assertEquals(2, result.plan!!.steps.size)
        assertTrue(result.diagnostics.isEmpty())
    }

    // --- Fail-hard cases ---

    @Test
    fun `invalid json is fail-hard with ERROR`() {
        val result = parser.parse("{ esto no es json ")
        assertNull(result.plan)
        assertTrue(codes(result).contains("INVALID_JSON"))
    }

    @Test
    fun `plan without steps is fail-hard`() {
        val result = parser.parse("""{ "plan_name": "x" }""")
        assertNull(result.plan)
        assertTrue(codes(result).contains("NO_STEPS"))
    }

    @Test
    fun `plan without usable steps is fail-hard`() {
        val json = """{ "steps": [ { "id": "a", "type": "DESCONOCIDO" } ] }"""
        val result = parser.parse(json)
        assertNull(result.plan)
        assertTrue(codes(result).contains("NO_USABLE_STEPS"))
    }
}
