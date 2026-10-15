package com.doggycredit.consent;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * C-11 (LOS-112), RF-CON-03: resumen de consentimientos para el reporte de accesos («con
 * qué autorización»). Sin el documento del titular: el reporte no lleva datos personales.
 */
public interface ConsentSummaries {

	/** Los consentimientos del tenant con esos ids; los que no existan o sean de otro tenant no vienen. */
	List<Summary> findAll(UUID tenantId, Collection<UUID> consentIds);

	/** @param status ACTIVO, REVOCADO o EXPIRADO, al momento de la consulta. */
	record Summary(UUID id, List<Source> sources, String purpose, String status, Instant grantedAt,
			Instant expiresAt, Instant revokedAt) {
	}

}
