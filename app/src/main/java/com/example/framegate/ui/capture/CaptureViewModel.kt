package com.example.framegate.ui.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.framegate.di.AppGraph
import com.example.framegate.domain.analyzer.MetricsAnalyzer
import com.example.framegate.domain.fixtures.FixtureFrameSource
import com.example.framegate.domain.gate.GateStateMachine
import com.example.framegate.domain.gate.GateStatus
import com.example.framegate.domain.model.BufferRect
import com.example.framegate.domain.model.CapturePlan
import com.example.framegate.domain.model.CaptureStep
import com.example.framegate.domain.model.NormalizedRoi
import com.example.framegate.domain.model.StepType
import com.example.framegate.domain.model.Thresholds
import com.example.framegate.domain.queue.JournalQueueStore
import com.example.framegate.domain.queue.QueueItem
import com.example.framegate.domain.queue.SerializableMetrics
import com.example.framegate.domain.queue.SerializableRoi
import com.example.framegate.domain.queue.UploadEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

// El loop de captura es provisional; el hot path real llega en fases posteriores.
class CaptureViewModel(
    private val queueStore: JournalQueueStore = AppGraph.queueStore,
    private val uploadEngine: UploadEngine = AppGraph.uploadEngine,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    private val _uiEffect = Channel<CaptureUiEffect>(Channel.BUFFERED)
    val uiEffect = _uiEffect.receiveAsFlow()

    private val gateStateMachine = GateStateMachine()
    private var captureJob: Job? = null

    private val mockPlan = CapturePlan(
        name = "Plan de Prueba Industrial",
        createdAtIso = "2026-09-11T12:00:00Z",
        scaleFactorRaw = "1.0",
        steps = listOf(
            CaptureStep(
                id = "step_1",
                type = StepType.SINGLE_FRAME,
                thresholds = Thresholds(minBrightness = MIN_BRIGHTNESS),
                roi = NormalizedRoi(ROI_X, ROI_Y, ROI_W, ROI_H),
                requiredHoldFrames = REQUIRED_HOLD_FRAMES,
            )
        )
    )

    fun onEvent(event: CaptureUiEvent) {
        when (event) {
            is CaptureUiEvent.StartCapture -> startCapture()
            is CaptureUiEvent.PauseCapture -> pauseCapture()
            is CaptureUiEvent.ToggleDiagnostics -> toggleDiagnostics()
        }
    }

    private fun startCapture() {
        if (captureJob?.isActive == true) return

        gateStateMachine.arm()
        sendEffect(CaptureUiEffect.ShowToast("Loop de captura iniciado"))
        captureJob = viewModelScope.launch {
            var frameCount = 0
            while (true) {
                frameCount++
                delay(FRAME_INTERVAL_MS)

                val frameBytes = FixtureFrameSource.createUniformFrame(FRAME_SIZE, FRAME_SIZE, VALID_LUMA)
                val bufferRect = BufferRect(0, 0, FRAME_SIZE, FRAME_SIZE)

                val metrics = MetricsAnalyzer.analyze(frameBytes, FRAME_SIZE, bufferRect)
                val gateResult = gateStateMachine.evaluate(metrics, mockPlan)

                _uiState.update { it.copy(gateStatusText = gateResult.statusText) }

                if (gateResult.status is GateStatus.GateOpen) {
                    val step = mockPlan.steps.first()
                    val item = QueueItem(
                        id = "cap_$frameCount",
                        idempotencyKey = UUID.randomUUID().toString(),
                        timestampEpochMillis = System.currentTimeMillis(),
                        planName = mockPlan.name,
                        scaleFactorRaw = mockPlan.scaleFactorRaw,
                        orientation = 0,
                        roi = SerializableRoi(step.roi.x, step.roi.y, step.roi.width, step.roi.height),
                        metrics = SerializableMetrics.from(metrics),
                    )
                    queueStore.put(item)
                    launch { uploadEngine.drain() }
                    sendEffect(CaptureUiEffect.ShowToast("Fotograma capturado y encolado"))
                }

                if (gateResult.status is GateStatus.Completed) {
                    gateStateMachine.arm()
                }
            }
        }
    }

    private fun pauseCapture() {
        captureJob?.cancel()
        gateStateMachine.reset()
        _uiState.update { it.copy(gateStatusText = "Pausado") }
        sendEffect(CaptureUiEffect.ShowToast("Loop de captura pausado"))
    }

    private fun toggleDiagnostics() {
        _uiState.update { it.copy(showDiagnosticsPanel = !it.showDiagnosticsPanel) }
    }

    private fun sendEffect(effect: CaptureUiEffect) {
        viewModelScope.launch { _uiEffect.send(effect) }
    }

    private companion object {
        const val FRAME_INTERVAL_MS = 300L
        const val FRAME_SIZE = 100
        const val VALID_LUMA: Byte = 100
        const val MIN_BRIGHTNESS = 50.0
        const val REQUIRED_HOLD_FRAMES = 2
        const val ROI_X = 0.25f
        const val ROI_Y = 0.30f
        const val ROI_W = 0.50f
        const val ROI_H = 0.40f
    }
}
