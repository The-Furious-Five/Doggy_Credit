"""
Entrenamiento del modelo de scoring (SPIKE-3; v2 en D-01, Sprint 2).

Pipeline: dataset sintético (feature_set v2) -> StandardScaler + regresión
logística (scikit-learn) -> exportado a ONNX -> golden set para la prueba
de paridad Python <-> Java (backend/.../scoring, ONNX Runtime).

Uso:
    ml/.venv/bin/python ml/training/train.py

Escribe en ml/model/:
    scoring-v2.onnx     — el artefacto servido por la JVM
    scoring-v2.params.json — media, escala y coeficientes por variable (imputación y explicabilidad)
    model_card.json     — versión, hash del dataset, métricas, gates
    golden_set.json     — 20 perfiles fijos con la probabilidad esperada
"""

from __future__ import annotations

import json
import sys
from datetime import datetime, timezone
from pathlib import Path

import numpy as np
import onnx
import sklearn
import skl2onnx
from skl2onnx import convert_sklearn
from skl2onnx.common.data_types import FloatTensorType
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import roc_auc_score, brier_score_loss
from sklearn.model_selection import train_test_split
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "data" / "generator"))
from generate_synthetic import (  # noqa: E402
    FEATURE_COLUMNS,
    FEATURE_SET_VERSION,
    SEED,
    dataset_hash,
    generate,
)

MODEL_DIR = Path(__file__).resolve().parents[1] / "model"
MODEL_VERSION = "v2"

# Decimales con los que se guardan los parámetros del modelo. Cada plataforma
# acumula en su propio BLAS y los coeficientes difieren en los últimos bits
# entre macOS y Linux; ajustando en float64 con Newton (converge al óptimo
# hasta ~1e-12) y redondeando, el .onnx sale idéntico byte a byte en ambas,
# que es lo que el CI compara por hash (DEBT-016).
PARAM_DECIMALS = 6

# Signo esperado del coeficiente en el espacio estandarizado (monotonía,
# §11.5 del informe): + significa "sube el riesgo", - "lo baja". Se
# verifica contra el modelo entrenado, no se le impone (para eso hace
# falta un modelo con restricciones, que es el v2/LightGBM del roadmap).
EXPECTED_SIGN = {
    "dti": 1,
    "mora_max_12m_dias": 1,
    "num_acreedores": 1,
    "peor_calificacion_24m": 1,
    "antiguedad_sistema_financiero_meses": -1,
    "ingreso_promedio_6m_bob": -1,
    "ingreso_volatilidad_6m": 1,
    "actividad_wallet_90d": -1,
    "saldo_promedio_cuentas_3m_bob": -1,
    "ratio_egreso_ingreso_6m": 1,
    "meses_flujo_negativo_6m": 1,
    "saldo_promedio_wallet_bob": -1,
    "antiguedad_wallet_meses": -1,
}


def four_fifths_ratio(p_default: np.ndarray, group: np.ndarray, threshold: float = 0.5) -> float:
    """Razón de impacto dispar (regla de los cuatro quintos): tasa de
    aprobación (p_default < threshold) del grupo con peor tasa sobre el
    grupo con mejor tasa. >= 0.8 se considera aceptable."""
    approved = p_default < threshold
    rates = []
    for g in np.unique(group):
        mask = group == g
        rates.append(approved[mask].mean())
    return float(min(rates) / max(rates)) if max(rates) > 0 else float("nan")


def ks_statistic(y_true: np.ndarray, p_default: np.ndarray) -> float:
    """Estadístico de Kolmogorov-Smirnov entre las distribuciones de score
    de buenos (y=0) y malos (y=1)."""
    order = np.argsort(p_default)
    y_sorted = y_true[order]
    cum_bad = np.cumsum(y_sorted) / y_sorted.sum()
    cum_good = np.cumsum(1 - y_sorted) / (1 - y_sorted).sum()
    return float(np.max(np.abs(cum_bad - cum_good)))


def _as_model_input(df):
    """Valores representables en float32 (lo que recibe el .onnx en la JVM),
    pero en float64 para que scikit-learn ajuste en doble precisión: en
    float32 la convergencia se queda en ~1e-7 y depende del BLAS."""
    return df[FEATURE_COLUMNS].astype(np.float32).astype(np.float64)


def _round_params(pipeline: Pipeline) -> None:
    scaler = pipeline.named_steps["scaler"]
    logreg = pipeline.named_steps["logreg"]
    scaler.mean_ = np.round(scaler.mean_, PARAM_DECIMALS)
    scaler.scale_ = np.round(scaler.scale_, PARAM_DECIMALS)
    logreg.coef_ = np.round(logreg.coef_, PARAM_DECIMALS)
    logreg.intercept_ = np.round(logreg.intercept_, PARAM_DECIMALS)


def train_once(seed: int = SEED) -> tuple[Pipeline, dict]:
    df = generate(seed=seed)
    X = _as_model_input(df)
    y = df["default"].to_numpy()
    protected = df[["genero_declarado", "zona"]]

    X_train, X_test, y_train, y_test, prot_train, prot_test = train_test_split(
        X, y, protected, test_size=0.2, random_state=seed, stratify=y
    )

    pipeline = Pipeline(
        steps=[
            ("scaler", StandardScaler()),
            ("logreg", LogisticRegression(
                C=1.0, solver="newton-cholesky", tol=1e-8, max_iter=1000, random_state=seed
            )),
        ]
    )
    pipeline.fit(X_train, y_train)
    _round_params(pipeline)

    p_test = pipeline.predict_proba(X_test)[:, 1]
    auc = roc_auc_score(y_test, p_test)
    ks = ks_statistic(y_test, p_test)
    brier = brier_score_loss(y_test, p_test)
    dir_genero = four_fifths_ratio(p_test, prot_test["genero_declarado"].to_numpy())
    dir_zona = four_fifths_ratio(p_test, prot_test["zona"].to_numpy())

    coef = dict(zip(FEATURE_COLUMNS, pipeline.named_steps["logreg"].coef_[0].tolist()))

    metrics = {
        "auc": auc,
        "ks": ks,
        "brier": brier,
        "dir_genero_declarado": dir_genero,
        "dir_zona": dir_zona,
        "n_train": len(X_train),
        "n_test": len(X_test),
        "tasa_default_train": float(y_train.mean()),
        "coeficientes_estandarizados": coef,
    }
    return pipeline, metrics


def check_gates(metrics: dict) -> dict:
    """§11.5 del informe. No detiene el entrenamiento: registra el
    resultado en model_card.json para que el CI decida (FF-ML)."""
    gates = {
        "auc_>=_0.70": metrics["auc"] >= 0.70,
        "ks_>=_0.30": metrics["ks"] >= 0.30,
        "dir_genero_>=_0.8": metrics["dir_genero_declarado"] >= 0.8,
        "dir_zona_>=_0.8": metrics["dir_zona"] >= 0.8,
    }
    coef = metrics["coeficientes_estandarizados"]
    for feature, expected_sign in EXPECTED_SIGN.items():
        actual_sign = 1 if coef[feature] >= 0 else -1
        gates[f"monotonia_{feature}"] = actual_sign == expected_sign
    return gates


def export_onnx(pipeline: Pipeline, path: Path) -> None:
    initial_type = [("input", FloatTensorType([None, len(FEATURE_COLUMNS)]))]
    onx = convert_sklearn(
        pipeline,
        initial_types=initial_type,
        options={id(pipeline): {"zipmap": False}},
        target_opset=18,
    )
    # skl2onnx nombra el grafo con un UUID aleatorio: se fija para que el
    # artefacto sea idéntico byte a byte en cada corrida y el CI pueda
    # compararlo por hash con el de backend/ (DEBT-016).
    onx.graph.name = f"scoring-{MODEL_VERSION}"
    path.write_bytes(onx.SerializeToString())


def export_params(pipeline: Pipeline) -> dict:
    """Parámetros del modelo para la JVM (D-02/D-05): media y escala para
    imputar de forma neutra una variable que falta (z = 0) y coeficientes
    estandarizados para explicar el score (c_i = beta_i * z_i). Son los mismos
    valores redondeados que van dentro del .onnx, así que salen idénticos en
    cualquier plataforma y el CI los compara por hash con los de backend/."""
    scaler = pipeline.named_steps["scaler"]
    logreg = pipeline.named_steps["logreg"]
    return {
        "modelVersion": MODEL_VERSION,
        "featureSetVersion": FEATURE_SET_VERSION,
        "features": [
            {"name": name, "mean": float(mean), "scale": float(scale), "coefficient": float(coef)}
            for name, mean, scale, coef in zip(
                FEATURE_COLUMNS, scaler.mean_, scaler.scale_, logreg.coef_[0]
            )
        ],
        "intercept": float(logreg.intercept_[0]),
    }


def build_golden_set(pipeline: Pipeline, seed: int, n: int = 20) -> list[dict]:
    """20 perfiles fijos (independientes del split de train/test) con la
    probabilidad que predice el pipeline de scikit-learn. El test de
    paridad en Java compara esto contra el .onnx corrido con ONNX Runtime."""
    df = generate(seed=seed + 999, n=n)
    X = _as_model_input(df)
    proba = pipeline.predict_proba(X)[:, 1]

    golden = []
    for i in range(n):
        golden.append(
            {
                "id": i,
                "features": {col: float(X.iloc[i][col]) for col in FEATURE_COLUMNS},
                "featuresOrdered": [float(v) for v in X.iloc[i].tolist()],
                # predict_proba pasa por el BLAS: se redondea para que el
                # golden set también salga idéntico en cualquier plataforma
                # (la tolerancia de paridad es 1e-6).
                "expectedPd": round(float(proba[i]), 12),
            }
        )
    return golden


def main() -> None:
    MODEL_DIR.mkdir(parents=True, exist_ok=True)

    df_for_hash = generate(seed=SEED)
    h = dataset_hash(df_for_hash)

    pipeline, metrics = train_once(seed=SEED)
    gates = check_gates(metrics)

    # gate de reproducibilidad: entrenar de nuevo con la misma semilla debe
    # dar exactamente los mismos coeficientes.
    pipeline_repeat, metrics_repeat = train_once(seed=SEED)
    coef_a = np.array(list(metrics["coeficientes_estandarizados"].values()))
    coef_b = np.array(list(metrics_repeat["coeficientes_estandarizados"].values()))
    gates["reproducibilidad_coeficientes_identicos"] = bool(np.allclose(coef_a, coef_b, atol=0))

    onnx_path = MODEL_DIR / f"scoring-{MODEL_VERSION}.onnx"
    export_onnx(pipeline, onnx_path)

    golden = build_golden_set(pipeline, seed=SEED)
    golden_path = MODEL_DIR / "golden_set.json"
    golden_path.write_text(json.dumps(golden, indent=2, ensure_ascii=False), encoding="utf-8")

    params_path = MODEL_DIR / f"scoring-{MODEL_VERSION}.params.json"
    params_path.write_text(json.dumps(export_params(pipeline), indent=2, ensure_ascii=False) + "\n",
                           encoding="utf-8")

    model_card = {
        "modelVersion": MODEL_VERSION,
        "featureSetVersion": FEATURE_SET_VERSION,
        "featureOrder": FEATURE_COLUMNS,
        "trainedAt": datetime.now(timezone.utc).isoformat(),
        "datasetSeed": SEED,
        "datasetHashSha256": h,
        "algorithm": "StandardScaler + LogisticRegression (scikit-learn)",
        "libraryVersions": {
            "python": sys.version.split()[0],
            "scikit-learn": sklearn.__version__,
            "skl2onnx": skl2onnx.__version__,
            "onnx": onnx.__version__,
        },
        "metrics": {k: v for k, v in metrics.items() if k != "coeficientes_estandarizados"},
        "coeficientesEstandarizados": metrics["coeficientes_estandarizados"],
        "gates": gates,
        "gatesPass": all(gates.values()),
        "advertencia": (
            "Entrenado con datos SINTETICOS (RN-20). El AUC y el KS no "
            "representan poder predictivo real; demuestran que el "
            "pipeline funciona de punta a punta y es reproducible. Antes "
            "de decidir con datos reales, reentrenar y revalidar gates."
        ),
    }
    (MODEL_DIR / "model_card.json").write_text(
        json.dumps(model_card, indent=2, ensure_ascii=False), encoding="utf-8"
    )

    print(json.dumps({"metrics": model_card["metrics"], "gates": gates}, indent=2))
    print(f"\nONNX: {onnx_path}")
    print(f"Golden set: {golden_path}")
    print(f"Parámetros: {params_path}")
    print(f"Model card: {MODEL_DIR / 'model_card.json'}")

    if not model_card["gatesPass"]:
        print("\n[ADVERTENCIA] Al menos un gate no pasó. Ver model_card.json.")
        sys.exit(1)


if __name__ == "__main__":
    main()
