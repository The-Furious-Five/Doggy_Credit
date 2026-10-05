package com.doggycredit.integration;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.stereotype.Component;

import com.doggycredit.consent.ConsentPolicy;

/**
 * Único punto de enforcement antes de llamar a una fuente: el tenant la tiene
 * activa (B-06, si no {@link SourceDisabledException}) y hay consentimiento
 * vigente (RF-CON-01.1, si no
 * {@link com.doggycredit.consent.ConsentRequiredException}). En ambos casos el
 * conector no se llama.
 */
@Component
public class ConnectorGateway {

	private final ConsentPolicy consentPolicy;
	private final TenantIntegrations integrations;

	ConnectorGateway(ConsentPolicy consentPolicy, TenantIntegrations integrations) {
		this.consentPolicy = consentPolicy;
		this.integrations = integrations;
	}

	public <T> T fetch(SourceConnector<T> connector, UUID tenantId, String documento) {
		requireEnabled(connector, tenantId);
		consentPolicy.requireActive(tenantId, documento, connector.source());
		return connector.fetch(tenantId, documento);
	}

	/**
	 * Variante para orquestadores con presupuesto: el consentimiento se valida
	 * antes de iniciar el reloj y solo la E/S externa corre en el executor.
	 */
	public <T> T fetchWithin(SourceConnector<T> connector, UUID tenantId, String documento,
			Duration budget, ExecutorService executor) {
		requireEnabled(connector, tenantId);
		consentPolicy.requireActive(tenantId, documento, connector.source());
		return fetchExternalWithin(connector, tenantId, documento, budget, executor);
	}

	/** Presupuesto externo y enforcement del consentimiento concreto referenciado por la solicitud. */
	public <T> T fetchWithin(SourceConnector<T> connector, UUID tenantId, UUID consentId, String documento,
			Duration budget, ExecutorService executor) {
		requireEnabled(connector, tenantId);
		consentPolicy.requireActive(tenantId, consentId, documento, connector.source());
		return fetchExternalWithin(connector, tenantId, documento, budget, executor);
	}

	private void requireEnabled(SourceConnector<?> connector, UUID tenantId) {
		if (!integrations.isActive(tenantId, connector.source())) {
			throw new SourceDisabledException(connector.source());
		}
	}

	private <T> T fetchExternalWithin(SourceConnector<T> connector, UUID tenantId, String documento,
			Duration budget, ExecutorService executor) {
		Future<T> future = executor.submit(() -> connector.fetch(tenantId, documento));
		try {
			return future.get(budget.toMillis(), TimeUnit.MILLISECONDS);
		}
		catch (TimeoutException exception) {
			future.cancel(true);
			throw new SourceUnavailableException(connector.source(), "presupuesto de recolección agotado", exception);
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new SourceUnavailableException(connector.source(), "recolección interrumpida", exception);
		}
		catch (ExecutionException exception) {
			if (exception.getCause() instanceof RuntimeException runtime) {
				throw runtime;
			}
			throw new SourceUnavailableException(connector.source(), "fallo inesperado", exception.getCause());
		}
	}

}
