package com.doggycredit.integration;

import java.util.Set;
import java.util.UUID;

import com.doggycredit.consent.Source;

/**
 * B-06: qué fuentes tiene activas cada tenant. {@link ConnectorGateway} no llama
 * a un conector inactivo; el orquestador (C-01) consulta solo estas fuentes.
 */
public interface TenantIntegrations {

	Set<Source> activeSources(UUID tenantId);

	default boolean isActive(UUID tenantId, Source source) {
		return activeSources(tenantId).contains(source);
	}

}
