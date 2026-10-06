package com.doggycredit.integration;

import static com.doggycredit.integration.BankConnectorContractTests.repoPath;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.time.Duration;
import java.time.YearMonth;
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

/** Pruebas de contrato de B-05 contra el simulador real de la billetera (simulators/wiremock/wallet). */
class WalletConnectorContractTests {

	static final GenericContainer<?> WIREMOCK = new GenericContainer<>(DockerImageName.parse("wiremock/wiremock:3.9.1"))
		.withCommand("--global-response-templating")
		.withCopyFileToContainer(MountableFile.forHostPath(repoPath("simulators/wiremock/wallet")), "/home/wiremock")
		.withExposedPorts(8080)
		.waitingFor(Wait.forHttp("/__admin/health"));

	static {
		WIREMOCK.start();
		RestClient.builder()
			.baseUrl(url())
			.requestFactory(new SimpleClientHttpRequestFactory())
			.build()
			.get()
			.uri("/wallet/v1/titulares/CALENTAMIENTO")
			.exchange((request, response) -> response.getStatusCode());
	}

	private static final UUID TENANT = UUID.randomUUID();

	private final WalletConnector wallet = new WalletConnector(url(), Duration.ofMillis(500), Duration.ofMillis(1500));

	@Test
	void titularSinHistorialUsaMasLaBilletera() {
		WalletReport r = wallet.fetch(TENANT, "CI-DEMO-0001003").orElseThrow();
		assertThat(r.antiguedadMeses()).isEqualTo(55);
		assertThat(r.saldoPromedio().amount()).hasToString("1760.00");
		assertThat(r.meses()).hasSize(6).first().satisfies(m -> {
			assertThat(m.periodo()).isEqualTo(YearMonth.of(2026, 3));
			assertThat(m.transacciones()).isEqualTo(58);
			assertThat(m.recibido().amount()).hasToString("3840.00");
			assertThat(m.enviado().amount()).hasToString("2910.00");
		});
		// (58 + 44 + 58 + 62 + 61 + 34) / 6 = 52,8
		assertThat(r.transaccionesMensuales()).isEqualTo(53);
		assertThat(r.linaje().fuente()).isEqualTo(Source.WALLET);
		assertThat(r.linaje().sha256Crudo()).hasSize(64);
	}

	@Test
	void todasLasPersonasSinteticasCoincidenConLaSemilla() throws Exception {
		JsonNode personas = JsonMapper.builder().build()
			.readTree(Files.readString(repoPath("seed/personas-sinteticas.json")))
			.get("personas");
		assertThat(personas.size()).isEqualTo(50);

		for (JsonNode p : personas) {
			String documento = p.get("documento").asString();
			JsonNode w = p.get("billetera");
			WalletReport r = wallet.fetch(TENANT, documento).orElseThrow();

			assertThat(r.antiguedadMeses()).as(documento).isEqualTo(w.get("antiguedad_meses").asInt());
			assertThat(r.saldoPromedio().amount()).as(documento).hasToString(w.get("saldo_promedio").asString());
			assertThat(r.meses()).as(documento).hasSize(6);
			for (int i = 0; i < 6; i++) {
				JsonNode esperado = w.get("meses").get(i);
				WalletReport.Mes mes = r.meses().get(i);
				assertThat(mes.periodo()).as(documento).hasToString(esperado.get("periodo").asString());
				assertThat(mes.transacciones()).as(documento).isEqualTo(esperado.get("movimientos").asInt());
				assertThat(mes.recibido().amount()).as(documento).hasToString(esperado.get("recibido").asString());
				assertThat(mes.enviado().amount()).as(documento).hasToString(esperado.get("enviado").asString());
			}
		}
	}

	@Test
	void documentoDesconocidoEsSinBilleteraNoUnaCaida() {
		assertThat(wallet.fetch(TENANT, "CI-DEMO-0009999")).isEmpty();
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
		assertThatThrownBy(() -> wallet.fetch(TENANT, documento))
			.isInstanceOfSatisfying(SourceUnavailableException.class, e -> assertThat(e.source()).isEqualTo(Source.WALLET));
	}

	private static String url() {
		return "http://" + WIREMOCK.getHost() + ":" + WIREMOCK.getMappedPort(8080);
	}

}
