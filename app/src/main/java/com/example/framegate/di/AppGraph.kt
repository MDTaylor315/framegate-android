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
 * Manual dependency container. Shares a single instance of the store, transport,
 * clock, and upload engine across screens. Initialized from the Activity using
 * the app's files directory.
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
     * Initializes the graph. [planJson] is the raw content of the plan fixture, which
     * the Activity reads from assets (assets require Context, so it is read
     * outside and only parsed here). Idempotent.
     */
    @Synchronized
    fun init(filesDir: File, planJson: String) {
        if (initialized) return

        val journalFile = File(filesDir, "queue-journal.ndjson")
        queueStore = JournalQueueStore(journalFile)
        captureStore = CaptureStore(File(filesDir, "captures"))
        clock = SystemClock()
        // Demo script: 1st upload recovers after backoff and 2nd (422)
        // stays terminal, so the Queue screen displays varied states and manual
        // retries are observable. Retry/backoff behavior is evaluated similarly in tests.
        transport = FakeUploadTransport(FailureScript.demo())
        uploadEngine = UploadEngine(queueStore, transport, clock, captureStore)

        loadPlan(planJson)

        initialized = true
    }

    private fun loadPlan(planJson: String) {
        val result = PlanParser().parse(planJson)
        planDiagnostics = result.diagnostics
        // If the plan is invalid, a minimal default plan is used to avoid crashing/blocking the app.
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
