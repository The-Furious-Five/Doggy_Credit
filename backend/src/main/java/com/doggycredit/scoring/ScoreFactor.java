package com.doggycredit.scoring;

import java.util.List;

/**
 * Factores con los que se explica el score avanzado (D-05, RF-SCO-03), los
 * mismos de la sección «Factores» de la pestaña Score del perfil. Cada
 * variable del modelo pertenece a uno solo; la razón de un factor es la suma
 * de las contribuciones de sus variables, en puntos del score.
 */
public enum ScoreFactor {

	HISTORIAL_PAGOS("Historial de pagos",
			List.of("mora_max_12m_dias", "peor_calificacion_24m", "antiguedad_sistema_financiero_meses")),
	CAPACIDAD_PAGO("Capacidad de pago",
			List.of("dti", "ratio_egreso_ingreso_6m", "meses_flujo_negativo_6m")),
	ESTABILIDAD_INGRESOS("Estabilidad de ingresos",
			List.of("ingreso_promedio_6m_bob", "ingreso_volatilidad_6m", "es_pyme", "ventas_pyme_promedio_bob",
					"saldo_promedio_cuentas_3m_bob")),
	USO_CREDITO("Uso de crédito disponible",
			List.of("num_acreedores", "actividad_wallet_90d", "saldo_promedio_wallet_bob", "antiguedad_wallet_meses"));

	private final String label;
	private final List<String> features;

	ScoreFactor(String label, List<String> features) {
		this.label = label;
		this.features = features;
	}

	/** Nombre en la vista. */
	public String label() {
		return label;
	}

	/** Variables del modelo (nombres de {@code featureOrder}) que lo componen. */
	public List<String> features() {
		return features;
	}

	public static ScoreFactor of(String feature) {
		for (ScoreFactor factor : values()) {
			if (factor.features.contains(feature)) {
				return factor;
			}
		}
		throw new IllegalArgumentException("Variable sin factor: " + feature);
	}

}
