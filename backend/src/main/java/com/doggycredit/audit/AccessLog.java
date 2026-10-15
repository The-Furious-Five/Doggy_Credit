package com.doggycredit.audit;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * C-11 (LOS-112), RF-CON-03: lectura de la bitácora del tenant actual para el reporte de
 * accesos (lo filtran @TenantId y la RLS). De lo más reciente a lo más antiguo, con
 * cursor sobre la secuencia de la cadena, igual que /auditoria.
 */
public interface AccessLog {

	/**
	 * @param applicantId titular, o null para no filtrar por titular.
	 * @param correlationId X-Correlation-Id exacto, o null para no filtrar por él.
	 * @param from incluido; null = sin límite.
	 * @param to excluido; null = sin límite.
	 * @param before secuencia del último registro de la página anterior; null = desde el final.
	 */
	Page find(UUID applicantId, String correlationId, Instant from, Instant to, Long before, int size);

	/** Una página y si hay más después de ella. */
	record Page(List<Entry> items, boolean hasMore) {
	}

	/** Un registro de la bitácora con el titular y la autorización que se guardaron. */
	record Entry(UUID id, long chainSeq, String actor, String action, String resourceType, UUID resourceId,
			String correlationId, Instant createdAt, String rowHash, UUID applicantId, UUID consentId) {
	}

}
