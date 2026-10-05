package com.doggycredit.recommendation;

import java.math.BigDecimal;
import java.util.List;

/**
 * D-06 (RF-NEC-01): a partir del perfil del titular y del destino declarado,
 * infiere la necesidad de crédito y sugiere monto y plazo según su capacidad
 * de pago (Reglas de Negocio, §5.2: CA-01 a CA-04). Es la entrada del motor de
 * elegibilidad (C-03), que luego lo ajusta al máximo y la tasa de cada producto.
 */
public interface CreditNeeds {

	CreditNeed analyze(NeedInput input);

	enum HolderType {
		PERSONA, PYME
	}

	/**
	 * Lo que la necesidad usa del Perfil 360 (B-04) y de la solicitud. Todo lo
	 * opcional puede venir en null: sin fuentes, el análisis sigue con lo declarado.
	 *
	 * @param declaredDestination   destino declarado por el titular (texto libre)
	 * @param greenDestinationCode  {@code necesidad.destinoVerde} del Perfil 360 (VER-*)
	 * @param declaredMonthlyIncome ingreso mensual declarado
	 * @param observedNetFlows      flujos netos mensuales observados (banco + billetera), hasta 6 meses
	 * @param currentInstallments   cuotas vigentes según el buró
	 * @param monthlySales          ventas mensuales de la PYME, de la más antigua a la más reciente
	 * @param creditors             cantidad de acreedores en el buró
	 */
	record NeedInput(HolderType holderType, String declaredDestination, String greenDestinationCode,
			BigDecimal declaredMonthlyIncome, List<BigDecimal> observedNetFlows, BigDecimal currentInstallments,
			List<BigDecimal> monthlySales, Integer creditors, BigDecimal requestedAmount, Integer requestedTermMonths) {
	}

	/**
	 * @param netMonthlyIncome ingreso neto según CA-01 (o CA-02 si no hay flujos observados)
	 * @param observedIncome   false si el ingreso sale solo de lo declarado (CA-02: no se puede preaprobar)
	 * @param maxInstallment   cuota máxima según CA-03 (0 si no hay capacidad)
	 * @param suggestedAmount  monto sugerido según CA-04, antes del máximo de cada producto
	 * @param signals          por qué se infirió esta necesidad, en lenguaje de la consola
	 */
	record CreditNeed(NeedCategory category, BigDecimal netMonthlyIncome, boolean observedIncome,
			BigDecimal maxInstallment, BigDecimal suggestedAmount, int suggestedTermMonths, List<String> signals) {
	}

}
