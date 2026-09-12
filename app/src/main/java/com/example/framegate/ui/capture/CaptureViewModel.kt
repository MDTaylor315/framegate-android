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
import com.example.framegate.domain.model.BufferRect
import com.example.framegate.domain.model.CapturePlan
import com.example.framegate.domain.model.CaptureStep
import com.example.framegate.domain.model.FrameData
import com.example.framegate.domain.model.Metrics
import com.example.framegate.domain.model.NormalizedRoi
import com.example.framegate.domain.model.StepType
import com.example.framegate.domain.model.Thresholds
import com.example.framegate.domain.queue.CaptureStore
import com.example.framegate.domain.queue.JournalQueueStore
import com.example.framegate.domain.queue.QueueItem
import com.example.framegate.domain.queue.SerializableMetrics
import com.example.framegate.domain.queue.SerializableRoi
import com.example.framegate.domain.queue.UploadEngine
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
    private val frameSource: FrameSource = ReplayFrameSource.uniform(),
) : ViewModel() {

    private val _gateState = MutableStateFlow(GateState())
    private val _lastMetrics = MutableStateFlow<Metrics?>(null)
    private val _isCapturing = MutableStateFlow(false)

    val uiState: StateFlow<CaptureUiState> = combine(
        _gateState,
        _lastMetrics,
        _isCapturing,
    ) { gate, metrics, capturing ->
        CaptureUiState(
            gateStatusText = phaseText(gate.phase),
            currentStepIndex = gate.stepIndex,
            fps = if (capturing) FRAMES_PER_SECOND else 0f,
            focus = metrics?.focus ?: 0f,
            brightness = metrics?.meanLuma ?: 0f,
            motion = metrics?.motion ?: 0f,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
        initialValue = CaptureUiState(),
    )

    private val _uiEffect = Channel<CaptureUiEffect>(Channel.BUFFERED)
    val uiEffect = _uiEffect.receiveAsFlow()

    private var captureJob: Job? = null

    private val mockPlan = CapturePlan(
        name = "Plan de Prueba Industrial",
        createdAtIso = "2026-09-11T12:00:00Z",
        scaleFactorRaw = "1.0",
        steps = listOf(
            CaptureStep(
                id = "step_1",
                type = StepType.SINGLE_FRAME,
                // Umbrales laxos para que el frame sintético uniforme dispare en la demo.
                thresholds = Thresholds(minFocus = 0.0, minBrightness = MIN_BRIGHTNESS, maxMotion = 1000.0),
                roi = NormalizedRoi(ROI_X, ROI_Y, ROI_W, ROI_H),
                requiredHoldFrames = REQUIRED_HOLD_FRAMES,
            )
        )
    )

    fun onEvent(event: CaptureUiEvent) {
        when (event) {
            is CaptureUiEvent.StartCapture -> startCapture()
            is CaptureUiEvent.PauseCapture -> pauseCapture()
            is CaptureUiEvent.ToggleDiagnostics -> Unit
        }
    }

    private fun startCapture() {
        if (captureJob?.isActive == true) return

        _gateState.value = GateState()
        _isCapturing.value = true
        sendEffect(CaptureUiEffect.ShowToast("Loop de captura iniciado"))

        captureJob = viewModelScope.launch {
            var frameCount = 0
            while (true) {
                delay(FRAME_INTERVAL_MS)
                val frame = frameSource.getNextFrame() ?: break
                frameCount++
                processFrame(frame, frameCount)
            }
        }
    }

    private fun processFrame(frame: FrameData, frameCount: Int) {
        val roi = BufferRect(0, 0, frame.width, frame.height)
        val metrics = MetricsAnalyzer.analyze(frame.yBuffer, frame.rowStride, roi)
        _lastMetrics.value = metrics
        _gateState.value = GateReducer.reduce(_gateState.value, metrics, mockPlan)

        if (_gateState.value.phase is GatePhase.Armed) {
            val step = mockPlan.steps[_gateState.value.stepIndex]
            queueStore.put(captureItem(frameCount, step, metrics, frame.yBuffer))
            viewModelScope.launch { uploadEngine.drain() }
            sendEffect(CaptureUiEffect.ShowToast("Fotograma capturado y encolado"))
            _gateState.value = GateReducer.advanceToNextStep(GateReducer.fire(_gateState.value), mockPlan)
        }
    }

    private fun captureItem(frameCount: Int, step: CaptureStep, metrics: Metrics, bytes: ByteArray): QueueItem {
        val id = "cap_$frameCount"
        // Se escribe el artefacto en disco ANTES de encolar el registro.
        val artifactPath = captureStore.write(id, bytes)
        return QueueItem(
            id = id,
            idempotencyKey = UUID.randomUUID().toString(),
            timestampEpochMillis = System.currentTimeMillis(),
            planName = mockPlan.name,
            scaleFactorRaw = mockPlan.scaleFactorRaw,
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
        sendEffect(CaptureUiEffect.ShowToast("Loop de captura pausado"))
    }

    private fun sendEffect(effect: CaptureUiEffect) {
        viewModelScope.launch { _uiEffect.send(effect) }
    }

    private companion object {
        const val FRAME_INTERVAL_MS = 300L
        const val FRAMES_PER_SECOND = 3.3f
        const val SUBSCRIPTION_TIMEOUT_MS = 5000L
        const val MIN_BRIGHTNESS = 50.0
        const val REQUIRED_HOLD_FRAMES = 2
        const val ROI_X = 0.25f
        const val ROI_Y = 0.30f
        const val ROI_W = 0.50f
        const val ROI_H = 0.40f
    }
}
