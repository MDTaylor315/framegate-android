package com.example.framegate.domain.parser

// WARNING no invalida el plan; ERROR sí (fail-hard de todo el plan)
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
