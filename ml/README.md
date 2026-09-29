# `ml/` — SPIKE-3

Pipeline de scoring v1, de punta a punta: dataset sintético → regresión
logística (scikit-learn) → exportado a ONNX → ejecutado dentro de la JVM
con ONNX Runtime, sin un segundo proceso ni un segundo lenguaje en el
camino caliente (ADR-011 del informe de arquitectura).

## Estructura

```
ml/
├── data/
│   └── generator/
│       └── generate_synthetic.py   # dataset sintético, feature_set v1, semilla fija
├── training/
│   └── train.py                    # entrena, calcula gates, exporta ONNX y el golden set
├── model/
│   ├── scoring-v1.onnx              # artefacto (copiado a mano a backend/, ver abajo)
│   ├── golden_set.json              # 20 perfiles fijos para la prueba de paridad
│   └── model_card.json              # versión, hash del dataset, métricas, gates
└── requirements.txt
```

## Cómo reproducirlo

```bash
python3 -m venv ml/.venv
ml/.venv/bin/pip install -r ml/requirements.txt
ml/.venv/bin/python ml/training/train.py
```

La misma semilla (`SEED = 20260101` en `generate_synthetic.py`) siempre
produce el mismo dataset, byte a byte (gate de reproducibilidad, §11.5 del
informe) — `train.py` lo verifica entrenando dos veces y comparando los
coeficientes.

## Gates (bloquean el merge en `ml/`, según el informe §11.5)

| Gate | Umbral | Resultado de la última corrida |
|---|---|---|
| AUC | ≥ 0.70 | ~0.80 |
| KS | ≥ 0.30 | ~0.44 |
| Monotonía | signo esperado por variable | todas correctas |
| Equidad (regla de los 4/5) | ≥ 0.8 por género y por zona | ambas ≥ 0.97 |
| Reproducibilidad | mismos coeficientes con la misma semilla | sí |
| Paridad Python↔Java | ≤ 1e-6 sobre 20 perfiles fijos | máx. observado ~6e-8 |

El resultado completo y actualizado siempre está en `model/model_card.json`.

## Advertencia honesta

El dataset es **sintético** (RN-20: ningún dato corresponde a una persona
real). El AUC y el KS no representan poder predictivo real — demuestran
que el pipeline funciona, es reproducible y es explicable, no que el
modelo "sabe" predecir el riesgo de un titular real. Antes de decidir
sobre datos reales, hay que reentrenar y volver a validar todos los gates
(ver DEBT-005 en `docs/debt-register.md`).

## Cómo entra el modelo al backend (Sprint 1)

El adaptador Java (`com.doggycredit.scoring.internal.OnnxScoringAdapter`)
carga el artefacto desde `backend/src/main/resources/models/scoring-v1.onnx`.
**Ese archivo hoy se copia a mano** desde `ml/model/scoring-v1.onnx` después
de cada `train.py` — no hay todavía un paso de CI que lo regenere y lo
copie solo (ver DEBT-016).

```bash
cp ml/model/scoring-v1.onnx backend/src/main/resources/models/scoring-v1.onnx
cp ml/model/golden_set.json backend/src/test/resources/golden_set.json
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
