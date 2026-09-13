package com.example.framegate.di

import com.example.framegate.domain.interfaces.Clock
import com.example.framegate.domain.interfaces.SystemClock
import com.example.framegate.domain.interfaces.UploadTransport
import com.example.framegate.domain.model.CapturePlan
import com.example.framegate.domain.model.CaptureStep
import com.example.framegate.domain.model.NormalizedRoi
import com.example.framegate.domain.model.StepType
import com.example.framegate.domain.model.Thresholds
import com.example.framegate.domain.parser.Diagnostic
import com.example.framegate.domain.parser.PlanParser
import com.example.framegate.domain.queue.CaptureStore
import com.example.framegate.domain.queue.FailureScript
import com.example.framegate.domain.queue.FakeUploadTransport
import com.example.framegate.domain.queue.JournalQueueStore
import com.example.framegate.domain.queue.UploadEngine
import java.io.File

/**
 * Contenedor de dependencias a mano. Comparte una única instancia del store, el
 * transporte, el reloj y el motor de subida entre pantallas. Se inicializa desde
 * la Activity con el directorio de la app.
 */
object AppGraph {

    @Volatile
    private var initialized = false

    lateinit var queueStore: JournalQueueStore
        private set

    lateinit var captureStore: CaptureStore
        private set

    lateinit var uploadEngine: UploadEngine
        private set

    lateinit var capturePlan: CapturePlan
        private set

    var planDiagnostics: List<Diagnostic> = emptyList()
        private set

    private lateinit var transport: UploadTransport
    private lateinit var clock: Clock

    /**
     * Inicializa el grafo. [planJson] es el contenido del fixture del plan, que
     * la Activity lee del asset (los assets necesitan Context, por eso se lee
     * afuera y aquí solo se parsea). Idempotente.
     */
    @Synchronized
    fun init(filesDir: File, planJson: String) {
        if (initialized) return

        val journalFile = File(filesDir, "queue-journal.ndjson")
        queueStore = JournalQueueStore(journalFile)
        captureStore = CaptureStore(File(filesDir, "captures"))
        clock = SystemClock()
        // Guion de demostración: la 1ª subida se recupera tras backoff y la 2ª (422)
        // queda terminal, para que la pantalla Queue muestre estados variados y el
        // reintento manual sea observable. El retry/backoff se evalúa igual en tests.
        transport = FakeUploadTransport(FailureScript.demo())
        uploadEngine = UploadEngine(queueStore, transport, clock, captureStore)

        loadPlan(planJson)

        initialized = true
    }

    private fun loadPlan(planJson: String) {
        val result = PlanParser().parse(planJson)
        planDiagnostics = result.diagnostics
        // Si el plan es inválido, se usa uno mínimo por defecto para no bloquear la app.
        capturePlan = result.plan ?: defaultPlan()
    }

    private fun defaultPlan(): CapturePlan = CapturePlan(
        name = "Plan por defecto",
        createdAtIso = "1970-01-01T00:00:00Z",
        scaleFactorRaw = "1.0",
        steps = listOf(
            CaptureStep(
                id = "default",
                type = StepType.SINGLE_FRAME,
                thresholds = Thresholds(),
                roi = NormalizedRoi(),
                requiredHoldFrames = DEFAULT_HOLD_FRAMES,
            )
        ),
    )

    private const val DEFAULT_HOLD_FRAMES = 3
}
