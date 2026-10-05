package com.doggycredit.analytics;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * D-08: deriva del score de cada modelo usado por el tenant (PSI semanal).
 * Lo consume {@code GET /analytics/resumen} (E-02) para la tarjeta «Modelos
 * de scoring» de {@code /app/analitica}.
 */
public interface ModelDrift {

	/** Último resultado por modelo del tenant; vacío si el job todavía no corrió. */
	List<Status> latest(UUID tenantId);

	/**
	 * Umbrales habituales del PSI: menor a 0,10 estable; 0,10 a 0,25 vigilar;
	 * 0,25 o más, alerta. Con menos de {@code minSample} evaluaciones en alguna
	 * de las dos ventanas no se calcula: INSUFICIENTE.
	 */
	enum Level {
		ESTABLE, VIGILAR, ALERTA, INSUFICIENTE
	}

	/**
	 * @param psi           null si es INSUFICIENTE
	 * @param windowStart   inicio de la semana evaluada (la línea base son las 4 semanas anteriores)
	 * @param actualCount   evaluaciones de la semana
	 * @param baselineCount evaluaciones de la línea base
	 */
	record Status(String modelVersion, Double psi, Level level, Instant windowStart, Instant windowEnd,
			int actualCount, int baselineCount) {
	}

}
