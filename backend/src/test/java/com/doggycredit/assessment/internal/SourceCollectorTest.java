package com.doggycredit.assessment.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.doggycredit.assessment.internal.SourceCollection.SourceOutcome.Status;
import com.doggycredit.consent.ConsentRequiredException;
import com.doggycredit.consent.Source;
import com.doggycredit.integration.ConnectorGateway;
import com.doggycredit.integration.SourceConnector;
import com.doggycredit.integration.SourceUnavailableException;

/**
 * C-01 (LOS-81): recolección de las 3 fuentes en paralelo. Prueba unitaria: banco y
 * billetera son conectores de prueba (aún no existen B-03/B-05); el flujo con el
 * buró real por HTTP lo cubren AssessmentControllerTests y BureauConnectorContractTests.
 */
class SourceCollectorTest {

	private static final UUID TENANT = UUID.randomUUID();
	private static final UUID CONSENT = UUID.randomUUID();
	private static final String DOCUMENT = "CI-PARALELO-0001";

	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

	private final Set<Source> sinConsentimiento = EnumSet.noneOf(Source.class);

	@AfterEach
	void closeExecutor() {
		executor.close();
	}

	@Test
	void lasTresFuentesRespondenYNoEsParcial() {
		SourceCollection collection = collector(Duration.ofSeconds(2),
				responde(Source.BURO), responde(Source.BANCO), responde(Source.WALLET)).collect(TENANT, CONSENT, DOCUMENT);

		assertThat(collection.partial()).isFalse();
		assertThat(collection.payload(Source.BANCO)).contains("datos de BANCO");
	}

	@Test
	void buroCaidoEsParcial() {
		SourceCollection collection = collector(Duration.ofSeconds(2),
				caida(Source.BURO), responde(Source.BANCO), responde(Source.WALLET)).collect(TENANT, CONSENT, DOCUMENT);

		assertThat(collection.partial()).isTrue();
		assertThat(collection.failed(Source.BURO)).isTrue();
		assertThat(collection.payload(Source.BANCO)).isPresent();
	}

	@Test
	void bancoCaidoEsParcialPeroElBuroSeConserva() {
		SourceCollection collection = collector(Duration.ofSeconds(2),
				responde(Source.BURO), caida(Source.BANCO), responde(Source.WALLET)).collect(TENANT, CONSENT, DOCUMENT);

		assertThat(collection.partial()).isTrue();
		assertThat(collection.failed(Source.BANCO)).isTrue();
		assertThat(collection.failed(Source.BURO)).isFalse();
		assertThat(collection.payload(Source.BURO)).contains("datos de BURO");
	}

	@Test
	void billeteraCaidaEsParcial() {
		SourceCollection collection = collector(Duration.ofSeconds(2),
				responde(Source.BURO), responde(Source.BANCO), caida(Source.WALLET)).collect(TENANT, CONSENT, DOCUMENT);

		assertThat(collection.partial()).isTrue();
		assertThat(collection.failed(Source.WALLET)).isTrue();
	}

	@Test
	void timeoutExcedidoCortaLaFuenteLentaYEsParcial() {
		long inicio = System.nanoTime();
		SourceCollection collection = collector(Duration.ofMillis(300),
				responde(Source.BURO), lenta(Source.BANCO, Duration.ofSeconds(5)), responde(Source.WALLET))
			.collect(TENANT, CONSENT, DOCUMENT);
		Duration total = Duration.ofNanos(System.nanoTime() - inicio);

		assertThat(collection.partial()).isTrue();
		assertThat(collection.outcomes().get(Source.BANCO).status()).isEqualTo(Status.TIMED_OUT);
		assertThat(collection.payload(Source.BURO)).isPresent();
		assertThat(total).isLessThan(Duration.ofSeconds(2));
	}

	@Test
	void lasFuentesSeConsultanEnParaleloNoUnaTrasOtra() {
		Duration demora = Duration.ofMillis(400);
		long inicio = System.nanoTime();
		SourceCollection collection = collector(Duration.ofSeconds(2),
				lenta(Source.BURO, demora), lenta(Source.BANCO, demora), lenta(Source.WALLET, demora))
			.collect(TENANT, CONSENT, DOCUMENT);
		Duration total = Duration.ofNanos(System.nanoTime() - inicio);

		assertThat(collection.partial()).isFalse();
		// En serie serían 1,2 s; en paralelo, poco más de 0,4 s.
		assertThat(total).isLessThan(Duration.ofMillis(1000));
	}

	@Test
	void fuenteSinConsentimientoNoSeLlamaYNoCuentaComoFalla() {
		AtomicBoolean bancoLlamado = new AtomicBoolean();
		sinConsentimiento.add(Source.BANCO);
		SourceConnector<String> banco = conector(Source.BANCO, () -> {
			bancoLlamado.set(true);
			return "datos de BANCO";
		});

		SourceCollection collection = collector(Duration.ofSeconds(2), responde(Source.BURO), banco)
			.collect(TENANT, CONSENT, DOCUMENT);

		assertThat(bancoLlamado).isFalse();
		assertThat(collection.outcomes().get(Source.BANCO).status()).isEqualTo(Status.NOT_CONSENTED);
		assertThat(collection.partial()).isFalse();
	}

	@Test
	void sinConsentimientoParaElBuroSeRechazaLaEvaluacion() {
		sinConsentimiento.add(Source.BURO);

		assertThatThrownBy(() -> collector(Duration.ofSeconds(2), responde(Source.BURO), responde(Source.BANCO))
			.collect(TENANT, CONSENT, DOCUMENT))
			.isInstanceOf(ConsentRequiredException.class);
	}

	@Test
	void unErrorNuestroNoSeDisfrazaDeFuenteCaida() {
		SourceConnector<String> conBug = conector(Source.BANCO, () -> {
			throw new IllegalStateException("bug del conector");
		});

		assertThatThrownBy(() -> collector(Duration.ofSeconds(2), responde(Source.BURO), conBug)
			.collect(TENANT, CONSENT, DOCUMENT))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("bug del conector");
	}

	// --- utilidades ------------------------------------------------------------

	/** El gateway real valida el consentimiento en este hilo y lanza el fetch en el executor. */
	private SourceCollector collector(Duration callTimeout, SourceConnector<?>... connectors) {
		ConnectorGateway gateway = mock(ConnectorGateway.class);
		when(gateway.submit(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
			SourceConnector<?> connector = invocation.getArgument(0);
			if (sinConsentimiento.contains(connector.source())) {
				throw new ConsentRequiredException(connector.source());
			}
			ExecutorService pool = invocation.getArgument(4);
			return pool.submit(() -> connector.fetch(TENANT, DOCUMENT));
		});
		return new SourceCollector(gateway, List.of(connectors), executor, Duration.ofSeconds(2), callTimeout);
	}

	private static SourceConnector<String> responde(Source source) {
		return conector(source, () -> "datos de " + source);
	}

	private static SourceConnector<String> caida(Source source) {
		return conector(source, () -> {
			throw new SourceUnavailableException(source, "simulada", null);
		});
	}

	private static SourceConnector<String> lenta(Source source, Duration demora) {
		return conector(source, () -> {
			try {
				Thread.sleep(demora);
			}
			catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
			}
			return "datos de " + source;
		});
	}

	private static SourceConnector<String> conector(Source source, java.util.function.Supplier<String> fetch) {
		return new SourceConnector<>() {
			@Override
			public Source source() {
				return source;
			}

			@Override
			public String fetch(UUID tenantId, String documento) {
				return fetch.get();
			}
		};
	}

}
