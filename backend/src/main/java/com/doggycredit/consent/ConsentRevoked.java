package com.doggycredit.consent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.modulith.events.Externalized;

/**
 * Evento de dominio: se revocó un consentimiento, total o por fuente (D-03).
 * Esquema publicado en {@code contracts/events/ConsentRevoked.v1.schema.json}.
 * Lo publica la revocación (B-07); si {@code fuentesVigentes} queda vacía, el
 * consentimiento entero pasó a REVOCADO.
 */
@Externalized("doggycredit.consent::#{tenantId()}")
public record ConsentRevoked(UUID eventId, Instant ocurridoEn, UUID tenantId, UUID consentId,
		List<Source> fuentesRevocadas, List<Source> fuentesVigentes) {

	public static final String SCHEMA = "ConsentRevoked.v1";

	public static ConsentRevoked of(UUID tenantId, UUID consentId, List<Source> fuentesRevocadas,
			List<Source> fuentesVigentes) {
		return new ConsentRevoked(UUID.randomUUID(), Instant.now(), tenantId, consentId,
				List.copyOf(fuentesRevocadas), List.copyOf(fuentesVigentes));
	}

}
