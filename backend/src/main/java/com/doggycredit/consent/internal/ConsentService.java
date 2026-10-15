package com.doggycredit.consent.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.doggycredit.audit.AccessSubject;
import com.doggycredit.audit.AuditTrail;
import com.doggycredit.consent.ApplicantDocuments;
import com.doggycredit.consent.ConsentSummaries;
import com.doggycredit.consent.ConsentNotFoundException;
import com.doggycredit.shared.UnknownApplicantException;
import com.doggycredit.consent.ConsentGranted;
import com.doggycredit.consent.ConsentRevoked;
import com.doggycredit.consent.Source;

/**
 * Alta (RF-CON-01.1), listado y revocación (B-07) de consentimientos. El
 * controlador no toca el repositorio directamente (regla de ArchitectureRulesTest).
 */
@Service
class ConsentService implements ConsentSummaries {

	private final ConsentRepository consents;
	private final ApplicantDocuments applicants;
	private final AuditTrail audit;
	private final ApplicationEventPublisher events;

	ConsentService(ConsentRepository consents, ApplicantDocuments applicants, AuditTrail audit,
			ApplicationEventPublisher events) {
		this.consents = consents;
		this.applicants = applicants;
		this.audit = audit;
		this.events = events;
	}

	@Override
	@Transactional(readOnly = true)
	public List<ConsentSummaries.Summary> findAll(UUID tenantId, Collection<UUID> consentIds) {
		Instant now = Instant.now();
		// Un consentimiento de otro tenant no se ve (RLS + @TenantId): no aparece.
		return consents.findAllById(consentIds).stream()
			.map(consent -> new ConsentSummaries.Summary(consent.getId(),
					Arrays.stream(consent.getFuentes()).map(Source::valueOf).toList(), consent.getFinalidad(),
					consent.estadoAt(now), consent.getOtorgadoEn(), consent.getExpiraEn(), consent.getRevocadoEn()))
			.toList();
	}

	@Transactional(readOnly = true)
	List<Consent> listForApplicant(UUID tenantId, UUID applicantId) {
		String documento = applicants.documentOf(tenantId, applicantId).orElseThrow(UnknownApplicantException::new);
		return consents.findByTenantIdAndTitularDocumentoOrderByOtorgadoEnDesc(tenantId, documento);
	}

	/** Revoca con evidencia (quién, cuándo) y deja rastro en la bitácora. Idempotente. */
	@Transactional
	Consent revoke(UUID tenantId, UUID consentId, String revokedBy) {
		Consent consent = consents.findByIdAndTenantId(consentId, tenantId).orElseThrow(ConsentNotFoundException::new);
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		if ("ACTIVO".equals(consent.estadoAt(now))) {
			consent.revoke(now, revokedBy);
			audit.record(tenantId, "CONSENT_REVOKED", "CONSENT", consentId, subject(tenantId, consent));
			// Revocación total: no queda ninguna fuente vigente (D-03).
			events.publishEvent(ConsentRevoked.of(tenantId, consentId,
					Arrays.stream(consent.getFuentes()).map(Source::valueOf).toList(), List.of()));
		}
		return consent;
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
		// C-11 (RF-CON-03): el otorgamiento también es parte de «con qué autorización».
		audit.record(tenantId, "CONSENT_GRANTED", "CONSENT", consent.getId(), subject(tenantId, consent));
		events.publishEvent(new ConsentGranted(UUID.randomUUID(), Instant.now(), tenantId, consent.getId(),
				Arrays.stream(fuentes).map(Source::valueOf).toList(), finalidad, dias, consent.getExpiraEn()));
		return consent;
	}

	/** El titular del consentimiento (si ya está registrado) y el propio consentimiento. */
	private AccessSubject subject(UUID tenantId, Consent consent) {
		UUID applicantId = applicants.applicantIdOf(tenantId, consent.getTitularDocumento()).orElse(null);
		return AccessSubject.applicant(applicantId, consent.getId());
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
