# FrameGate

App Android (Kotlin + Jetpack Compose) de dos pantallas cuyo núcleo es un loop de
captura: mide cada frame dentro de una región definida por un plan, dispara el
obturador cuando las mediciones se mantienen estables N frames, y saca cada captura
del dispositivo exactamente una vez a través de una cola que sobrevive a que la maten.
Corre de punta a punta en un emulador stock usando los fixtures embebidos: sin cámara
ni servidor reales.

## Cómo correr

Requisitos: JDK 17+, Android SDK (compileSdk 36, minSdk 26).

```
./gradlew installDebug      # instala en un emulador/dispositivo conectado
./gradlew test              # corre la suite completa (JUnit puro, sin dispositivo)
./gradlew testDebugUnitTest # misma suite, solo la variante debug
./gradlew detekt            # análisis estático
./gradlew assembleDebug     # genera el APK
```

La app arranca sobre la fixture frame source (los 24 frames del assessment), sin
editar código.

No hay container ni servidor: la subida se evalúa contra el fake transport in-process
(`FakeUploadTransport`), que es el default. El Dockerfile / mock server para una subida
real end-to-end es stretch y no se incluyó (ver "Qué se sacrificó").

### Las dos frame sources

Ambas viven detrás de la interfaz `FrameSource` (`getNextFrame(): FrameData?`):

- **`ReplayFrameSource.assessment()`** — la ruta de revisión principal, activa por
  defecto en `CaptureViewModel`. Reproduce 24 frames sintéticos deterministas que
  cuentan una historia de captura: oscuro/movido → transición → nítido/centrado →
  nítido en un cuadrante. Los verdicts esperados de estos 24 frames están en
  `expected_verdicts.csv` y se validan en `ExpectedVerdictsTest`.
- **`ReplayFrameSource.uniform()`** — una fuente mínima de un solo frame uniforme,
  útil para pruebas simples.

Para cambiar de fuente, se pasa la instancia al construir `CaptureViewModel` (el
default es `assessment()`). No hay selector en la UI: el plan y la fuente se fijan
en código, como pide el enunciado (sin plan picker).

## Arquitectura

- **Dominio puro** (`domain/`): parser, gate, mapper de coordenadas, analyzer de
  métricas, cola y manifest. Sin dependencias de Android; todo alcanzable desde
  JUnit sin dispositivo ni reloj real.
- **ViewModels** con un `StateFlow` por pantalla derivado con
  `combine(...).stateIn(...)`, un único `onEvent` por pantalla, y efectos one-shot
  por `Channel` (fuera del estado).
- **DI a mano** en `di/AppGraph.kt` (sin Hilt/Koin/Dagger).
- Una sola `Activity`; navegación por un enum switch entre Capture y Queue.
- Sin RxJava/LiveData, sin WorkManager, JSON solo con `kotlinx.serialization`.

### Decisiones de diseño no triviales

- **Foco como ratio contra un baseline auto-calibrado.** La energía de gradiente no
  tiene escala fija (depende de la escena), así que un umbral absoluto no generaliza.
  El gate exige que el foco sea al menos `focusRatio` (default 0.6) del pico visto en
  la secuencia. El primer frame es su propio baseline, así que nunca se bloquea por
  foco. El campo `min_focus` absoluto se eliminó por ser código muerto.
- **Brillo con clipping.** El verdict de brillo exige luma media suficiente y que la
  fracción de píxeles quemados/aplastados no supere `maxClippedFraction` (default 0.5).
- **Efectos one-shot con `Channel.RENDEZVOUS` + `trySend`.** Un efecto emitido sin
  colector activo (p. ej. durante la recreación por rotación) se descarta en vez de
  bufferizarse y reemitirse. Con `rememberSaveable` para la pantalla activa, la
  rotación no reinicia el estado ni repite toasts.
- **Durabilidad: journal append-only.** Cada cambio de estado del registro es una
  línea JSON. El registro se escribe antes de la llamada de red; si el proceso muere
  a mitad de una subida, al relanzar el item `UPLOADING` se degrada a `PENDING` y se
  reintenta. La `idempotencyKey` es estable durante toda la vida del registro, así el
  servidor deduplica (409 = éxito) y se sube exactamente una vez. El id del registro
  es un UUID único, para que reiniciar el loop o relanzar la app nunca sobrescriba
  capturas.
- **Backoff exponencial con jitter**, delay e intentos capados, con `Clock` inyectable
  para testear sin esperas reales. 201/409 = éxito; 400/422 = terminal sin reintento
  automático (solo el botón manual de la Queue puede reintentar); 500/503/timeout =
  reintento con backoff.
- **`scale_factor` del plan** se preserva como número crudo (`JsonUnquotedLiteral`),
  sin pasar por `Double`, para no perder dígitos.
- **Mapeo de coordenadas** como función pura (`CoordinateMapper` + `RoiTransform`),
  con rotación y espejo explícitos, sin offsets mágicos. La pantalla Capture tiene un
  botón que cicla las 4 configuraciones de cámara para verificar que el overlay del
  ROI cae bien en cada rotación/espejo.

## Fixtures como oráculo

Los tests de lógica pura se corren desde fixtures en `app/src/test/resources/fixtures`,
no desde casos retipeados en Kotlin:

- `plan_messy.json` / `plan_clean.json` + `plan_expectations.md` — parseo defensivo.
- `roi_mapping_cases.json` — coordenadas golden a 0.5 px, 4 configuraciones.
- `gate_cases.json` — secuencias del gate (incluida una jitter).
- `expected_verdicts.csv` + `metrics_reference.md` — los 24 frames y sus verdicts,
  derivados a mano de la fórmula documentada (con aritmética de precisión simple,
  igual que el código), no de la salida del código.
- `manifest.schema.json` + `manifest_golden.json` — igualdad byte a byte del manifest.

## Qué se sacrificó y por qué

Todo esto son stretch goals explícitamente no puntuables según el enunciado; se
priorizó blindar el scope obligatorio:

- **CameraX real** no se cableó. El `MetricsAnalyzer` recibe un buffer de luma con su
  stride, así que un `FrameSource` de CameraX solo tendría que extraer `planes[0]` del
  `ImageProxy`, pasarlo al mismo analyzer, y cerrar el `ImageProxy` en un `try/finally`.
  No cambia el dominio. No se implementó porque no puntúa y el revisor no lo puede
  ejecutar de forma fiable.
- **Dockerfile / mock server** para una subida real end-to-end no se incluyó. El
  retry/backoff se evalúa contra el fake transport in-process, que sigue siendo el
  default.
- Segundo tipo de step (burst) y fusión de sensores en el movimiento: no implementados.

## Qué no se pudo verificar / limitaciones conocidas

- **Sin cámara real.** Los frames son sintéticos (`ReplayFrameSource`), no de cámara.
  La app se ejecutó sobre la fixture source (en emulador y en un dispositivo físico
  Pixel 9a), pero el path de CameraX no se cableó y por tanto nunca se corrió.
- **El "JPEG" es el buffer del frame**, no un JPEG codificado. Se persiste el buffer de
  luma tal cual como artefacto; no hay codificación de imagen.
- **`ImageProxy.close()` no aplica** en esta implementación: no hay `ImageProxy` (los
  frames son `ByteArray`). Revisado el hot path, no hay recursos sin cerrar; los buffers
  los gestiona el GC y el único stream (el asset del plan) se cierra con `use`.
- **Buffers no preasignados.** El analyzer no reusa un pool de buffers porque no asigna
  arrays por frame (opera sobre el buffer entrante); los únicos objetos por frame son
  acumuladores triviales. Con CameraX real convendría un pool.
- **Guion de fallos de demostración en runtime.** Para que la pantalla Queue muestre
  estados variados, el transporte en la app usa un `FailureScript` de demo (la primera
  captura de cada corrida se recupera tras backoff; la segunda queda en `Failed` con
  reintento manual). El grading real del retry/backoff es por los tests + el log del
  transporte, no por la UI.
- **Muerte del proceso:** la recuperación está probada por JUnit (`UploadEngineTest`) y,
  además, verificada a mano en el Pixel 9a: matar la app a mitad de una subida y
  relanzar deja el registro en pendiente, reintenta y termina; nada se pierde ni se
  duplica.
