package com.example.framegate.ui.capture

data class CaptureUiState(
    val gateStatusText: String = "Inactivo",
    val currentStepIndex: Int = 0,
    val fps: Float = 0f,
    // Mediciones del último frame, para el HUD.
    val focus: Float = 0f,
    val brightness: Float = 0f,
    val motion: Float = 0f,
    // Rendimiento del hot path.
    val msPerFrame: Float = 0f,
    val droppedFrames: Int = 0,
    // Rectángulo del overlay en coordenadas de vista, calculado por el mapper.
    val overlayRect: OverlayRect? = null,
    // Diagnósticos del parseo del plan, ya formateados como texto plano.
    val planDiagnostics: List<String> = emptyList(),
    // Etiqueta de la configuración de cámara activa (rotación/espejo).
    val cameraConfigLabel: String = "0°",
)

/** Rectángulo en píxeles de pantalla, listo para dibujar (sin más cálculo en la UI). */
data class OverlayRect(val left: Float, val top: Float, val width: Float, val height: Float)

