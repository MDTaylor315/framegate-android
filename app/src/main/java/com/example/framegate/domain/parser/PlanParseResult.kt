package com.example.framegate.domain.parser

import com.example.framegate.domain.model.CapturePlan

data class PlanParseResult(
    val plan: CapturePlan?,
    val diagnostics: List<Diagnostic>,
) {
    val isSuccess: Boolean get() = plan != null

    // Texto plano para mostrar en el HUD, sin UI de error dedicada.
    fun diagnosticsText(): String = diagnostics.joinToString("\n") { it.toString() }
}
