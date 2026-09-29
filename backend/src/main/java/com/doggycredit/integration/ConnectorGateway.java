package com.doggycredit.integration;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.doggycredit.consent.ConsentPolicy;

/**
 * Único punto de enforcement de la política de consentimiento (RF-CON-01.1):
 * sin consentimiento vigente para la fuente del conector, lanza
 * {@link com.doggycredit.consent.ConsentRequiredException} y el conector no
 * se llama.
 */
@Component
public class ConnectorGateway {

	private final ConsentPolicy consentPolicy;

	ConnectorGateway(ConsentPolicy consentPolicy) {
		this.consentPolicy = consentPolicy;
	}

	public <T> T fetch(SourceConnector<T> connector, UUID tenantId, String documento) {
		consentPolicy.requireActive(tenantId, documento, connector.source());
		return connector.fetch(tenantId, documento);
	}

}
