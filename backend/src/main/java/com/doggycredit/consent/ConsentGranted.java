package com.doggycredit.consent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.modulith.events.Externalized;

/**
 * Evento de dominio: se otorgó un consentimiento (D-03). Esquema publicado en
 * {@code contracts/events/ConsentGranted.v1.schema.json}; cualquier cambio de
 * campos va en un {@code v2}, nunca sobre este.
 * <p>
 * Sin el documento del titular (minimización): quien lo consuma lo resuelve
 * por {@code consentId}. Se publica en el outbox de Spring Modulith y se
 * externaliza con el {@code tenantId} como clave (DEBT-002: hoy sin broker).
 */
@Externalized("doggycredit.consent::#{tenantId()}")
public record ConsentGranted(UUID eventId, Instant ocurridoEn, UUID tenantId, UUID consentId,
		List<Source> fuentes, String finalidad, int vigenciaDias, Instant expiraEn) {

	public static final String SCHEMA = "ConsentGranted.v1";

}
