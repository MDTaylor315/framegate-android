package com.example.framegate.ui.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.framegate.di.AppGraph
import com.example.framegate.domain.analyzer.MetricsAnalyzer
import com.example.framegate.domain.fixtures.ReplayFrameSource
import com.example.framegate.domain.gate.GatePhase
import com.example.framegate.domain.gate.GateReducer
import com.example.framegate.domain.gate.GateState
import com.example.framegate.domain.interfaces.FrameSource
import com.example.framegate.domain.mapping.CoordinateMapper
import com.example.framegate.domain.mapping.ScaleMode
import com.example.framegate.domain.model.BufferRect
import com.example.framegate.domain.model.CameraConfig
import com.example.framegate.domain.model.CapturePlan
import com.example.framegate.domain.model.CaptureStep
import com.example.framegate.domain.model.FrameData
import com.example.framegate.domain.model.Metrics
import com.example.framegate.domain.model.height
import com.example.framegate.domain.model.width
import com.example.framegate.domain.queue.CaptureStore
import com.example.framegate.domain.queue.JournalQueueStore
import com.example.framegate.domain.queue.QueueItem
import com.example.framegate.domain.queue.SerializableMetrics
import com.example.framegate.domain.queue.SerializableRoi
import com.example.framegate.domain.queue.UploadEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * The capture loop pulls frames from [FrameSource], measures metrics, and updates source flows
 * (_gateState, _lastMetrics, _isCapturing). The uiState is DERIVED from these flows using
 * combine/stateIn; it is never mutated manually.
 */
class CaptureViewModel(
    private val queueStore: JournalQueueStore = AppGraph.queueStore,
    private val captureStore: CaptureStore = AppGraph.captureStore,
    private val uploadEngine: UploadEngine = AppGraph.uploadEngine,
    private val frameSource: FrameSource = ReplayFrameSource.assessment(),
) : ViewModel() {

    private val _gateState = MutableStateFlow(GateState())
    private val _lastMetrics = MutableStateFlow<Metrics?>(null)
    private val _isCapturing = MutableStateFlow(false)
    private val _perf = MutableStateFlow(PerfStats())
    private val _overlay = MutableStateFlow<OverlayRect?>(null)
    private val _cameraConfig = MutableStateFlow(CameraConfig.ROT_0)

    private var viewSize: Pair<Int, Int>? = null

    // Geometry of the last received frame, used to size the overlay with actual frame data
    // (rather than hardcoded values) and respect rotation/mirroring.
    private var frameGeometry: FrameData? = null

    // The plan is loaded from the fixture (parsed in AppGraph), not hardcoded.
    private val plan: CapturePlan = AppGraph.capturePlan

    // Parsed plan diagnostics; stable for the session (never re-parsed). Formatted plain text.
    private val planDiagnostics: List<String> = AppGraph.planDiagnostics.map { it.toString() }

    // Overlay and camera config are combined separately to keep the main combine under 5 streams;
    // both affect view-space rendering.
    private val overlayAndConfig = combine(_overlay, _cameraConfig) { overlay, config -> overlay to config }

    val uiState: StateFlow<CaptureUiState> = combine(
        _gateState,
        _lastMetrics,
        _isCapturing,
        _perf,
        overlayAndConfig,
    ) { gate, metrics, capturing, perf, (overlay, config) ->
        CaptureUiState(
            gateStatusText = phaseText(gate.phase),
            currentStepIndex = gate.stepIndex,
            fps = if (capturing) FRAMES_PER_SECOND else 0f,
            focus = metrics?.focus ?: 0f,
            brightness = metrics?.meanLuma ?: 0f,
            motion = metrics?.motion ?: 0f,
            msPerFrame = perf.msPerFrame,
            droppedFrames = perf.dropped,
            overlayRect = overlay,
            planDiagnostics = planDiagnostics,
            cameraConfigLabel = config.label,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
        initialValue = CaptureUiState(planDiagnostics = planDiagnostics),
    )

    // RENDEZVOUS + trySend: an effect emitted without an active collector (e.g., during
    // Activity rotation recreation) is dropped rather than buffered and re-emitted.
    private val _uiEffect = Channel<CaptureUiEffect>(Channel.RENDEZVOUS)
    val uiEffect = _uiEffect.receiveAsFlow()

    private var captureJob: Job? = null

    fun onEvent(event: CaptureUiEvent) {
        when (event) {
            is CaptureUiEvent.StartCapture -> startCapture()
            is CaptureUiEvent.StopCapture -> stopCapture()
            is CaptureUiEvent.CycleCameraConfig -> {
                _cameraConfig.value = _cameraConfig.value.next()
                recomputeOverlay()
            }
            is CaptureUiEvent.ViewSizeChanged -> {
                viewSize = event.width to event.height
                recomputeOverlay()
            }
        }
    }

    // Computes the overlay rectangle via coordinate mapper (never inside Composables).
    private fun recomputeOverlay() {
        val (w, h) = viewSize?.takeIf { it.first > 0 && it.second > 0 } ?: return
        val frame = frameGeometry ?: return
        val step = plan.steps[_gateState.value.stepIndex.coerceIn(0, plan.steps.lastIndex)]
        val mapping = CoordinateMapper.mapCoordinates(
            bufferWidth = frame.width,
            bufferHeight = frame.height,
            sensorRotation = frame.sensorRotation,
            isMirrored = frame.isMirrored,
            viewWidth = w.toFloat(),
            viewHeight = h.toFloat(),
            // FIT mode scales buffer into view, allowing full ROI box visibility.
            scaleMode = ScaleMode.FIT,
            normalizedRoi = step.roi,
        )
        val r = mapping.viewRect
        _overlay.value = OverlayRect(r.left, r.top, r.width, r.height)
    }

    private fun startCapture() {
        if (captureJob?.isActive == true) return

        _gateState.value = GateState()
        _isCapturing.value = true
        sendEffect(CaptureUiEffect.ShowToast("Loop de captura iniciado"))

        // Analysis (CPU work) runs off the main thread.
        captureJob = viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                delay(FRAME_INTERVAL_MS)
                val frame = frameSource.getNextFrame() ?: break
                processFrame(frame)
            }
        }
    }

    // Step ROI in buffer coordinates for the current frame, using the same mapper as overlay.
    private fun stepBufferRect(step: CaptureStep, frame: FrameData): BufferRect =
        CoordinateMapper.mapCoordinates(
            bufferWidth = frame.width,
            bufferHeight = frame.height,
            sensorRotation = frame.sensorRotation,
            isMirrored = frame.isMirrored,
            viewWidth = frame.width.toFloat(),
            viewHeight = frame.height.toFloat(),
            normalizedRoi = step.roi,
        ).bufferRect

    private fun processFrame(rawFrame: FrameData) {
        // Applies active camera configuration to fixture frame (which lacks orientation metadata),
        // exercising mapping across all 4 camera configurations.
        val config = _cameraConfig.value
        val frame = rawFrame.copy(sensorRotation = config.sensorRotation, isMirrored = config.isMirrored)

        // Saves actual frame geometry and re-project overlay with it.
        frameGeometry = frame
        recomputeOverlay()

        // Measures strictly within the active step ROI, not the whole frame.
        val step = plan.steps[_gateState.value.stepIndex.coerceIn(0, plan.steps.lastIndex)]
        val roi = stepBufferRect(step, frame)

        val startNs = System.nanoTime()
        val metrics = MetricsAnalyzer.analyze(frame.yBuffer, frame.rowStride, roi, frame.pixelStride)
        val elapsedMs = (System.nanoTime() - startNs) / NANOS_PER_MILLI

        // If processing takes longer than frame interval, a frame was dropped.
        val dropped = _perf.value.dropped + if (elapsedMs > FRAME_INTERVAL_MS) 1 else 0
        _perf.value = PerfStats(msPerFrame = elapsedMs.toFloat(), dropped = dropped)

        _lastMetrics.value = metrics
        _gateState.value = GateReducer.reduce(_gateState.value, metrics, plan)

        if (_gateState.value.phase is GatePhase.Armed) {
            val step = plan.steps[_gateState.value.stepIndex]
            queueStore.put(captureItem(step, metrics, frame.yBuffer))
            viewModelScope.launch { uploadEngine.drain() }
            sendEffect(CaptureUiEffect.ShowToast("Fotograma capturado y encolado"))
            _gateState.value = GateReducer.advanceToNextStep(GateReducer.fire(_gateState.value), plan)
        }
    }

    private fun captureItem(step: CaptureStep, metrics: Metrics, bytes: ByteArray): QueueItem {
        // Unique ID per capture record (not derived from frame counter, which resets per run
        // and would collide when re-queuing or restarting the app).
        val id = "cap_${UUID.randomUUID()}"
        // Binary artifact is written to disk BEFORE queuing the record.
        val artifactPath = captureStore.write(id, bytes)
        return QueueItem(
            id = id,
            idempotencyKey = UUID.randomUUID().toString(),
            timestampEpochMillis = System.currentTimeMillis(),
            planName = plan.name,
            scaleFactorRaw = plan.scaleFactorRaw,
            orientation = 0,
            roi = SerializableRoi(step.roi.x, step.roi.y, step.roi.width, step.roi.height),
            metrics = SerializableMetrics.from(metrics),
            artifactPath = artifactPath,
        )
    }

    private fun phaseText(phase: GatePhase): String = when (phase) {
        is GatePhase.Blocked ->
            if (phase.failing.isEmpty()) "Esperando" else "Bloqueado por ${phase.failing.joinToString()}"
        is GatePhase.Holding -> "Estabilizando (${phase.held}/${phase.required})"
        is GatePhase.Armed -> "¡Obturador abierto!"
        is GatePhase.Fired -> "Capturando..."
        is GatePhase.Complete -> "Plan completado"
    }

    private fun stopCapture() {
        // Stopping cancels loop and resets to start; Starting again restarts from step 1.
        captureJob?.cancel()
        _gateState.value = GateState()
        _isCapturing.value = false
        _perf.value = PerfStats()
        // Redraws overlay with step 1 ROI, consistent with reset state.
        recomputeOverlay()
        sendEffect(CaptureUiEffect.ShowToast("Loop de captura detenido"))
    }

    private fun sendEffect(effect: CaptureUiEffect) {
        _uiEffect.trySend(effect)
    }

    private companion object {
        const val FRAME_INTERVAL_MS = 300L
        const val FRAMES_PER_SECOND = 3.3f
        const val SUBSCRIPTION_TIMEOUT_MS = 5000L
        const val NANOS_PER_MILLI = 1_000_000.0
    }
}

private data class PerfStats(val msPerFrame: Float = 0f, val dropped: Int = 0)
