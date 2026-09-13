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
import com.example.framegate.domain.model.BufferRect
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
 * El loop pide frames a [FrameSource], los mide y alimenta los flujos fuente
 * (_gateState, _lastMetrics, _isCapturing). El uiState se DERIVA de esos flujos
 * con combine/stateIn; no se muta a mano.
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

    private var viewSize: Pair<Int, Int>? = null

    // Geometría del último frame recibido, para dimensionar el overlay con datos reales
    // (no constantes) y respetar su rotación/espejo.
    private var frameGeometry: FrameData? = null

    // El plan se carga desde el fixture (parseado en AppGraph), no se hardcodea.
    private val plan: CapturePlan = AppGraph.capturePlan

    // Diagnósticos del plan ya parseado; estables en la sesión (no se re-parsea). Texto plano.
    private val planDiagnostics: List<String> = AppGraph.planDiagnostics.map { it.toString() }

    val uiState: StateFlow<CaptureUiState> = combine(
        _gateState,
        _lastMetrics,
        _isCapturing,
        _perf,
        _overlay,
    ) { gate, metrics, capturing, perf, overlay ->
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
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
        initialValue = CaptureUiState(planDiagnostics = planDiagnostics),
    )

    // RENDEZVOUS + trySend: un efecto emitido sin colector activo (p. ej. durante
    // la recreación por rotación) se descarta en vez de bufferizarse y reemitirse.
    private val _uiEffect = Channel<CaptureUiEffect>(Channel.RENDEZVOUS)
    val uiEffect = _uiEffect.receiveAsFlow()

    private var captureJob: Job? = null

    fun onEvent(event: CaptureUiEvent) {
        when (event) {
            is CaptureUiEvent.StartCapture -> startCapture()
            is CaptureUiEvent.PauseCapture -> pauseCapture()
            is CaptureUiEvent.ViewSizeChanged -> {
                viewSize = event.width to event.height
                recomputeOverlay()
            }
        }
    }

    // Calcula el recuadro del overlay con el mapper (nunca en el Composable).
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

        // El análisis (trabajo de CPU) corre fuera del main thread.
        captureJob = viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                delay(FRAME_INTERVAL_MS)
                val frame = frameSource.getNextFrame() ?: break
                processFrame(frame)
            }
        }
    }

    // ROI del paso en coordenadas del buffer del frame actual, vía el mismo mapper que el overlay.
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

    private fun processFrame(frame: FrameData) {
        // Guarda la geometría real del frame y reproyecta el overlay con ella.
        frameGeometry = frame
        recomputeOverlay()

        // Se mide solo dentro del ROI del paso activo, no el frame completo.
        val step = plan.steps[_gateState.value.stepIndex.coerceIn(0, plan.steps.lastIndex)]
        val roi = stepBufferRect(step, frame)

        val startNs = System.nanoTime()
        val metrics = MetricsAnalyzer.analyze(frame.yBuffer, frame.rowStride, roi, frame.pixelStride)
        val elapsedMs = (System.nanoTime() - startNs) / NANOS_PER_MILLI

        // Si analizar tardó más que el intervalo entre frames, se habría perdido uno.
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
        // Id único por registro (no derivado del contador de frame, que reinicia por
        // corrida y colisionaría al reencolar o relanzar la app).
        val id = "cap_${UUID.randomUUID()}"
        // Se escribe el artefacto en disco ANTES de encolar el registro.
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

    private fun pauseCapture() {
        captureJob?.cancel()
        _gateState.value = GateState()
        _isCapturing.value = false
        _perf.value = PerfStats()
        sendEffect(CaptureUiEffect.ShowToast("Loop de captura pausado"))
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
