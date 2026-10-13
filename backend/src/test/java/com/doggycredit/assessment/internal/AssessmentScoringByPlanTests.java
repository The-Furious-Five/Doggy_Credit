package com.doggycredit.assessment.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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
import com.doggycredit.shared.Money;
import com.doggycredit.shared.DocumentNumber;
import com.doggycredit.shared.TenantContext;

/**
 * D-02 — criterio de "Lista cuando": una prueba por plan, de punta a punta por
 * la API. Tenants de la semilla: Progreso Andino es FREE y Fintech Vecina es PRO.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AssessmentScoringByPlanTests {

	private static final String FREE = "9c7e7c1a-0000-4000-8000-000000000001";
	private static final String PRO = "9c7e7c1a-0000-4000-8000-000000000002";
	private final String document = DocumentNumber.normalize("CI-PLAN-" + UUID.randomUUID());

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@MockitoBean(name = "bureauConnector")
	BureauConnector bureau;

	@BeforeEach
	void bureauAnswers() {
		when(bureau.source()).thenReturn(Source.BURO);
		when(bureau.fetch(any(), anyString())).thenReturn(Optional.of(report()));
	}

	@Test
	void planFreeEvaluaConElScorecardBasico() throws Exception {
		UUID consent = prepare(FREE);
		assess(FREE, consent)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.modelVersion").value("basic-v1"));
		assertThat(storedModelVersion(FREE)).isEqualTo("basic-v1");
	}

	@Test
	void planProEvaluaConElModeloOnnxYRegistraSuVersion() throws Exception {
		UUID consent = prepare(PRO);
		assess(PRO, consent)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.modelVersion").value("scoring-v2"))
			.andExpect(jsonPath("$.score").value(org.hamcrest.Matchers.allOf(
					org.hamcrest.Matchers.greaterThanOrEqualTo(0), org.hamcrest.Matchers.lessThanOrEqualTo(1000))))
			.andExpect(jsonPath("$.banda").isNotEmpty());
		assertThat(storedModelVersion(PRO)).isEqualTo("scoring-v2");
	}

	private org.springframework.test.web.servlet.ResultActions assess(String tenant, UUID consent) throws Exception {
		return mvc.perform(post("/credit-assessments")
			.with(jwt().jwt(token -> token.subject("analista-plan").claim("tenant_id", tenant)))
			.header("Idempotency-Key", "plan-" + UUID.randomUUID())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"titularDocumento":"%s","consentId":"%s","proposito":"Capital de trabajo",
					 "montoSolicitado":"3000.00","plazoMeses":12}
					""".formatted(document, consent)));
	}

	private UUID prepare(String tenant) throws Exception {
		mvc.perform(post("/solicitantes")
			.with(jwt().jwt(token -> token.subject("analista-plan").claim("tenant_id", tenant)))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"documento":"%s","actividad":"Comercio","antiguedadActividadMeses":36,
					 "ingresosMensuales":"5000.00","moneda":"BOB","destinoCredito":"Capital"}
					""".formatted(document)))
			.andExpect(status().isCreated());
		UUID consent = UUID.randomUUID();
		comoTenant(tenant, () -> jdbc.update("""
				insert into consent.consents (id, tenant_id, titular_documento, fuentes, finalidad, vigencia_dias,
				  otorgado_en, expira_en, estado, otorgado_por, payload_sha256)
				values (?, ?::uuid, ?, '{BURO}'::text[], 'evaluacion', 90, now(), now() + interval '1 day',
				  'ACTIVO', 'test', 'x')
				""", consent, tenant, document));
		return consent;
	}

	private String storedModelVersion(String tenant) {
		return comoTenant(tenant, () -> jdbc.queryForObject(
				"select model_version from assessment.credit_assessments order by created_at desc limit 1",
				String.class));
	}

	/** SPIKE-1 (RLS): el JDBC directo corre fuera de una petición HTTP. */
	private <T> T comoTenant(String tenant, java.util.function.Supplier<T> work) {
		TenantContext.set(UUID.fromString(tenant));
		try {
			return work.get();
		}
		finally {
			TenantContext.clear();
		}
	}

	private BureauReport report() {
		Money balance = new Money(new BigDecimal("1000.00"), Currency.getInstance("BOB"));
		Money payment = new Money(new BigDecimal("400.00"), Currency.getInstance("BOB"));
		return new BureauReport(TipoTitular.PERSONA, 48,
				List.of(new Endeudamiento(Source.BURO, "MICROCREDITO", balance, payment, 0, "A",
						LocalDate.of(2026, 9, 1))),
				new Linaje(Source.BURO, "v1", "v1", "fetch-1", "a".repeat(64), Instant.now()));
	}

}
