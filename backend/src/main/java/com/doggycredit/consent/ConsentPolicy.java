package com.doggycredit.consent;

import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.doggycredit.consent.internal.ConsentRepository;
import com.doggycredit.shared.DocumentNumber;

/**
 * API pública del módulo para los demás: ¿hay consentimiento vigente
 * (ACTIVO y no expirado) del titular, en este tenant, para esta fuente?
 */
@Service
public class ConsentPolicy {

	private final ConsentRepository consents;

	ConsentPolicy(ConsentRepository consents) {
		this.consents = consents;
	}

	/** @throws ConsentRequiredException si no hay consentimiento vigente que cubra la fuente. */
	public void requireActive(UUID tenantId, String documento, Source source) {
		Objects.requireNonNull(tenantId, "tenantId");
		Objects.requireNonNull(source, "source");
		if (!consents.existsActive(tenantId, DocumentNumber.normalize(documento), source.name())) {
			throw new ConsentRequiredException(source);
		}
	}

}
