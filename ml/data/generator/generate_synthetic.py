"""
Generador de datos sintéticos para el modelo de scoring (SPIKE-3, D-01).

Produce un dataset tabular con las variables del `feature_set v2` (el v1 del
informe de arquitectura, §11.3, más las variables de banco y billetera del
Perfil 360 que llegan en el Sprint 2) y una etiqueta `default` generada a
partir de una
combinación logística latente con ruido — así el modelo tiene señal real
que aprender, no puro azar, sin que ningún dato corresponda a una persona
real (RN-20).

Reproducible: la misma SEED siempre produce el mismo dataset (byte a byte),
que es uno de los gates de la §11.5 del informe.
"""

from __future__ import annotations

import hashlib
from pathlib import Path

import numpy as np
import pandas as pd

SEED = 20260101
N_SAMPLES = 4000
FEATURE_SET_VERSION = "v2"

FEATURE_COLUMNS = [
    "dti",  # carga financiera: cuota / ingreso neto
    "mora_max_12m_dias",
    "num_acreedores",
    "peor_calificacion_24m",  # 0=A (mejor) .. 4=E (peor)
    "antiguedad_sistema_financiero_meses",
    "ingreso_promedio_6m_bob",
    "ingreso_volatilidad_6m",  # coeficiente de variación
    "actividad_wallet_90d",  # transacciones en 90 días
    "es_pyme",
    "ventas_pyme_promedio_bob",
    # --- v2: banco (Perfil 360: cuentas[].saldoPromedio3m y flujos) ---
    "saldo_promedio_cuentas_3m_bob",  # 0 si el titular no tiene cuenta bancaria
    "ratio_egreso_ingreso_6m",  # egresos / ingresos de los últimos 6 meses
    "meses_flujo_negativo_6m",  # meses (0..6) con egresos > ingresos
    # --- v2: billetera (Perfil 360: wallet.saldoPromedio y antiguedadMeses) ---
    "saldo_promedio_wallet_bob",
    "antiguedad_wallet_meses",
]

# Variables PROHIBIDAS como feature del modelo (RN-04): sexo, etnia, idioma,
# religión, estado civil. Se generan aparte, SOLO para medir equidad
# (fairness), y nunca entran a X_train.
PROTECTED_COLUMNS = ["genero_declarado", "zona"]


def _sigmoid(x: np.ndarray) -> np.ndarray:
    return 1.0 / (1.0 + np.exp(-x))


def _z(x: np.ndarray) -> np.ndarray:
    return (x - x.mean()) / x.std()


def generate(n: int = N_SAMPLES, seed: int = SEED) -> pd.DataFrame:
    rng = np.random.default_rng(seed)

    es_pyme = rng.binomial(1, 0.25, n)

    dti = np.clip(rng.beta(2, 5, n) * 1.2, 0.0, 1.5)
    mora_max_12m_dias = rng.choice(
        [0, 15, 30, 60, 90, 120], size=n, p=[0.55, 0.15, 0.12, 0.10, 0.05, 0.03]
    )
    num_acreedores = rng.poisson(1.6, n)
    peor_calificacion_24m = rng.choice([0, 1, 2, 3, 4], size=n, p=[0.35, 0.30, 0.20, 0.10, 0.05])
    antiguedad_meses = np.clip(rng.gamma(3.0, 20.0, n), 0, 300).round()

    ingreso_base = np.where(
        es_pyme == 1,
        rng.lognormal(mean=8.6, sigma=0.5, size=n),  # PYME: ingresos más altos
        rng.lognormal(mean=7.3, sigma=0.55, size=n),
    )
    ingreso_promedio_6m_bob = np.round(ingreso_base, 2)
    ingreso_volatilidad_6m = np.clip(rng.beta(2, 8, n) * 1.5, 0.0, 1.0)

    actividad_wallet_90d = rng.poisson(np.where(es_pyme == 1, 8, 22), n)

    ventas_pyme_promedio_bob = np.where(
        es_pyme == 1, np.round(rng.lognormal(mean=9.3, sigma=0.6, size=n), 2), 0.0
    )

    # --- variables protegidas, generadas SIN relación causal con el riesgo real
    # (se usan solo para auditar disparidad, nunca como feature) ---
    genero_declarado = rng.choice(["F", "M"], size=n)
    zona = rng.choice(["URBANA", "RURAL"], size=n, p=[0.7, 0.3])

    # --- v2: banco. Se generan después de las variables v1 para que, con la
    # misma semilla, las columnas v1 sigan siendo idénticas a las del v1 ---
    tiene_cuenta = rng.binomial(1, np.where(es_pyme == 1, 0.92, 0.75), n)
    saldo_promedio_cuentas_3m_bob = np.where(
        tiene_cuenta == 1,
        np.round(ingreso_promedio_6m_bob * rng.lognormal(mean=-0.3, sigma=0.7, size=n), 2),
        0.0,
    )
    ratio_egreso_ingreso_6m = np.clip(rng.normal(0.70 + 0.25 * dti, 0.12, n), 0.30, 1.60)
    meses_flujo_negativo_6m = rng.binomial(6, _sigmoid((ratio_egreso_ingreso_6m - 0.95) * 10))

    # --- v2: billetera ---
    saldo_promedio_wallet_bob = np.round(
        rng.lognormal(mean=5.5, sigma=0.9, size=n) * (1 + actividad_wallet_90d / 30), 2
    )
    antiguedad_wallet_meses = np.clip(rng.gamma(2.0, 12.0, n), 0, 120).round()

    # --- variable latente estandarizada que define el riesgo real ---
    z_dti = (dti - dti.mean()) / dti.std()
    z_mora = (mora_max_12m_dias - mora_max_12m_dias.mean()) / mora_max_12m_dias.std()
    z_acreedores = (num_acreedores - num_acreedores.mean()) / num_acreedores.std()
    z_calif = (peor_calificacion_24m - peor_calificacion_24m.mean()) / peor_calificacion_24m.std()
    z_antig = (antiguedad_meses - antiguedad_meses.mean()) / antiguedad_meses.std()
    z_ingreso = (ingreso_promedio_6m_bob - ingreso_promedio_6m_bob.mean()) / ingreso_promedio_6m_bob.std()
    z_volat = (ingreso_volatilidad_6m - ingreso_volatilidad_6m.mean()) / ingreso_volatilidad_6m.std()
    z_wallet = (actividad_wallet_90d - actividad_wallet_90d.mean()) / actividad_wallet_90d.std()
    z_saldo_banco = _z(saldo_promedio_cuentas_3m_bob)
    z_ratio_flujo = _z(ratio_egreso_ingreso_6m)
    z_meses_neg = _z(meses_flujo_negativo_6m)
    z_saldo_wallet = _z(saldo_promedio_wallet_bob)
    z_antig_wallet = _z(antiguedad_wallet_meses)

    noise = rng.normal(0, 0.6, n)

    logit = (
        -2.0
        + 0.95 * z_dti
        + 0.55 * z_mora
        + 0.30 * z_acreedores
        + 0.45 * z_calif
        - 0.35 * z_antig
        - 0.50 * z_ingreso
        + 0.40 * z_volat
        - 0.25 * z_wallet
        - 0.30 * z_saldo_banco
        + 0.35 * z_ratio_flujo
        + 0.25 * z_meses_neg
        - 0.20 * z_saldo_wallet
        - 0.15 * z_antig_wallet
        + noise
    )
    p_default = _sigmoid(logit)
    default = rng.binomial(1, p_default)

    df = pd.DataFrame(
        {
            "dti": dti,
            "mora_max_12m_dias": mora_max_12m_dias,
            "num_acreedores": num_acreedores,
            "peor_calificacion_24m": peor_calificacion_24m,
            "antiguedad_sistema_financiero_meses": antiguedad_meses,
            "ingreso_promedio_6m_bob": ingreso_promedio_6m_bob,
            "ingreso_volatilidad_6m": ingreso_volatilidad_6m,
            "actividad_wallet_90d": actividad_wallet_90d,
            "es_pyme": es_pyme,
            "ventas_pyme_promedio_bob": ventas_pyme_promedio_bob,
            "saldo_promedio_cuentas_3m_bob": saldo_promedio_cuentas_3m_bob,
            "ratio_egreso_ingreso_6m": ratio_egreso_ingreso_6m,
            "meses_flujo_negativo_6m": meses_flujo_negativo_6m,
            "saldo_promedio_wallet_bob": saldo_promedio_wallet_bob,
            "antiguedad_wallet_meses": antiguedad_wallet_meses,
            "genero_declarado": genero_declarado,
            "zona": zona,
            "default": default,
        }
    )
    return df


def dataset_hash(df: pd.DataFrame) -> str:
    """Hash reproducible del dataset (gate de reproducibilidad, §11.5)."""
    return hashlib.sha256(pd.util.hash_pandas_object(df, index=True).values.tobytes()).hexdigest()


if __name__ == "__main__":
    out_dir = Path(__file__).resolve().parents[1] / "generated"
    out_dir.mkdir(parents=True, exist_ok=True)

    dataset = generate()
    out_path = out_dir / f"synthetic_{FEATURE_SET_VERSION}.csv"
    dataset.to_csv(out_path, index=False)

    h = dataset_hash(dataset)
    print(f"Filas: {len(dataset)}")
    print(f"Tasa de default: {dataset['default'].mean():.3f}")
    print(f"Guardado en: {out_path}")
    print(f"sha256: {h}")
