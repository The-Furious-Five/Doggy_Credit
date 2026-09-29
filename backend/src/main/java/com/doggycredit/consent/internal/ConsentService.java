package com.doggycredit.consent.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta de consentimientos (RF-CON-01.1). El controlador no toca el
 * repositorio directamente (regla de ArchitectureRulesTest).
 */
@Service
class ConsentService {

	private final ConsentRepository consents;
	private final JdbcTemplate jdbc;

	ConsentService(ConsentRepository consents, JdbcTemplate jdbc) {
		this.consents = consents;
		this.jdbc = jdbc;
	}

	/** Guarda el consentimiento con su evidencia: quién lo otorgó y el SHA-256 del payload canónico. */
	@Transactional
	Consent grant(UUID tenantId, String documento, String[] fuentes, String finalidad, int dias, String otorgadoPor) {
		// SET LOCAL via set_config(..., true) is scoped to this transaction; it
		// cannot leak across Hikari connections reused by another tenant.
		jdbc.queryForObject("select set_config('app.tenant_id', ?, true)", String.class, tenantId.toString());
		String canonical = String.join("|", documento, String.join(",", fuentes), finalidad, String.valueOf(dias));
		return consents.save(new Consent(tenantId, documento, fuentes, finalidad, dias,
				Instant.now().truncatedTo(ChronoUnit.MICROS), otorgadoPor, sha256(canonical)));
	}

	private static String sha256(String value) {
		try {
			return HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

}
