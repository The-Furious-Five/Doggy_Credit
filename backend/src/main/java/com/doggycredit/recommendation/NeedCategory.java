package com.doggycredit.recommendation;

/** Las 4 necesidades de crédito que muestra la consola (perfil, Resumen, Analítica y Resultado). */
public enum NeedCategory {

	CAPITAL_TRABAJO("Capital de trabajo"),
	CONSUMO_CONSOLIDACION("Consumo y consolidación"),
	EFICIENCIA_ENERGETICA("Eficiencia energética"),
	INVERSION_PRODUCTIVA("Inversión productiva");

	private final String label;

	NeedCategory(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}

}
