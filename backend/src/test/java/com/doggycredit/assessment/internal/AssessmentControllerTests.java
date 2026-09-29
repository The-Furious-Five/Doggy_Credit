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

import com.doggycredit.TestcontainersConfiguration;
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

	@MockitoBean(name = "bureauConnector")
	BureauConnector bureau;

	@BeforeEach
	void configureBureauAndData() throws Exception {
		when(bureau.source()).thenReturn(Source.BURO);
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
		createAssessment(key, DOCUMENT)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").value(id.toString()));
		verify(bureau).fetch(UUID.fromString(TENANT), DOCUMENT);

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
	void caidaDeFuenteProduceParcial() throws Exception {
		when(bureau.fetch(any(), anyString()))
			.thenThrow(new SourceUnavailableException(Source.BURO, "simulada", null));
		createAssessment("assessment-failure-0001", DOCUMENT)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.estado").value("PARCIAL"));
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
