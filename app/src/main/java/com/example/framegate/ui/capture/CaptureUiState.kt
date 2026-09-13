package com.example.framegate.ui.capture

data class CaptureUiState(
    val gateStatusText: String = "Inactive",
    val currentStepIndex: Int = 0,
    val fps: Float = 0f,
    // Latest frame measurements, for HUD display.
    val focus: Float = 0f,
    val brightness: Float = 0f,
    val motion: Float = 0f,
    // Hot-path processing performance metrics.
    val msPerFrame: Float = 0f,
    val droppedFrames: Int = 0,
    // Overlay bounding box in view space coordinates, computed by the coordinate mapper.
    val overlayRect: OverlayRect? = null,
    // Plan parse diagnostics, formatted as plain text lines.
    val planDiagnostics: List<String> = emptyList(),
    // Active camera configuration label (rotation/mirroring).
    val cameraConfigLabel: String = "0°",
)

/** Bounding rectangle in display pixels, ready for rendering (no further UI calculations). */
data class OverlayRect(val left: Float, val top: Float, val width: Float, val height: Float)

