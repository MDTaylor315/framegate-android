# FrameGate

A two-screen Android application (Kotlin + Jetpack Compose) built around a frame capture loop: it measures each incoming camera/fixture frame within a region of interest (ROI) defined by a plan, triggers the shutter when metrics remain stable for N consecutive frames, and safely exits each capture from the device exactly once using an crash-resilient persistent queue. Runs end-to-end on a stock emulator using embedded fixtures: no physical camera or real remote server required.

## How to Run

Requirements: JDK 17+, Android SDK (compileSdk 36, minSdk 26).

```bash
./gradlew installDebug      # Installs on a connected emulator or physical device
./gradlew test              # Runs the full unit test suite (pure JUnit, no device required)
./gradlew testDebugUnitTest # Runs the same unit test suite for the debug variant
./gradlew detekt            # Executes static analysis checks
./gradlew assembleDebug     # Generates the debug APK
```

The app boots directly onto the fixture frame source (the 24 assessment frames) without requiring any code edits.

No containers or servers are required: uploads are evaluated against an in-process fake transport (`FakeUploadTransport`), which is enabled by default. Docker containers / external mock servers for end-to-end network transmission were treated as stretch items and omitted (see "Sacrifices and Trade-offs").

### The Two Frame Sources

Both frame sources implement the `FrameSource` interface (`getNextFrame(): FrameData?`):

- **`ReplayFrameSource.assessment()`** — the primary evaluation pipeline, active by default in `CaptureViewModel`. Replays 24 deterministic synthetic frames representing a capture lifecycle: dark/shaky → transition → sharp/centered → sharp in one quadrant. Expected verdicts for these 24 frames are stored in `expected_verdicts.csv` and validated in `ExpectedVerdictsTest`.
- **`ReplayFrameSource.uniform()`** — a minimal frame source yielding a single uniform frame, useful for quick unit testing.

To switch sources, pass the desired instance when constructing `CaptureViewModel` (default is `assessment()`). There is no UI selector: the plan and frame source are fixed in code per specification guidelines (no plan picker UI required).

## Architecture

- **Pure Domain Layer** (`domain/`): Plan parser, gate reducer, coordinate mapper, metrics analyzer, queue store, and manifest builder. Free of Android framework dependencies; fully testable via JUnit without requiring a device or real system clock.
- **ViewModels**: Screen state is exposed via `StateFlow` derived using `combine(...).stateIn(...)`. Features a single `onEvent` handler per screen, and streams one-shot UI events using `Channel` (decoupled from persistent state).
- **Manual Dependency Injection**: Centralized in `di/AppGraph.kt` (no Hilt/Koin/Dagger).
- **Single Activity**: Screen navigation managed via an enum-based state switch between Capture and Queue.
- **Minimal Dependencies**: No RxJava/LiveData, no WorkManager; JSON serialization handled strictly with `kotlinx.serialization`.

### Non-Trivial Design Decisions

- **Focus Evaluated as Ratio Against Auto-Calibrated Baseline**: Gradient energy does not have a fixed scale across arbitrary scenes, making absolute thresholding impractical. The gate requires focus to reach at least `focusRatio` (default 0.6) of the peak focus observed in the sequence. The first frame serves as its own baseline, preventing initial false blocks. The absolute `min_focus` field was eliminated as dead code.
- **Brightness Evaluation with Clipping**: Brightness verdicts require sufficient average luma while ensuring the fraction of clipped/blown-out or crushed pixels does not exceed `maxClippedFraction` (default 0.5).
- **One-Shot UI Effects with `Channel.RENDEZVOUS` + `trySend`**: Effects emitted without an active UI collector (e.g. during Activity rotation recreation) are dropped rather than buffered for re-emission. Paired with `rememberSaveable` for screen navigation, screen rotation does not reset UI state or repeat toast notifications.
- **Durability via Append-Only Journal**: Every queue record state change appends a JSON line to disk. Records are written before initiating network calls; if the process dies mid-upload, restarting demotes `UPLOADING` items to `PENDING` for retry. The `idempotencyKey` remains stable across the item's lifetime, allowing server-side deduplication (409 = success) to enforce exactly-once delivery. The record ID uses a unique UUID, preventing app restarts from overwriting prior capture records.
- **Exponential Backoff with Jitter**: Includes capped delays and attempt limits, with an injectable `Clock` for deterministic testing. 201/409 = success; 400/422 = terminal client error with no automatic retries (manual retries available in Queue screen); 500/503/timeout = transient error with backoff.
- **Plan `scale_factor` Preservation**: Scale factor is preserved as a raw unquoted JSON number (`JsonUnquotedLiteral`), avoiding `Double` parsing to prevent precision loss.
- **Coordinate Mapping**: Pure functions (`CoordinateMapper` + `RoiTransform`) handle explicit rotation and horizontal mirroring without magic offsets. The Capture screen features a button to cycle all 4 camera configurations (rotation/mirroring) to visually verify ROI alignment.

## Fixtures as Test Oracles

Pure domain unit tests execute against fixtures in `app/src/test/resources/fixtures`:

- `plan_messy.json` / `plan_clean.json` + `plan_expectations.md` — defensive plan parsing validation.
- `roi_mapping_cases.json` — golden coordinate reference within 0.5 px across 4 camera configurations.
- `gate_cases.json` — gate step-by-step state transition sequences (including jitter handling).
- `expected_verdicts.csv` + `metrics_reference.md` — 24 assessment frames and their expected verdicts, derived from specs rather than code output.
- `manifest.schema.json` + `manifest_golden.json` — byte-for-byte exact equality validation for upload manifests.

## Sacrifices and Trade-Offs

- **Real CameraX Integration**: Omitted in favor of fixture sources. `MetricsAnalyzer` consumes a luma buffer with stride metadata; a CameraX `FrameSource` would extract `planes[0]` from `ImageProxy`, pass it to the analyzer, and close the `ImageProxy` in a `try/finally`. Domain logic remains untouched.
- **Dockerfile / External Mock Server**: End-to-end network transmission relies on `FakeUploadTransport` in-process simulation.
- **Burst Steps & Multi-Sensor Fusion**: Multi-frame burst captures and sensor fusion for motion detection were excluded to maintain focus on core scope requirements.

## Known Limitations & Verification Notes

- **Synthetic Frames**: Frames are synthetic (`ReplayFrameSource`). App UI flows were verified on emulator and physical hardware (Pixel 9a), but live camera hardware capture streams were not wired.
- **Uncompressed Binary Artifacts**: Capture artifacts store raw luma byte buffers directly without JPEG compression encoding.
- **Memory & Resource Cleanup**: No unclosed resources remain on hot-paths; byte buffers are managed by garbage collection, and file assets are safely managed with `use`.
- **Runtime Demo Script**: To demonstrate varied states on the Queue screen, runtime transport uses a demo `FailureScript` (1st upload recovers via backoff; 2nd upload encounters terminal 422 error requiring manual retry).
- **Process Death Resilience**: Verified via JUnit tests (`UploadEngineTest`) and manual verification on physical device (killing app during upload gracefully recovers pending state on restart).
