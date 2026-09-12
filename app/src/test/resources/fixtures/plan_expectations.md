# plan_expectations.md

Resultado esperado al parsear `plan_messy.json`. Este documento es el oráculo:
define el comportamiento correcto, y los tests deben coincidir con él.

## Resultado global

- El parseo es exitoso (fail-soft): el plan no es null.
- `plan_name` = "Plan de Pruebas Sucio FrameGate".
- `scale_factor` se conserva como string crudo `"1.234567890123456789"` (sin pasar por Double).
- `created_at` = "2026-09-10T12:00:00" (se conserva tal cual; solo se emite diagnóstico por falta de zona horaria).
- Claves desconocidas (`unknown_top_level_key`) se ignoran sin error.

## Pasos resultantes (2 utilizables)

| id | origen | notas |
|---|---|---|
| step-01 | primer paso | min_brightness `"60.0"` (string) → 60.0; max_motion null → default 15.0; hold_frames `"5"` (string) → 5 |
| step-03-null-thresholds | cuarto paso | thresholds null → todos por defecto (focus 10.0, brillo 50.0, motion 15.0) |

Pasos descartados:
- El segundo `step-01`: id duplicado → se descarta la repetición (se conserva el primero).
- `step-02-desconocido`: tipo `PASO_DESCONOCIDO_FUTURO` desconocido → se omite.

## Diagnósticos esperados (por código)

| code | severidad | motivo |
|---|---|---|
| TIMESTAMP_NO_TZ | WARNING | `created_at` sin zona horaria; se asume UTC |
| DUPLICATE_STEP_ID | WARNING | segundo `step-01` descartado |
| UNKNOWN_STEP_TYPE | WARNING | `step-02-desconocido` con tipo desconocido |
| MISSING_THRESHOLDS | WARNING | `step-03-null-thresholds` con thresholds null |

No hay diagnósticos de severidad ERROR (el plan es utilizable).

## Casos fail-hard (plan completo inválido, plan = null, con ERROR)

- JSON sintácticamente inválido → `INVALID_JSON`.
- Sin lista `steps` → `NO_STEPS`.
- `steps` presente pero ningún paso utilizable → `NO_USABLE_STEPS`.
