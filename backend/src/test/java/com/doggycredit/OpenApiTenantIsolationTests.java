package com.doggycredit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.yaml.snakeyaml.Yaml;

import com.doggycredit.shared.TenantContext;

/**
 * A-04 (LOS-66): meta-test de aislamiento sobre el contrato OpenAPI.
 * <ol>
 * <li>Toda operación del contrato debe figurar en {@link #COVERAGE} con la prueba que
 * demuestra su aislamiento entre tenants. Una ruta nueva sin cobertura hace fallar la
 * suite, igual que una entrada que ya no existe en el contrato.</li>
 * <li>Lectura: el tenant B recorre <b>todas</b> las operaciones GET del contrato y ninguna
 * respuesta contiene identificadores ni datos sembrados en el tenant A.</li>
 * <li>Escritura: el tenant B intenta modificar recursos del tenant A por su id; cada
 * intento responde 404 y el recurso queda igual.</li>
 * </ol>
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
// Registra tenants y filas propias: se descarta su contenedor para no alterar a
// TenantRegistryTest ni a ProductCatalogTableTests, que cuentan lo sembrado.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OpenApiTenantIsolationTests {

	private static final String TENANT_A = "9c7e7c1a-0000-4000-8000-000000000001"; // Progreso Andino (FREE)
	private static final String TENANT_B = "9c7e7c1a-0000-4000-8000-000000000002"; // Fintech Vecina (PRO)
	private static final Path CONTRACT = Path.of("..", "contracts", "openapi", "doggycredit-api.v1.yaml");
	private static final String HERE = "OpenApiTenantIsolationTests";

	/** Operación → prueba que demuestra que un tenant no lee ni modifica lo de otro. */
	private static final Map<String, String> COVERAGE = Map.ofEntries(
			Map.entry("GET /webhooks", HERE + " (lectura)"),
			Map.entry("PUT /webhooks", "RowLevelSecurityCoverageTests (webhooks.subscriptions con RLS forzado); WebhookDeliveryTests"),
			Map.entry("GET /analytics/resumen", HERE + " (lectura); AnalyticsControllerTests"),
			Map.entry("GET /solicitantes", HERE + " (lectura)"),
			Map.entry("POST /solicitantes", "ApplicantControllerTests: unicidad por tenant"),
			Map.entry("GET /solicitantes/{id}", HERE + " (lectura por id)"),
			Map.entry("GET /consentimientos", HERE + " (lectura)"),
			Map.entry("POST /consentimientos", "TenantIsolationTests.consentIsolation"),
			Map.entry("POST /consentimientos/{id}/revocar", HERE + " (escritura por id)"),
			Map.entry("GET /integraciones", HERE + " (lectura)"),
			Map.entry("PUT /integraciones/{fuente}", "IntegrationControllerTests; TenantIsolationTests (tenant_integrations)"),
			Map.entry("GET /integraciones/{fuente}/credenciales", HERE + " (lectura); IntegrationCredentialsTests"),
			Map.entry("PUT /integraciones/{fuente}/credenciales", "IntegrationCredentialsTests: RLS por tenant"),
			Map.entry("DELETE /integraciones/{fuente}/credenciales", "IntegrationCredentialsTests: RLS por tenant"),
			Map.entry("GET /credit-assessments", HERE + " (lectura)"),
			Map.entry("POST /credit-assessments", "AssessmentControllerTests: idempotencia por tenant"),
			Map.entry("GET /credit-assessments/{id}", "AssessmentControllerTests: otro tenant recibe 404"),
			Map.entry("GET /revision-manual", HERE + " (lectura); ManualReviewTests"),
			Map.entry("POST /revision-manual/{evaluacionId}/resolucion", "ManualReviewTests"),
			Map.entry("GET /auditoria", HERE + " (lectura); AuditQueryControllerTests"),
			Map.entry("GET /auditoria/accesos", HERE + " (lectura)"),
			Map.entry("GET /tenant/actual", HERE + " (lectura)"),
			Map.entry("GET /tenant/actual/equipo", HERE + " (lectura); TenantControllerTests (tenant del token)"),
			Map.entry("POST /tenant/actual/equipo", "TenantControllerTests: invita en el tenant del token"),
			Map.entry("GET /tenant/actual/preferencias", HERE + " (lectura)"),
			Map.entry("PATCH /tenant/actual/preferencias", "SensitiveDataMaskingTests: la preferencia no cruza tenants"),
			Map.entry("GET /tenant/actual/uso", HERE + " (lectura); TenantIsolationTests.monthlyUsageIsIsolatedAcrossTenants"),
			Map.entry("PATCH /tenant/actual/organizacion", "TenantControllerTests: actualiza el tenant del token"),
			Map.entry("GET /tenant/actual/plan", HERE + " (lectura)"),
			Map.entry("PUT /tenant/actual/plan/bajada", "PlanChangeTests.elTenantSoloProgramaSuPropiaBajadaAunqueNombreAOtroEnElCuerpo"),
			Map.entry("DELETE /tenant/actual/plan/bajada", "PlanChangeTests.laBajadaDeUnTenantNoTocaElPlanNiLasIntegracionesDeOtro"),
			Map.entry("GET /admin/tenants", "AdminControllerTests: solo PLATFORM_ADMIN (vista de operador, entre tenants por diseño)"),
			Map.entry("PATCH /admin/tenants/{id}", "PlanChangeTests.elOperadorCambiaElTenantDeLaRutaYNoElSuyo"),
			Map.entry("GET /operador/metricas", "Sin implementar (D-14 / LOS-134): solo agregados. Al implementarla, sumar su prueba"),
			Map.entry("GET /api-keys", HERE + " (lectura)"),
			Map.entry("POST /api-keys", "KeycloakApiKeyIntegrationTests: la clave lleva el tenant del token"),
			Map.entry("DELETE /api-keys/{id}", HERE + " (escritura por id)"),
			Map.entry("GET /productos", HERE + " (lectura)"),
			Map.entry("PUT /productos/{id}", HERE + " (escritura por id)"),
			Map.entry("GET /politicas", HERE + " (lectura)"),
			Map.entry("PUT /politicas/{id}", HERE + " (escritura por id)"));

	/** Rutas del contrato que solo usa el operador: no tienen sentido como lectura de un tenant. */
	private static final Set<String> OPERATOR_ONLY = Set.of("GET /admin/tenants", "GET /operador/metricas");

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	private String applicantId;
	private String document;
	private UUID productId;
	private UUID policyId;
	private UUID consentId;
	private UUID apiKeyId;

	@BeforeAll
	void seedTenantA() throws Exception {
		String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
		document = "ISO-META-" + suffix;
		String response = mvc.perform(post("/solicitantes").with(as(TENANT_A))
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"documento":"%s","actividad":"Comercio","antiguedadActividadMeses":24,
						 "ingresosMensuales":"3100.00","moneda":"BOB","destinoCredito":"Capital de trabajo"}
						""".formatted(document)))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		applicantId = response.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
		consentId = UUID.randomUUID();
		apiKeyId = UUID.randomUUID();
		asTenant(TENANT_A, () -> {
			productId = jdbc.queryForObject("""
					select id from recommendation.products where tenant_id = ?::uuid and product_type = 'MICROCREDITO'
					""", UUID.class, TENANT_A);
			policyId = jdbc.queryForObject("select id from assessment.policies where tenant_id = ?::uuid order by id limit 1",
					UUID.class, TENANT_A);
			jdbc.update("""
					insert into consent.consents (id, tenant_id, titular_documento, fuentes, finalidad, vigencia_dias,
					  otorgado_en, expira_en, estado, otorgado_por, payload_sha256)
					values (?, ?::uuid, ?, '{BURO}'::text[], 'evaluacion', 90, now(), now() + interval '1 day',
					  'ACTIVO', 'test', 'x')
					""", consentId, TENANT_A, document);
			jdbc.update("""
					insert into tenancy.api_keys (id, tenant_id, nombre, client_id, keycloak_id, entorno,
					  sufijo_secreto, creada_por, creada_en)
					values (?, ?::uuid, 'meta-aislamiento', ?, ?, 'SANDBOX', '9876', 'test', now())
					""", apiKeyId, TENANT_A, "dc-meta-" + suffix.toLowerCase(), UUID.randomUUID());
			return null;
		});
	}

	@Test
	void todaOperacionDelContratoTieneCoberturaDeAislamiento() throws Exception {
		Set<String> operations = operations();
		assertThat(new TreeSet<>(COVERAGE.keySet()))
			.as("Cada operación del contrato necesita su prueba de aislamiento en COVERAGE (y nada más)")
			.isEqualTo(new TreeSet<>(operations));
	}

	@TestFactory
	Stream<DynamicTest> elTenantBNoLeeNadaDelTenantAEnNingunGetDelContrato() throws Exception {
		List<DynamicTest> tests = new ArrayList<>();
		for (String operation : operations()) {
			if (!operation.startsWith("GET ") || OPERATOR_ONLY.contains(operation)) {
				continue;
			}
			String path = operation.substring(4);
			// Los ids de A no aparecen en una ruta de B salvo en la lectura por id, que se prueba aparte;
			// los parámetros que no son ids (la fuente de una integración) se fijan a un valor válido.
			String concrete = path.replace("{id}", applicantId).replace("{fuente}", "BURO");
			tests.add(DynamicTest.dynamicTest(operation + " → sin datos del tenant A", () -> {
				var result = mvc.perform(get(concrete).with(as(TENANT_B))).andReturn().getResponse();
				String body = result.getContentAsString();
				if (path.contains("{id}")) {
					assertThat(result.getStatus()).as(operation + " con un id del tenant A").isEqualTo(404);
				}
				List<String> foreign = new ArrayList<>(List.of(TENANT_A, document, productId.toString(),
						policyId.toString(), consentId.toString(), apiKeyId.toString()));
				// El 404 de la lectura por id repite la ruta pedida en "instance"; en el resto, el id no puede salir.
				if (!path.contains("{id}")) {
					foreign.add(applicantId);
				}
				assertThat(body).as(operation).doesNotContain(foreign);
			}));
		}
		assertThat(tests).as("GET recorridos").hasSizeGreaterThan(15);
		return tests.stream();
	}

	@Test
	void elTenantBNoModificaRecursosDelTenantAPorSuId() throws Exception {
		mvc.perform(put("/productos/{id}", productId).with(as(TENANT_B)).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"activo":false,"montoMinimo":"1","montoMaximo":"2","plazoMinimoMeses":1,"plazoMaximoMeses":2}"""))
			.andExpect(status().isNotFound());
		mvc.perform(put("/politicas/{id}", policyId).with(as(TENANT_B)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"activa\": false}"))
			.andExpect(status().isNotFound());
		mvc.perform(post("/consentimientos/{id}/revocar", consentId).with(as(TENANT_B)))
			.andExpect(status().isNotFound());
		mvc.perform(delete("/api-keys/{id}", apiKeyId).with(as(TENANT_B)))
			.andExpect(status().isNotFound());

		asTenant(TENANT_A, () -> {
			assertThat(jdbc.queryForObject("select active from recommendation.products where id = ?", Boolean.class,
					productId)).isTrue();
			assertThat(jdbc.queryForObject("select estado from consent.consents where id = ?", String.class, consentId))
				.isEqualTo("ACTIVO");
			assertThat(jdbc.queryForObject("select count(*) from tenancy.api_keys where id = ? and revocada_en is null",
					Integer.class, apiKeyId)).isEqualTo(1);
			return null;
		});
	}

	@SuppressWarnings("unchecked")
	private static Set<String> operations() throws Exception {
		Map<String, Object> contract;
		try (InputStream in = Files.newInputStream(CONTRACT)) {
			contract = new Yaml().load(in);
		}
		Set<String> operations = new TreeSet<>();
		((Map<String, Map<String, Object>>) contract.get("paths")).forEach((path, methods) -> methods.keySet().stream()
			.filter(Set.of("get", "post", "put", "patch", "delete")::contains)
			.forEach(method -> operations.add(method.toUpperCase() + " " + path)));
		return operations;
	}

	/** Administrador y analista a la vez: así ninguna ruta se salta por el rol. */
	private static RequestPostProcessor as(String tenant) {
		return jwt().jwt(token -> token.subject("meta-" + tenant.substring(tenant.length() - 1)).claim("tenant_id", tenant))
			.authorities(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN"), new SimpleGrantedAuthority("ROLE_ANALYST"));
	}

	private <T> T asTenant(String tenant, Callable<T> work) throws Exception {
		TenantContext.set(UUID.fromString(tenant));
		try {
			return work.call();
		}
		finally {
			TenantContext.clear();
		}
	}

}
