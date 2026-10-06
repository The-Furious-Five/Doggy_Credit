package com.doggycredit.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import com.doggycredit.consent.Source;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pruebas de contrato de B-03 contra el simulador publicado real
 * (simulators/wiremock/bank, misma imagen que infra/compose.yaml):
 * generador sintético ↔ simulador ↔ capa anticorrupción. El enforcement del
 * consentimiento es del gateway y ya lo cubre {@link BureauConnectorContractTests}.
 */
class BankConnectorContractTests {

	static final GenericContainer<?> WIREMOCK = new GenericContainer<>(DockerImageName.parse("wiremock/wiremock:3.9.1"))
		.withCommand("--global-response-templating")
		.withCopyFileToContainer(MountableFile.forHostPath(repoPath("simulators/wiremock/bank")), "/home/wiremock")
		.withExposedPorts(8080)
		.waitingFor(Wait.forHttp("/__admin/health"));

	static {
		WIREMOCK.start();
		// Calienta Handlebars (el 404 usa plantilla); ver BureauConnectorContractTests.
		RestClient.builder()
			.baseUrl(url())
			.requestFactory(new SimpleClientHttpRequestFactory())
			.build()
			.get()
			.uri("/bank/v1/clientes/CALENTAMIENTO")
			.exchange((request, response) -> response.getStatusCode());
	}

	private static final UUID TENANT = UUID.randomUUID();

	private final BankConnector bank = new BankConnector(url(), Duration.ofMillis(500), Duration.ofMillis(1500));

	@Test
	void titularDemoSeMapeaAlCanonico() {
		BankReport r = bank.fetch(TENANT, "CI-DEMO-0001001").orElseThrow();
		assertThat(r.cuentas()).singleElement().satisfies(c -> {
			assertThat(c.tipo()).isEqualTo("CAJA_AHORRO");
			assertThat(c.saldoPromedio3m().amount()).hasToString("8700.00");
			assertThat(c.saldoPromedio3m().currency().getCurrencyCode()).isEqualTo("BOB");
			assertThat(c.antiguedadMeses()).isEqualTo(48);
		});
		assertThat(r.ingresosMensuales()).hasSize(6).first().satisfies(m -> assertThat(m.amount()).hasToString("9940.00"));
		assertThat(r.egresosMensuales()).hasSize(6);
		assertThat(r.fechaCorte()).isEqualTo(LocalDate.of(2026, 9, 1));
		assertThat(r.linaje().fuente()).isEqualTo(Source.BANCO);
		assertThat(r.linaje().sha256Crudo()).hasSize(64);
	}

	/** El contrato de verdad: cada persona sembrada llega igual a través del simulador y la ACL. */
	@Test
	void todasLasPersonasSinteticasCoincidenConLaSemilla() throws Exception {
		JsonNode personas = JsonMapper.builder().build()
			.readTree(Files.readString(repoPath("seed/personas-sinteticas.json")))
			.get("personas");
		assertThat(personas.size()).isEqualTo(50);

		for (JsonNode p : personas) {
			String documento = p.get("documento").asString();
			JsonNode b = p.get("banco");
			BankReport r = bank.fetch(TENANT, documento).orElseThrow();

			assertThat(r.cuentas()).as(documento).singleElement().satisfies(c -> {
				assertThat(c.tipo()).isEqualTo(b.get("tipo_cuenta").asString());
				assertThat(c.saldoPromedio3m().amount()).hasToString(b.get("saldo_promedio_3m").asString());
				assertThat(c.antiguedadMeses()).isEqualTo(b.get("antiguedad_cuenta_meses").asInt());
			});
			for (int i = 0; i < 6; i++) {
				assertThat(r.ingresosMensuales().get(i).amount()).as(documento)
					.hasToString(b.get("abonos_mensuales").get(i).asString());
				assertThat(r.egresosMensuales().get(i).amount()).as(documento)
					.hasToString(b.get("cargos_mensuales").get(i).asString());
			}
		}
	}

	@Test
	void documentoDesconocidoEsSinCuentaNoUnaCaida() {
		assertThat(bank.fetch(TENANT, "CI-DEMO-0009999")).isEmpty();
	}

	@Test
	void documentoSinNormalizarFunciona() {
		assertThat(bank.fetch(TENANT, " ci-demo-0 001.001 ")).isPresent();
	}

	@Test
	void servicioNoDisponibleEsFuenteCaida() {
		assertUnavailable("CI-DEMO-0009503");
	}

	@Test
	void respuestaTardiaCortaPorTimeout() {
		long start = System.nanoTime();
		assertUnavailable("CI-DEMO-0009504");
		assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));
	}

	private void assertUnavailable(String documento) {
		assertThatThrownBy(() -> bank.fetch(TENANT, documento))
			.isInstanceOfSatisfying(SourceUnavailableException.class, e -> assertThat(e.source()).isEqualTo(Source.BANCO));
	}

	private static String url() {
		return "http://" + WIREMOCK.getHost() + ":" + WIREMOCK.getMappedPort(8080);
	}

	/** Busca la ruta subiendo desde el directorio de trabajo (backend/ con Maven, la raíz desde el IDE). */
	static Path repoPath(String relative) {
		for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
			if (Files.exists(dir.resolve(relative))) {
				return dir.resolve(relative);
			}
		}
		throw new IllegalStateException("No se encontró " + relative + " subiendo desde " + Path.of("").toAbsolutePath());
	}

}
