package com.example.framegate.domain.parser

import com.example.framegate.domain.model.CapturePlan


data class PlanParseResult(
    val plan: CapturePlan?,
    val diagnostics: List<String>,
    val isSuccess: Boolean
)
