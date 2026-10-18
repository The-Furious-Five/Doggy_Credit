package com.doggycredit.assessment.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.doggycredit.TestcontainersConfiguration;
import com.doggycredit.TestTenants;
import com.doggycredit.consent.Source;
import com.doggycredit.integration.BureauConnector;
import com.doggycredit.integration.BureauReport;
import com.doggycredit.integration.BureauReport.Endeudamiento;
import com.doggycredit.integration.BureauReport.Linaje;
import com.doggycredit.integration.BureauReport.TipoTitular;
import com.doggycredit.integration.SourceUnavailableException;
import com.doggycredit.shared.Money;
import com.doggycredit.shared.TenantContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "doggycredit.assessment.collection-budget=100ms")
@AutoConfigureMockMvc
class AssessmentControllerTests {

	private static final String TENANT = "44444444-4444-4444-8444-444444444444";
	private static final String DOCUMENT = "CI-ASSESS-0001";
	private static final UUID CONSENT_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PostgreSQLContainer postgres;

	@MockitoBean(name = "bureauConnector")
	BureauConnector bureau;

	@BeforeEach
	void configureBureauAndData() throws Exception {
		when(bureau.source()).thenReturn(Source.BURO);
		TestTenants.register(postgres, TENANT, "FREE");
		try (var admin = java.sql.DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
				var seed = admin.prepareStatement("""
					INSERT INTO recommendation.products (id, tenant_id, product_type, min_amount, max_amount,
					  min_term_months, max_term_months, annual_rate, min_band)
					VALUES (?, ?::uuid, 'MICROCREDITO', 1000, 30000, 6, 36, 11.5, 'C')
					ON CONFLICT (tenant_id, product_type) DO NOTHING
					""")) {
			seed.setObject(1, UUID.randomUUID());
			seed.setString(2, TENANT);
			seed.executeUpdate();
		}
		registerApplicant();
		comoTenant(() -> jdbc.update("""
				insert into consent.consents (id, tenant_id, titular_documento, fuentes, finalidad, vigencia_dias,
				  otorgado_en, expira_en, estado, otorgado_por, payload_sha256)
				values (?, ?::uuid, ?, '{BURO}'::text[], 'evaluacion', 90, now(), now() + interval '1 day',
				  'ACTIVO', 'test', 'x')
				on conflict do nothing
				""", CONSENT_ID, TENANT, DOCUMENT));
	}

	/**
	 * SPIKE-1 (RLS): el JDBC directo de esta prueba corre fuera de una petición
	 * HTTP, así que nadie fija el tenant; sin esto la política lo filtra todo
	 * (conteos en 0, INSERT rechazado). Se envuelve cada llamada por separado
	 * porque TenantClaimFilter limpia el contexto al terminar cada mvc.perform.
	 */
	private <T> T comoTenant(java.util.function.Supplier<T> work) {
		TenantContext.set(UUID.fromString(TENANT));
		try {
			return work.get();
		}
		finally {
			TenantContext.clear();
		}
	}

	@Test
	void orquestaScorePersisteYReutilizaLaIdempotencia() throws Exception {
		when(bureau.fetch(any(), anyString())).thenReturn(Optional.of(report()));
		String key = "assessment-happy-0001";
		String response = createAssessment(key, DOCUMENT)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.estado").value("REVISION_MANUAL"))
			.andExpect(jsonPath("$.score").isNumber())
			.andExpect(jsonPath("$.banda").value("A"))
			.andExpect(jsonPath("$.razones.length()").value(6))
			.andExpect(jsonPath("$.modelVersion").value("basic-v1"))
			.andReturn().getResponse().getContentAsString();

		UUID id = UUID.fromString(response.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1"));
		expectBureauInProfile("COMPLETA");
		int snapshots = profileSnapshots();
		createAssessment(key, DOCUMENT)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").value(id.toString()));
		verify(bureau).fetch(UUID.fromString(TENANT), DOCUMENT);
		// La repetición idempotente no vuelve a consultar ni consolida otro perfil.
		assertThat(profileSnapshots()).isEqualTo(snapshots);
		assertThat(comoTenant(() -> jdbc.queryForObject(
				"select need_category from assessment.credit_assessments where id = ?", String.class, id)))
				.isEqualTo("CAPITAL_TRABAJO");
		assertThat(comoTenant(() -> jdbc.queryForObject(
				"select decision_time_ms from assessment.credit_assessments where id = ?", Long.class, id)))
				.isNotNegative();
		// A-06: este tenant es FREE, que no incluye recomendaciones (el camino PRO está en
		// AssessmentRecommendationsTests). Antes esperaba MICROCREDITO y pasaba o fallaba según el orden.
		assertThat(comoTenant(() -> jdbc.queryForObject(
				"select recommended_product_type from assessment.credit_assessments where id = ?", String.class, id)))
				.isNull();
		assertThat(comoTenant(() -> jdbc.queryForObject(
				"select recommendations_evaluated from assessment.credit_assessments where id = ?", Boolean.class, id)))
				.isFalse();
		assertThat(comoTenant(() -> jdbc.queryForObject(
				"select count(*) from assessment.credit_assessments where idempotency_key = ?", Integer.class, key)))
				.isEqualTo(1);

		mvc.perform(get("/credit-assessments/{id}", id)
				.with(jwt().jwt(token -> token.subject("analista-3").claim("tenant_id", TENANT))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(id.toString()));
		mvc.perform(get("/credit-assessments/{id}", id)
				.with(jwt().jwt(token -> token.subject("otro").claim("tenant_id",
						"55555555-5555-4555-8555-555555555555"))))
			.andExpect(status().isNotFound());
	}

	@Test
	void timeoutDeFuenteProduceParcialYNuncaPreaprobado() throws Exception {
		when(bureau.fetch(any(), anyString())).thenAnswer(invocation -> {
			Thread.sleep(Duration.ofSeconds(1));
			return Optional.of(report());
		});
		long started = System.nanoTime();
		createAssessment("assessment-timeout-0001", DOCUMENT)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.estado").value("PARCIAL"));
		assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(1));
	}

	@Test
	void conLaReglaDePreaprobacionActivaElTitularSolidoQuedaPreaprobado() throws Exception {
		// C-05: la evaluación aplica las reglas activas del tenant.
		when(bureau.fetch(any(), anyString())).thenReturn(Optional.of(report()));
		withRule("PREAPROBACION_SCORE", "PREAPROBADO", () -> createAssessment("assessment-policy-0001", DOCUMENT)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.banda").value("A"))
			.andExpect(jsonPath("$.estado").value("PREAPROBADO"))
			.andExpect(jsonPath("$.decision").value("PREAPROBADO")));
	}

	@Test
	void conLaReglaDePreaprobacionActivaUnResultadoParcialNuncaSePreaprueba() throws Exception {
		when(bureau.fetch(any(), anyString()))
			.thenThrow(new SourceUnavailableException(Source.BURO, "simulada", null));
		withRule("PREAPROBACION_SCORE", "PREAPROBADO", () -> createAssessment("assessment-policy-0002", DOCUMENT)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.estado").value("PARCIAL"))
			.andExpect(jsonPath("$.decision").value("REVISION_MANUAL")));
	}

	@Test
	void conDatosInsuficientesPideDatosYElEstadoPublicadoEsRevisionManual() throws Exception {
		// Solo el buró está consentido: 1 fuente respondió (< 2). El enum `estado` del
		// contrato v1 no incluye SOLICITAR_DATOS, así que la decisión va en `decision`.
		when(bureau.fetch(any(), anyString())).thenReturn(Optional.of(report()));
		withRule("DATOS_INSUFICIENTES", "SOLICITAR_DATOS", () -> createAssessment("assessment-policy-0003", DOCUMENT)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.estado").value("REVISION_MANUAL"))
			.andExpect(jsonPath("$.decision").value("SOLICITAR_DATOS")));
	}

	/** Activa una regla para el tenant de la prueba y la borra al terminar (la app no tiene INSERT). */
	private void withRule(String code, String effect, ThrowingRunnable body) throws Exception {
		try (var admin = java.sql.DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
				postgres.getPassword())) {
			try (var insert = admin.prepareStatement("""
					INSERT INTO assessment.policies (id, tenant_id, code, name, condition_text, effect, active)
					VALUES (gen_random_uuid(), ?::uuid, ?, 'regla de prueba', 'x', ?, true)
					""")) {
				insert.setString(1, TENANT);
				insert.setString(2, code);
				insert.setString(3, effect);
				insert.executeUpdate();
			}
			try {
				body.run();
			}
			finally {
				try (var delete = admin.prepareStatement(
						"DELETE FROM assessment.policies WHERE tenant_id = ?::uuid AND code = ?")) {
					delete.setString(1, TENANT);
					delete.setString(2, code);
					delete.executeUpdate();
				}
			}
		}
	}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Exception;
	}

	@Test
	void caidaDeFuenteProduceParcial() throws Exception {
		when(bureau.fetch(any(), anyString()))
			.thenThrow(new SourceUnavailableException(Source.BURO, "simulada", null));
		createAssessment("assessment-failure-0001", DOCUMENT)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.estado").value("PARCIAL"))
			// Demo, punto 6: PARCIAL y además nada inventado dentro del score (RN-15).
			.andExpect(jsonPath("$.razones[0]").value("MORA_DESCONOCIDA_BURO_NO_DISPONIBLE"))
			.andExpect(jsonPath("$.razones[?(@ == 'SIN_MORA_12M')]").isEmpty())
			// Antigüedad y registro tampoco se inventan: desconocidos, no "0 meses" ni "sin registro".
			.andExpect(jsonPath("$.razones[?(@ == 'HISTORIAL_DESCONOCIDO_BURO_NO_DISPONIBLE')]").isNotEmpty())
			.andExpect(jsonPath("$.razones[?(@ == 'REGISTRO_DESCONOCIDO_BURO_NO_DISPONIBLE')]").isNotEmpty())
			.andExpect(jsonPath("$.razones[?(@ == 'HISTORIAL_MENOR_6_MESES')]").isEmpty())
			.andExpect(jsonPath("$.razones[?(@ == 'SIN_REGISTRO_BURO')]").isEmpty())
			.andExpect(jsonPath("$.banda").value(org.hamcrest.Matchers.not("A")));
		expectBureauInProfile("NO_DISPONIBLE");
	}

	@Test
	void sinRegistroEnElBuroEsRespuestaValidaNoMoraDesconocida() throws Exception {
		// El buró respondió "documento no encontrado": no es una caída ni un motivo de
		// exclusión (informe de arquitectura). Mora 0 de verdad, no −350 por desconocida.
		when(bureau.fetch(any(), anyString())).thenReturn(Optional.empty());
		createAssessment("assessment-no-record-0001", DOCUMENT)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.estado").value("REVISION_MANUAL"))
			.andExpect(jsonPath("$.razones[?(@ == 'MORA_DESCONOCIDA_BURO_NO_DISPONIBLE')]").isEmpty())
			.andExpect(jsonPath("$.razones[?(@ == 'SIN_MORA_12M')]").isNotEmpty())
			.andExpect(jsonPath("$.razones[?(@ == 'SIN_REGISTRO_BURO')]").isNotEmpty())
			.andExpect(jsonPath("$.razones[?(@ == 'REGISTRO_DESCONOCIDO_BURO_NO_DISPONIBLE')]").isEmpty())
			.andExpect(jsonPath("$.razones[?(@ == 'HISTORIAL_DESCONOCIDO_BURO_NO_DISPONIBLE')]").isEmpty());
		expectBureauInProfile("SIN_REGISTRO");
	}

	@Test
	void consentimientoDistintoNoPermiteConsultarLaFuente() throws Exception {
		mvc.perform(post("/credit-assessments")
			.with(jwt().jwt(token -> token.subject("analista-3").claim("tenant_id", TENANT)))
			.header("Idempotency-Key", "assessment-wrong-consent")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"titularDocumento":"%s","consentId":"bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"}
					""".formatted(DOCUMENT)))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));
		verify(bureau, never()).fetch(any(), anyString());
	}

	/** B-04: la evaluación deja consolidado el Perfil 360 que muestra el detalle del solicitante. */
	private void expectBureauInProfile(String state) throws Exception {
		UUID applicantId = comoTenant(() -> jdbc.queryForObject(
				"select id from customer.applicants where tenant_id = ?::uuid", UUID.class, TENANT));
		mvc.perform(get("/solicitantes/{id}", applicantId)
				.with(jwt().jwt(token -> token.subject("analista-3").claim("tenant_id", TENANT))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.perfil.fuentes[?(@.fuente == 'BURO')].estado").value(state));
	}

	private int profileSnapshots() {
		return comoTenant(() -> jdbc.queryForObject(
				"select count(*) from customer.financial_profiles where tenant_id = ?::uuid", Integer.class, TENANT));
	}

	private org.springframework.test.web.servlet.ResultActions createAssessment(String key, String document)
			throws Exception {
		return mvc.perform(post("/credit-assessments")
			.with(jwt().jwt(token -> token.subject("analista-3").claim("tenant_id", TENANT)))
			.header("Idempotency-Key", key)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"titularDocumento":"%s","consentId":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
					 "proposito":"Capital de trabajo","montoSolicitado":"3000.00","plazoMeses":12}
					""".formatted(document)));
	}

	private void registerApplicant() throws Exception {
		if (comoTenant(() -> jdbc.queryForObject("select count(*) from customer.applicants where tenant_id = ?::uuid",
				Integer.class, TENANT)) > 0) {
			return;
		}
		mvc.perform(post("/solicitantes")
			.with(jwt().jwt(token -> token.subject("analista-3").claim("tenant_id", TENANT)))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"documento":"%s","actividad":"Comercio","antiguedadActividadMeses":36,
					 "ingresosMensuales":"5000.00","moneda":"BOB","destinoCredito":"Capital"}
					""".formatted(DOCUMENT)))
			.andExpect(status().isCreated());
	}

	private BureauReport report() {
		Money balance = new Money(new BigDecimal("1000.00"), Currency.getInstance("BOB"));
		Money payment = new Money(new BigDecimal("100.00"), Currency.getInstance("BOB"));
		return new BureauReport(TipoTitular.PERSONA, 48,
				List.of(new Endeudamiento(Source.BURO, "MICROCREDITO", balance, payment, 0, "A",
						LocalDate.of(2026, 9, 1))),
				new Linaje(Source.BURO, "v1", "v1", "fetch-1", "a".repeat(64), Instant.now()));
	}

}
