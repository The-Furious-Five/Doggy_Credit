# `ml/` — SPIKE-3 / D-01

Pipeline de scoring (hoy `scoring-v2`), de punta a punta: dataset sintético → regresión
logística (scikit-learn) → exportado a ONNX → ejecutado dentro de la JVM
con ONNX Runtime, sin un segundo proceso ni un segundo lenguaje en el
camino caliente (ADR-011 del informe de arquitectura).

## Estructura

```
ml/
├── data/
│   └── generator/
│       └── generate_synthetic.py   # dataset sintético, feature_set v2, semilla fija
├── training/
│   └── train.py                    # entrena, calcula gates, exporta ONNX y el golden set
├── model/
│   ├── scoring-v2.onnx              # artefacto (copiado a mano a backend/, ver abajo)
│   ├── golden_set.json              # 20 perfiles fijos para la prueba de paridad
│   └── model_card.json              # versión, hash del dataset, métricas, gates
└── requirements.txt
```

## feature_set v2 (D-01, Sprint 2)

Las 10 variables del v1 (buró, ingresos, billetera a 90 días, PYME) más 5
que salen de las fuentes nuevas del Perfil 360:

| Variable | Fuente en el Perfil 360 | Signo esperado |
|---|---|---|
| `saldo_promedio_cuentas_3m_bob` | `cuentas[].saldoPromedio3m` (banco; 0 sin cuenta) | baja el riesgo |
| `ratio_egreso_ingreso_6m` | `flujos.egresosMensuales / ingresosMensuales` | sube el riesgo |
| `meses_flujo_negativo_6m` | meses con egresos > ingresos en `flujos` | sube el riesgo |
| `saldo_promedio_wallet_bob` | `wallet.saldoPromedio` | baja el riesgo |
| `antiguedad_wallet_meses` | `wallet.antiguedadMeses` | baja el riesgo |

Se generan después de las del v1, así que con la misma semilla las columnas
del v1 no cambian. El orden exacto que espera el modelo es `featureOrder`
de `model/model_card.json`.

## Cómo reproducirlo

```bash
python3 -m venv ml/.venv
ml/.venv/bin/pip install -r ml/requirements.txt
ml/.venv/bin/python ml/training/train.py
```

La misma semilla (`SEED = 20260101` en `generate_synthetic.py`) siempre
produce el mismo dataset, byte a byte (gate de reproducibilidad, §11.5 del
informe) — `train.py` lo verifica entrenando dos veces y comparando los
coeficientes. El `.onnx` también sale idéntico byte a byte entre corridas
(`train.py` fija el nombre del grafo, que `skl2onnx` genera al azar).

## Gates (bloquean el merge en `ml/`, según el informe §11.5)

| Gate | Umbral | Resultado de la última corrida |
|---|---|---|
| AUC | ≥ 0.70 | ~0.83 (v1: ~0.80) |
| KS | ≥ 0.30 | ~0.54 (v1: ~0.44) |
| Monotonía | signo esperado por variable | las 13 con signo esperado, correctas |
| Equidad (regla de los 4/5) | ≥ 0.8 por género y por zona | género ~1.00, zona ~0.94 |
| Reproducibilidad | mismos coeficientes con la misma semilla | sí |
| Paridad Python↔Java | ≤ 1e-6 sobre 20 perfiles fijos | 20/20 |

El resultado completo y actualizado siempre está en `model/model_card.json`.

## Advertencia honesta

El dataset es **sintético** (RN-20: ningún dato corresponde a una persona
real). El AUC y el KS no representan poder predictivo real — demuestran
que el pipeline funciona, es reproducible y es explicable, no que el
modelo "sabe" predecir el riesgo de un titular real. Antes de decidir
sobre datos reales, hay que reentrenar y volver a validar todos los gates
(ver DEBT-005 en `docs/debt-register.md`).

## Cómo entra el modelo al backend

El adaptador Java (`com.doggycredit.scoring.internal.OnnxScoringAdapter`)
carga el artefacto desde `backend/src/main/resources/models/scoring-<versión>.onnx`
(`new OnnxScoringAdapter("v2")`).
Ese archivo se copia a mano desde `ml/model/scoring-v2.onnx` después de cada
`train.py`, pero el CI lo vigila (job `ml` de `ci-cd.yml`, D-07): reentrena
desde cero y falla si `ml/model/` no queda igual o si el `.onnx` y el golden
set de `backend/` no tienen exactamente los mismos bytes
(`ci/check_ml_artifacts.py`). Para eso el entrenamiento es determinista en
cualquier plataforma: ajusta en float64 con Newton y redondea los parámetros
a `PARAM_DECIMALS` antes de exportar.

```bash
cp ml/model/scoring-v2.onnx backend/src/main/resources/models/scoring-v2.onnx
cp ml/model/golden_set.json backend/src/test/resources/golden_set.json
python3 ci/check_ml_artifacts.py   # lo mismo que verifica el CI
```

La prueba de paridad vive en
`backend/src/test/java/com/doggycredit/scoring/OnnxParityTest.java`.

## Plan B (si ONNX Runtime da problemas en algún entorno)

Guardar los coeficientes de `model_card.json` (`coeficientesEstandarizados`,
más la media y desviación del `StandardScaler`, que `train.py` puede
exportar por separado si hace falta) y evaluarlos directamente en Java, sin
ONNX Runtime — se pierde la vitrina del modelo exportado, no la
funcionalidad. No fue necesario en este spike: la ruta ONNX funcionó a la
primera.
