package com.doggycredit.integration;

import java.util.UUID;

import com.doggycredit.consent.Source;

/**
 * Conector hacia una fuente externa (buró, banco, billetera). Nadie llama a
 * {@link #fetch} directamente: se invoca siempre a través de
 * {@link ConnectorGateway#fetch}, que exige consentimiento vigente antes.
 */
public interface SourceConnector<T> {

	Source source();

	T fetch(UUID tenantId, String documento);

}
