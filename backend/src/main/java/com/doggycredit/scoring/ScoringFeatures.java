package com.doggycredit.scoring;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Vector de variables del modelo avanzado (feature_set v2, ver
 * {@code ml/model/model_card.json}). {@code null} significa "no hay dato"
 * (fuente sin consentimiento, caída o todavía no integrada): el modelo la
 * imputa con su media de entrenamiento, que en el espacio estandarizado es 0
 * y no mueve el score ni hacia arriba ni hacia abajo.
 */
public record ScoringFeatures(Double dti, Double moraMax12mDias, Double numAcreedores, Double peorCalificacion24m,
		Double antiguedadSistemaFinancieroMeses, Double ingresoPromedio6mBob, Double ingresoVolatilidad6m,
		Double actividadWallet90d, Double esPyme, Double ventasPymePromedioBob, Double saldoPromedioCuentas3mBob,
		Double ratioEgresoIngreso6m, Double mesesFlujoNegativo6m, Double saldoPromedioWalletBob,
		Double antiguedadWalletMeses) {

	/** Por el nombre de columna con el que se entrenó el modelo. */
	public Map<String, Double> byName() {
		Map<String, Double> values = new LinkedHashMap<>();
		values.put("dti", dti);
		values.put("mora_max_12m_dias", moraMax12mDias);
		values.put("num_acreedores", numAcreedores);
		values.put("peor_calificacion_24m", peorCalificacion24m);
		values.put("antiguedad_sistema_financiero_meses", antiguedadSistemaFinancieroMeses);
		values.put("ingreso_promedio_6m_bob", ingresoPromedio6mBob);
		values.put("ingreso_volatilidad_6m", ingresoVolatilidad6m);
		values.put("actividad_wallet_90d", actividadWallet90d);
		values.put("es_pyme", esPyme);
		values.put("ventas_pyme_promedio_bob", ventasPymePromedioBob);
		values.put("saldo_promedio_cuentas_3m_bob", saldoPromedioCuentas3mBob);
		values.put("ratio_egreso_ingreso_6m", ratioEgresoIngreso6m);
		values.put("meses_flujo_negativo_6m", mesesFlujoNegativo6m);
		values.put("saldo_promedio_wallet_bob", saldoPromedioWalletBob);
		values.put("antiguedad_wallet_meses", antiguedadWalletMeses);
		return values;
	}

}
