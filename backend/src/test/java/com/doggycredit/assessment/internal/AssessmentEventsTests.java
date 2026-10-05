package com.doggycredit.assessment.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.modulith.test.EnableScenarios;
import org.springframework.modulith.test.Scenario;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.doggycredit.TestcontainersConfiguration;
import com.doggycredit.assessment.AssessmentCommand;
import com.doggycredit.assessment.AssessmentOperations;
import com.doggycredit.assessment.CreditAssessmentCompleted;
import com.doggycredit.consent.Source;
import com.doggycredit.integration.BureauConnector;
import com.doggycredit.integration.BureauReport;
import com.doggycredit.integration.BureauReport.Endeudamiento;
import com.doggycredit.integration.BureauReport.Linaje;
import com.doggycredit.integration.BureauReport.TipoTitular;
import com.doggycredit.shared.Money;
import com.doggycredit.shared.TenantContext;

/** D-03: terminar una evaluación publica {@link CreditAssessmentCompleted} en el outbox. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@EnableScenarios
class AssessmentEventsTests {

	private static final UUID TENANT = UUID.fromString("77777777-7777-4777-8777-777777777777");
	private static final String DOCUMENT = "CI-EVENT-0002";
	private static final UUID CONSENT_ID = UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc");

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	AssessmentOperations assessments;

	@MockitoBean(name = "bureauConnector")
	BureauConnector bureau;

	@BeforeEach
	void applicantAndConsent() throws Exception {
		when(bureau.source()).thenReturn(Source.BURO);
		if (comoTenant(() -> jdbc.queryForObject("select count(*) from customer.applicants where tenant_id = ?",
				Integer.class, TENANT)) == 0) {
			mvc.perform(post("/solicitantes")
				.with(jwt().jwt(token -> token.subject("analista-evento").claim("tenant_id", TENANT.toString())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"documento":"%s","actividad":"Comercio","antiguedadActividadMeses":36,
						 "ingresosMensuales":"5000.00","moneda":"BOB","destinoCredito":"Capital"}
						""".formatted(DOCUMENT)))
				.andExpect(status().isCreated());
		}
		comoTenant(() -> jdbc.update("""
				insert into consent.consents (id, tenant_id, titular_documento, fuentes, finalidad, vigencia_dias,
				  otorgado_en, expira_en, estado, otorgado_por, payload_sha256)
				values (?, ?, ?, '{BURO}'::text[], 'evaluacion', 90, now(), now() + interval '1 day',
				  'ACTIVO', 'test', 'x')
				on conflict do nothing
				""", CONSENT_ID, TENANT, DOCUMENT));
	}

	@Test
	void terminarEvaluacionPublicaCreditAssessmentCompleted(Scenario scenario) {
		when(bureau.fetch(any(), anyString())).thenReturn(Optional.of(report()));
		AssessmentCommand command = new AssessmentCommand(TENANT, DOCUMENT, CONSENT_ID, "Capital de trabajo",
				new BigDecimal("3000.00"), 12, "assessment-event-" + UUID.randomUUID(), "corr-evento-1");

		comoTenant(() -> {
			scenario.stimulate(() -> assessments.create(command))
				.andWaitForEventOfType(CreditAssessmentCompleted.class)
				.matching(event -> event.correlationId().equals("corr-evento-1"))
				.toArriveAndVerify((event, result) -> {
					assertThat(event.assessmentId()).isEqualTo(result.id());
					assertThat(event.tenantId()).isEqualTo(TENANT);
					assertThat(event.applicantId()).isNotNull();
					assertThat(event.estado()).isEqualTo(result.status());
					assertThat(event.score()).isEqualTo(result.score());
					assertThat(event.banda()).isEqualTo(result.band());
					assertThat(event.razones()).isEqualTo(result.reasons());
					assertThat(event.modelVersion()).isEqualTo(result.modelVersion());
					assertThat(event.simulado()).isTrue();
				});
			return null;
		});
	}

	/** SPIKE-1 (RLS): fuera de una petición HTTP nadie fija el tenant. */
	private <T> T comoTenant(java.util.function.Supplier<T> work) {
		TenantContext.set(TENANT);
		try {
			return work.get();
		}
		finally {
			TenantContext.clear();
		}
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
