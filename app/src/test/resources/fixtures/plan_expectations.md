# plan_expectations.md

Expected results when parsing `plan_messy.json`. This document serves as the oracle spec:
defines correct parser behavior, and unit tests must match it.

## Overall Result

- Parsing succeeds (fail-soft): plan is non-null.
- `plan_name` = "Plan de Pruebas Sucio FrameGate".
- `scale_factor` is preserved as a raw unquoted string `"1.234567890123456789"` (without passing through Double).
- `created_at` = "2026-09-10T12:00:00" (preserved as-is; only emits a warning diagnostic for missing timezone offset).
- Unknown top-level keys (`unknown_top_level_key`) are ignored without error.

## Resulting Steps (2 usable)

| id | origin | notes |
|---|---|---|
| step-01 | first step | focus_ratio (key `FOCUS_RATIO`, mixed casing) `"0.7"` (string) → 0.7; min_brightness `"60.0"` (string) → 60.0; max_motion null → default 15.0; hold_frames `"5"` (string) → 5 |
| step-03-null-thresholds | fourth step | thresholds null → all defaults (focus_ratio 0.6, brightness 50.0, motion 15.0); roi (0.1, 0.1, 0.4, 0.4) |

Discarded steps:
- Second `step-01`: duplicate ID → duplicate is discarded (first occurrence kept).
- `step-02-desconocido`: unknown type `PASO_DESCONOCIDO_FUTURO` → step omitted.

## Expected Diagnostics (by code)

| code | severity | reason |
|---|---|---|
| TIMESTAMP_NO_TZ | WARNING | `created_at` lacks timezone offset; assumes UTC |
| DUPLICATE_STEP_ID | WARNING | second `step-01` discarded |
| UNKNOWN_STEP_TYPE | WARNING | `step-02-desconocido` has unknown type |
| MISSING_THRESHOLDS | WARNING | `step-03-null-thresholds` has null thresholds |

No ERROR severity diagnostics are emitted (plan remains usable).

## Fail-Hard Cases (entire plan invalid, plan = null, with ERROR)

- Syntactically invalid JSON → `INVALID_JSON`.
- Missing `steps` array → `NO_STEPS`.
- `steps` present but no usable steps → `NO_USABLE_STEPS`.
