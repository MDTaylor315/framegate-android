package com.example.framegate.ui.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.framegate.domain.analyzer.MetricsAnalyzer
import com.example.framegate.domain.fixtures.FixtureFrameSource
import com.example.framegate.domain.gate.GateStateMachine
import com.example.framegate.domain.gate.GateStatus
import com.example.framegate.domain.model.CapturePlan
import com.example.framegate.domain.model.CaptureStep
import com.example.framegate.domain.model.NormalizedRoi
import com.example.framegate.domain.model.BufferRect
import com.example.framegate.domain.model.StepType
import com.example.framegate.domain.model.Thresholds
import com.example.framegate.domain.queue.PersistentQueueStore
import com.example.framegate.domain.queue.QueueItem
import com.example.framegate.domain.queue.SimulatedUploadTransport
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

class CaptureViewModel(
    private val queueStore: PersistentQueueStore = PersistentQueueStore,
    private val uploadTransport: SimulatedUploadTransport = SimulatedUploadTransport()
) : ViewModel(){
    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    private val _uiEffect = Channel<CaptureUiEffect>(Channel.BUFFERED)
    val uiEffect = _uiEffect.receiveAsFlow()

    private val gateStateMachine = GateStateMachine()
    private val uploadEngine = UploadEngine(queueStore, uploadTransport)
    private var captureJob: Job? = null

    private val mockPlan = CapturePlan(
        name = "Plan de Prueba Industrial",
        createdAtIso = "2026-09-11T12:00:00Z",
        scaleFactorRaw = "1.0",
        steps = listOf(
            CaptureStep(
                id = "step_1",
                type = StepType.SINGLE_FRAME,
                thresholds = Thresholds(minBrightness = 50.0),
                roi = NormalizedRoi(0.25f, 0.30f, 0.50f, 0.40f),
                requiredHoldFrames = 2
            )
        )
    )

    fun onEvent(event: CaptureUiEvent){
        when(event){
            is CaptureUiEvent.StartCapture -> startCapture()
            is CaptureUiEvent.PauseCapture -> pauseCapture()
            is CaptureUiEvent.ToggleDiagnostics -> toggleDiagnostics()
        }
    }

    private fun startCapture() {
        if (captureJob?.isActive == true) return

        gateStateMachine.arm()
        uploadEngine.startProcessing()
        sendEffect(CaptureUiEffect.ShowToast("Loop de captura iniciado"))
        captureJob = viewModelScope.launch {
            var frameCount = 0
            while (true) {
                frameCount++
                delay(300) //300ms por fotograma

                // frame sintético con luz válida (100)
                val frameBytes = FixtureFrameSource.createUniformFrame(100, 100, 100.toByte())
                val bufferRect = BufferRect(0, 0, 100, 100)

                //Se revisa la iluminacion y la maquina de estado
                val metrics = MetricsAnalyzer.analyze(frameBytes, 100, bufferRect)
                val gateResult = gateStateMachine.evaluate(metrics, mockPlan)

                _uiState.update { currentState ->
                    currentState.copy(
                        gateStatusText = gateResult.statusText,
                        fps = 3.3f
                    )
                }
                // si el obturador se abrió, se guarda la captura en la cola
                if (gateResult.status is GateStatus.GateOpen) {
                    val newItem = QueueItem(
                        id = "cap_$frameCount",
                        timestampIso = "2026-09-11T12:00:${frameCount}Z",
                        planName = mockPlan.name,
                        metrics = metrics
                    )
                    queueStore.enqueue(newItem)
                    sendEffect(CaptureUiEffect.ShowToast("¡Fotograma capturado y encolado!"))
                }

                // Si se completó el paso, reiniciamos el estado de la camara
                if (gateResult.status is GateStatus.Completed) {
                    gateStateMachine.arm()
                }

            }
        }
    }
    private fun pauseCapture() {
        captureJob?.cancel()
        gateStateMachine.reset()
        _uiState.update { currentState -> currentState.copy(gateStatusText = "Pausado") }
        sendEffect(CaptureUiEffect.ShowToast("Loop de captura pausado"))
    }

    private fun toggleDiagnostics(){
        _uiState.update { currentState -> currentState.copy(showDiagnosticsPanel = !currentState.showDiagnosticsPanel)}
    }

    private fun sendEffect(effect: CaptureUiEffect){
        viewModelScope.launch {
            _uiEffect.send(effect)
        }
    }
}