package com.doggycredit.consent.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.doggycredit.consent.ConsentGranted;
import com.doggycredit.consent.Source;

/**
 * Alta de consentimientos (RF-CON-01.1). El controlador no toca el
 * repositorio directamente (regla de ArchitectureRulesTest).
 */
@Service
class ConsentService {

	private final ConsentRepository consents;

	private final ApplicationEventPublisher events;

	ConsentService(ConsentRepository consents, ApplicationEventPublisher events) {
		this.consents = consents;
		this.events = events;
	}

	/**
	 * Guarda el consentimiento con su evidencia: quién lo otorgó y el SHA-256 del payload canónico.
	 * Publica {@link ConsentGranted} en la misma transacción (outbox de Spring Modulith).
	 */
	@Transactional
	Consent grant(UUID tenantId, String documento, String[] fuentes, String finalidad, int dias, String otorgadoPor) {
		String canonical = String.join("|", documento, String.join(",", fuentes), finalidad, String.valueOf(dias));
		Consent consent = consents.save(new Consent(tenantId, documento, fuentes, finalidad, dias,
				Instant.now().truncatedTo(ChronoUnit.MICROS), otorgadoPor, sha256(canonical)));
		events.publishEvent(new ConsentGranted(UUID.randomUUID(), Instant.now(), tenantId, consent.getId(),
				Arrays.stream(fuentes).map(Source::valueOf).toList(), finalidad, dias, consent.getExpiraEn()));
		return consent;
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
