# metrics_reference.md

Especificación de las tres métricas por frame. Este documento es el oráculo:
los verdicts de `expected_verdicts.csv` se derivan de estas fórmulas, no de la
salida del código. La implementación (`MetricsAnalyzer`) debe coincidir con esto.

## Lectura del plano de luma

- Se lee solo `planes[0]` (luma Y). Cada byte se interpreta sin signo: `luma = byte AND 0xFF` (0..255).
- Índice de un píxel `(x, y)`: `index = y * rowStride + x * pixelStride`.
  - `rowStride` puede ser mayor que el ancho (padding de fila); no asumir `rowStride == width`.
  - `pixelStride` puede ser mayor que 1 (luma intercalada); no asumir píxeles contiguos.
- Todo el cálculo ocurre **solo dentro del ROI** (`bufferRect`), nunca sobre el frame completo.

## Submuestreo

- `SAMPLE_STEP = 2` en ambos ejes: se procesa 1 de cada 4 píxeles (pares en x y en y).
- El número de muestras `N` es la cantidad de píxeles visitados con ese paso dentro del ROI.

## Foco (energía de gradiente)

Por cada muestra con vecino válido:

```
vecino  = luma en (x + SAMPLE_STEP, y)      # a SAMPLE_STEP columnas a la derecha
gradiente = luma - vecino
sumaGradiente += gradiente * gradiente
```

- El vecino solo cuenta si `x + SAMPLE_STEP < ROI.right` y su índice cae dentro del buffer.
- Las muestras sin vecino válido (borde derecho del ROI) suman al conteo `N` pero no al gradiente.

```
focus = sumaGradiente / N
```

Un frame uniforme (todos los píxeles iguales) da `focus = 0`. Mayor gradiente = más nítido.

## Brillo (luma media + clipping)

```
meanLuma = sumaLuma / N
```

Clipping por muestra, con umbrales `CLIP_LOW = 16` y `CLIP_HIGH = 239`:

```
estaClippeado = (luma <= 16) OR (luma >= 239)
clippedFraction = nºClippeados / N          # rango 0..1
```

El verdict de brillo del gate exige ambas cosas: `meanLuma >= minBrightness` **y**
`clippedFraction <= maxClippedFraction` (default 0.5). Un frame con luma media suficiente
pero muchos píxeles quemados/aplastados no se considera un brillo usable.

## Movimiento (diferencia media absoluta)

Contra el mismo índice del frame anterior (ya submuestreado):

```
motion = ( Σ |luma_actual(index) - luma_previa(index)| ) / N
```

- Si no hay frame anterior, `motion = 0`.
- Dos frames idénticos dan `motion = 0`. Un desplazamiento uniforme de brillo `d` en todos los píxeles da `motion = d`.

## Frame vacío o parámetros inválidos

Si el buffer está vacío, `rowStride <= 0`, el ROI es degenerado o `pixelStride < 1`, todas las métricas son 0.

## Redondeo (solo para el manifest, no para el gate)

El manifest redondea `focus`, `meanLuma`, `clippedFraction` y `motion` a 3 decimales.
El gate evalúa con los valores sin redondear.

## Secuencia de 24 frames y expected_verdicts.csv

Los 24 frames son sintéticos y deterministas (64x64, rowStride 64, pixelStride 1),
construidos por regla para poder calcular las métricas a mano. El
`expected_verdicts.csv` deriva de estas fórmulas, no de la salida del código.

Thresholds del CSV: `focusRatio = 0.6`, `minBrightness = 50`, `maxMotion = 15`.
`focus_ok` usa el baseline acumulado (máximo de foco visto hasta ese frame),
igual que el gate. `motion` del frame 0 es 0 (no hay frame anterior).

Arquetipos (secuencia continua de captura), con su ROI:

| Frames | Arquetipo | Patrón | ROI |
|---|---|---|---|
| 0–5 | shaky/dark | luma baja (uniforme 20 / mitades 10-35 alternadas) | completo |
| 6–11 | transición | luma sube a 120, uniforme (sin nitidez) | completo |
| 12–17 | sharp/centred | franjas verticales 40/210 (bordes marcados) | completo |
| 18–23 | sharp-in-one-quadrant | franjas solo en el cuadrante superior-izquierdo, fondo plano 120 | (0,0,0.5,0.5) |

El bloque sharp-in-one-quadrant se mide con un ROI que cae sobre el cuadrante
nítido: foco alto (~27093). El mismo frame medido con ROI completo daría foco
bajo (~6873), porque el cuadrante nítido se diluye en el fondo plano. Esa
diferencia demuestra que la medición respeta el ROI y no el frame entero.
