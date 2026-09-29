package com.doggycredit.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import com.doggycredit.TestcontainersConfiguration;
import com.doggycredit.consent.ConsentRequiredException;
import com.doggycredit.consent.Source;
import com.doggycredit.integration.BureauReport.TipoTitular;
import com.doggycredit.shared.TenantContext;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pruebas de contrato de RF-INT-02.1 contra el simulador publicado real
 * (simulators/wiremock/bureau, misma imagen que infra/compose.yaml): generador
 * sintético ↔ simulador ↔ capa anticorrupción.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class BureauConnectorContractTests {

	static final GenericContainer<?> WIREMOCK = new GenericContainer<>(DockerImageName.parse("wiremock/wiremock:3.9.1"))
		.withCommand("--global-response-templating")
		.withCopyFileToContainer(MountableFile.forHostPath(repoPath("simulators/wiremock/bureau")), "/home/wiremock")
		.withExposedPorts(8080)
		.waitingFor(Wait.forHttp("/__admin/health"));

	static {
		WIREMOCK.start();
		// El 404 es la única respuesta con plantilla ({{request.path.[3]}}), y
		// WireMock inicializa Handlebars en frío con la primera que la usa:
		// medido, ~1,6 s contra un read-timeout de 1,5 s — el primer documento
		// desconocido salía como "fuente caída". Se calienta una vez aquí,
		// igual que lo hace el healthcheck del simulador en infra/compose.yaml.
		RestClient.builder()
			.baseUrl(wiremockUrl())
			.requestFactory(new SimpleClientHttpRequestFactory())
			.build()
			.get()
			.uri("/bureau/v1/informe/CALENTAMIENTO")
			.exchange((request, response) -> response.getStatusCode());
	}

	@DynamicPropertySource
	static void bureauUrl(DynamicPropertyRegistry registry) {
		registry.add("doggycredit.integration.bureau.url", BureauConnectorContractTests::wiremockUrl);
	}

	@Autowired
	BureauConnector bureau;

	@Autowired
	ConnectorGateway gateway;

	@Autowired
	JdbcTemplate jdbc;

	private static final UUID TENANT = UUID.randomUUID();

	@Test
	void titularesDemoSeMapeanAlCanonico() {
		BureauReport persona = bureau.fetch(TENANT, "CI-DEMO-0001001").orElseThrow();
		assertThat(persona.tipo()).isEqualTo(TipoTitular.PERSONA);
		assertThat(persona.antiguedadSistemaFinancieroMeses()).isEqualTo(92);
		assertThat(persona.endeudamiento()).singleElement().satisfies(d -> {
			assertThat(d.fuente()).isEqualTo(Source.BURO);
			assertThat(d.saldo().amount()).hasToString("8580.00");
			assertThat(d.saldo().currency().getCurrencyCode()).isEqualTo("BOB");
			assertThat(d.cuota().amount()).hasToString("1930.00");
			assertThat(d.diasMoraMax12m()).isZero();
			assertThat(d.calificacion()).isEqualTo("A");
			assertThat(d.fechaCorte()).isEqualTo(LocalDate.of(2026, 9, 1));
		});
		assertThat(persona.linaje().sha256Crudo()).hasSize(64);

		BureauReport pyme = bureau.fetch(TENANT, "NIT-DEMO-9001001").orElseThrow();
		assertThat(pyme.tipo()).isEqualTo(TipoTitular.PYME);
		assertThat(pyme.antiguedadSistemaFinancieroMeses()).isEqualTo(29);
		assertThat(pyme.endeudamiento()).singleElement().satisfies(d -> {
			assertThat(d.saldo().amount()).hasToString("22920.00");
			assertThat(d.cuota().amount()).hasToString("1470.00");
		});
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
			JsonNode f = p.get("finanzas");
			BureauReport r = bureau.fetch(TENANT, documento).orElseThrow();

			assertThat(r.tipo()).as(documento).hasToString(p.get("tipo").asString());
			assertThat(r.antiguedadSistemaFinancieroMeses()).as(documento).isEqualTo(f.get("antiguedad_sf_meses").asInt());
			if (f.get("calificacion").asString().equals("N")) {
				assertThat(r.endeudamiento()).as(documento).isEmpty();
				continue;
			}
			assertThat(r.endeudamiento()).as(documento).singleElement().satisfies(d -> {
				assertThat(d.saldo().amount()).hasToString(f.get("deuda_vigente").asString());
				assertThat(d.cuota().amount()).hasToString(f.get("cuota_mensual").asString());
				assertThat(d.saldo().currency().getCurrencyCode()).isEqualTo(p.get("moneda").asString());
				assertThat(d.diasMoraMax12m()).isEqualTo(f.get("mora_max_12m_dias").asInt());
				assertThat(d.calificacion()).isEqualTo(f.get("calificacion").asString());
			});
		}
	}

	@Test
	void documentoDesconocidoEsSinRegistroNoUnaCaida() {
		assertThat(bureau.fetch(TENANT, "CI-DEMO-0009999")).isEmpty();
	}

	@Test
	void documentoSinNormalizarFunciona() {
		assertThat(bureau.fetch(TENANT, " ci-demo-0 001.001 ")).isPresent();
	}

	@Test
	void servicioNoDisponibleEsFuenteCaida() {
		assertUnavailable("CI-DEMO-0009503");
	}

	@Test
	void respuestaTardiaCortaPorTimeout() {
		long start = System.nanoTime();
		assertUnavailable("CI-DEMO-0009504");
		// read-timeout por defecto 1500 ms; el simulador tarda 15 s.
		assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));
	}

	@Test
	void sinConsentimientoNiUnaLlamadaAlBuro() {
		String documento = "CI-DEMO-0001005";
		long antes = requests(documento);

		// SPIKE-1 (RLS): gateway.fetch lee consent.consents; sin TenantContext
		// (esta prueba llama al gateway directo, sin petición HTTP real de
		// por medio) la política lo filtra a cero filas siempre, sin importar
		// si el consentimiento existe. Se declara "esto corre como TENANT"
		// a mano, tal como lo haría TenantClaimFilter en una petición real.
		TenantContext.set(TENANT);
		try {
			assertThatThrownBy(() -> gateway.fetch(bureau, TENANT, documento)).isInstanceOf(ConsentRequiredException.class);
			assertThat(requests(documento)).isEqualTo(antes);

			insertConsent(TENANT, documento);
			Optional<BureauReport> report = gateway.fetch(bureau, TENANT, documento);
			assertThat(report).isPresent();
			assertThat(requests(documento)).isEqualTo(antes + 1);
		} finally {
			TenantContext.clear();
		}
	}

	private void assertUnavailable(String documento) {
		assertThatThrownBy(() -> bureau.fetch(TENANT, documento))
			.isInstanceOfSatisfying(SourceUnavailableException.class, e -> assertThat(e.source()).isEqualTo(Source.BURO));
	}

	/** Solicitudes que WireMock registró para el informe de este documento. */
	private static long requests(String documento) {
		// HTTP/1.1 (HttpURLConnection): el HttpClient del JDK intenta h2c y WireMock corta la conexión.
		Map<?, ?> count = RestClient.builder()
			.baseUrl(wiremockUrl())
			.requestFactory(new SimpleClientHttpRequestFactory())
			.build()
			.post()
			.uri("/__admin/requests/count")
			.body(Map.of("method", "GET", "url", "/bureau/v1/informe/" + documento))
			.retrieve()
			.body(Map.class);
		return ((Number) count.get("count")).longValue();
	}

	private void insertConsent(UUID tenant, String documento) {
		jdbc.update("""
				insert into consent.consents (id, tenant_id, titular_documento, fuentes, finalidad, vigencia_dias,
				  otorgado_en, expira_en, estado, otorgado_por, payload_sha256)
				values (?, ?, ?, '{BURO}'::text[], 'evaluacion', 90, now(), now() + interval '1 day', 'ACTIVO', 'test', 'x')
				""", UUID.randomUUID(), tenant, documento);
	}

	private static String wiremockUrl() {
		return "http://" + WIREMOCK.getHost() + ":" + WIREMOCK.getMappedPort(8080);
	}

	/** Busca la ruta subiendo desde el directorio de trabajo (backend/ con Maven, la raíz desde el IDE). */
	private static Path repoPath(String relative) {
		for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
			if (Files.exists(dir.resolve(relative))) {
				return dir.resolve(relative);
			}
		}
		throw new IllegalStateException("No se encontró " + relative + " subiendo desde " + Path.of("").toAbsolutePath());
	}

}
