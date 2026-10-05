package com.doggycredit.assessment.internal;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.doggycredit.assessment.internal.SourceCollection.SourceOutcome;
import com.doggycredit.consent.ConsentRequiredException;
import com.doggycredit.consent.Source;
import com.doggycredit.integration.ConnectorGateway;
import com.doggycredit.integration.SourceConnector;
import com.doggycredit.integration.SourceDisabledException;
import com.doggycredit.integration.SourceUnavailableException;

/**
 * C-01 (LOS-81): consulta todas las fuentes registradas (buró, banco, billetera)
 * a la vez, con un único presupuesto de recolección (informe de arquitectura,
 * resiliencia del microkernel): 2 s para todas en paralelo y como máximo 1,2 s
 * por llamada. Al vencer, la fuente cuenta como no respondida y la evaluación
 * sale PARCIAL (RN-15).
 * <p>
 * Toma todos los {@link SourceConnector} del contexto: un conector nuevo (banco,
 * billetera) entra en la recolección solo por existir como bean.
 * <p>
 * Consentimiento: se valida en este hilo, antes de lanzar nada (ver
 * {@link ConnectorGateway#submit}). Una fuente que el consentimiento no cubre no
 * se llama y no cuenta como falla; el buró es obligatorio en los tres productos,
 * así que sin consentimiento para él la petición se rechaza (422 CONSENT_REQUIRED).
 */
@Component
class SourceCollector {

	private final ConnectorGateway gateway;
	private final List<SourceConnector<?>> connectors;
	private final ExecutorService executor;
	private final Duration budget;
	private final Duration callTimeout;

	SourceCollector(ConnectorGateway gateway, List<SourceConnector<?>> connectors,
			@Qualifier("assessmentCollectionExecutor") ExecutorService executor,
			@Value("${doggycredit.assessment.collection-budget:2s}") Duration budget,
			@Value("${doggycredit.assessment.call-timeout:1200ms}") Duration callTimeout) {
		this.gateway = gateway;
		this.connectors = List.copyOf(connectors);
		this.executor = executor;
		this.budget = budget;
		this.callTimeout = callTimeout;
	}

	SourceCollection collect(UUID tenantId, UUID consentId, String document) {
		Map<Source, SourceOutcome> outcomes = new EnumMap<>(Source.class);
		Map<Source, Future<?>> pending = new EnumMap<>(Source.class);
		for (SourceConnector<?> connector : connectors) {
			try {
				pending.put(connector.source(), gateway.submit(connector, tenantId, consentId, document, executor));
			}
			catch (ConsentRequiredException exception) {
				if (connector.source() == Source.BURO) {
					pending.values().forEach(future -> future.cancel(true));
					throw exception;
				}
				outcomes.put(connector.source(), SourceOutcome.notConsented());
			}
			catch (SourceDisabledException exception) {
				// B-06: el tenant no la tiene activa, no se consulta ni cuenta como falla.
				if (connector.source() == Source.BURO) {
					pending.values().forEach(future -> future.cancel(true));
					throw exception;
				}
			}
		}

		// Todas arrancaron a la vez: cada una espera hasta el mismo plazo, el menor entre
		// el presupuesto total y el timeout por llamada.
		long deadline = System.nanoTime() + Math.min(budget.toNanos(), callTimeout.toNanos());
		pending.forEach((source, future) -> outcomes.put(source, await(future, deadline)));
		return new SourceCollection(outcomes);
	}

	private static SourceOutcome await(Future<?> future, long deadline) {
		try {
			return SourceOutcome.received(future.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS));
		}
		catch (TimeoutException exception) {
			future.cancel(true);
			return SourceOutcome.timedOut();
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			future.cancel(true);
			return SourceOutcome.unavailable();
		}
		catch (ExecutionException exception) {
			if (exception.getCause() instanceof SourceUnavailableException) {
				return SourceOutcome.unavailable();
			}
			// Un fallo que no es de la fuente (un bug nuestro) no se disfraza de PARCIAL.
			if (exception.getCause() instanceof RuntimeException runtime) {
				throw runtime;
			}
			throw new IllegalStateException("Fallo inesperado al recolectar la fuente", exception.getCause());
		}
	}

}
