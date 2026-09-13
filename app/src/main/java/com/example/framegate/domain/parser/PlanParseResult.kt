package com.example.framegate.domain.parser

import com.example.framegate.domain.model.CapturePlan

data class PlanParseResult(
    val plan: CapturePlan?,
    val diagnostics: List<Diagnostic>,
) {
    val isSuccess: Boolean get() = plan != null

    // Plain text display for the HUD, without requiring dedicated error UI.
    fun diagnosticsText(): String = diagnostics.joinToString("\n") { it.toString() }
}
