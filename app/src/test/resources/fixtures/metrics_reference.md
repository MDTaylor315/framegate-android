# metrics_reference.md

Per-frame 3-metrics specification. This document serves as the oracle spec:
verdicts in `expected_verdicts.csv` are derived from these formulas, not code outputs. The implementation (`MetricsAnalyzer`) must match this specification.

## Luma Plane Reading

- Reads strictly `planes[0]` (luma Y). Each byte is interpreted as unsigned: `luma = byte AND 0xFF` (0..255).
- Pixel index `(x, y)`: `index = y * rowStride + x * pixelStride`.
  - `rowStride` may exceed frame width (row padding); do not assume `rowStride == width`.
  - `pixelStride` may exceed 1 (interleaved luma); do not assume contiguous pixels.
- All metric calculations occur **strictly within the ROI** (`bufferRect`), never over the full frame.

## Sub-sampling

- `SAMPLE_STEP = 2` on both axes: processes 1 out of 4 pixels (even indices in x and y).
- Sample count `N` is the total number of pixels visited with this step inside the ROI.

## Focus (Gradient Energy)

For each sample with a valid neighbor:

```
neighbour  = luma at (x + SAMPLE_STEP, y)      # SAMPLE_STEP columns to the right
gradient  = luma - neighbour
sumGradient += gradient * gradient
```

- A neighbor is valid only if `x + SAMPLE_STEP < ROI.right` and its byte index is within buffer bounds.
- Samples without valid neighbors (right edge of ROI) increment count `N` but do not contribute to gradient sum.

```
focus = sumGradient / N
```

A uniform frame (identical pixel values) yields `focus = 0`. Higher gradient energy indicates a sharper image.

## Brightness (Mean Luma + Clipping)

```
meanLuma = sumLuma / N
```

Sample clipping detection using thresholds `CLIP_LOW = 16` and `CLIP_HIGH = 239`:

```
isClipped = (luma <= 16) OR (luma >= 239)
clippedFraction = countClipped / N          # range 0..1
```

The gate brightness verdict requires both conditions: `meanLuma >= minBrightness` **and** `clippedFraction <= maxClippedFraction` (default 0.5). A frame with adequate mean luma but excessive clipped/blown-out or crushed pixels is not considered usable brightness.

## Motion (Mean Absolute Difference)

Compared against the exact same index from the previous frame (already sub-sampled):

```
motion = ( Σ |current_luma(index) - previous_luma(index)| ) / N
```

- If no previous frame exists, `motion = 0`.
- Two identical frames yield `motion = 0`. A uniform brightness shift `d` across all pixels yields `motion = d`.

## Empty Buffer or Invalid Parameters

If buffer is empty, `rowStride <= 0`, ROI is degenerate, or `pixelStride < 1`, all metrics return 0.

## Rounding (Manifest Only, Not Gate Evaluation)

The manifest rounds `focus`, `meanLuma`, `clippedFraction`, and `motion` to 3 decimal places.
The gate evaluates unrounded floating point values.

## 24-Frame Sequence and expected_verdicts.csv

The 24 evaluation frames are synthetic and deterministic (64x64, rowStride 64, pixelStride 1), constructed by rule for manual formula calculation. `expected_verdicts.csv` derives from these formulas, not code output.

CSV Thresholds: `focusRatio = 0.6`, `minBrightness = 50`, `maxMotion = 15`.
`focus_ok` evaluates against accumulated baseline (peak focus seen up to that frame), matching gate logic. `motion` for frame 0 is 0 (no previous frame).

Archetypes (continuous capture sequence), with corresponding ROI:

| Frames | Archetype | Pattern | ROI |
|---|---|---|---|
| 0–5 | shaky/dark | low luma (uniform 20 / alternating halves 10-35) | full |
| 6–11 | transition | luma rises to 120, uniform (no sharpness) | full |
| 12–17 | sharp/centred | vertical stripes 40/210 (sharp edges) | full |
| 18–23 | sharp-in-one-quadrant | stripes only in top-left quadrant, flat background 120 | (0,0,0.5,0.5) |

The sharp-in-one-quadrant block is measured with an ROI targeted over the sharp quadrant: high focus (~27093). The same frame measured with a full ROI yields low focus (~6873), as the sharp quadrant is diluted by the flat background. This difference verifies that metric evaluation strictly respects the ROI rather than the entire frame.
