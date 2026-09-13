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
