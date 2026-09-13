package com.example.framegate.domain.parser

// WARNING does not invalidate the plan; ERROR does (fail-hard for the entire plan)
enum class Severity { WARNING, ERROR }

data class Diagnostic(
    val severity: Severity,
    val code: String,
    val message: String,
) {
    override fun toString(): String = "[$severity] $code: $message"

    companion object {
        fun warning(code: String, message: String) = Diagnostic(Severity.WARNING, code, message)
        fun error(code: String, message: String) = Diagnostic(Severity.ERROR, code, message)
    }
}
