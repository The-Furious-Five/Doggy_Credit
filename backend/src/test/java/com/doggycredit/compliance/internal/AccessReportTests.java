package com.doggycredit.compliance.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.doggycredit.TestcontainersConfiguration;
import com.doggycredit.consent.Source;
import com.doggycredit.integration.BureauConnector;
import com.doggycredit.integration.BureauReport;
import com.doggycredit.integration.BureauReport.Endeudamiento;
import com.doggycredit.integration.BureauReport.Linaje;
import com.doggycredit.integration.BureauReport.TipoTitular;
import com.doggycredit.shared.Money;

/**
 * C-11 (LOS-112), RF-CON-03: reporte de accesos de punta a punta por la API, contra
 * PostgreSQL real. Cada test registra un titular nuevo y recorre el camino real: alta,
 * consentimiento, evaluación y lecturas; después pide el reporte como TENANT_ADMIN.
 * Tenants de la semilla: Progreso Andino (A) y Fintech Vecina (B).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AccessReportTests {

	private static final String TENANT_A = "9c7e7c1a-0000-4000-8000-000000000001";
	private static final String TENANT_B = "9c7e7c1a-0000-4000-8000-000000000002";

	@Autowired
	MockMvc mvc;

	@MockitoBean(name = "bureauConnector")
	BureauConnector bureau;

	@BeforeEach
	void bureauAnswers() {
		when(bureau.source()).thenReturn(Source.BURO);
		when(bureau.fetch(any(), anyString())).thenReturn(Optional.of(report()));
	}

	@Test
	void porTitularMuestraQuienAccedioYConQueConsentimiento() throws Exception {
		Titular t = recorridoCompleto(TENANT_A);

		reporte(TENANT_A, "solicitanteId", t.applicantId().toString())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.solicitanteId").value(t.applicantId().toString()))
			.andExpect(jsonPath("$.generadoEn").isNotEmpty())
			.andExpect(jsonPath("$.items[*].accion").value(hasItems("APPLICANT_CREATED", "CONSENT_GRANTED",
					"FINANCIAL_PROFILE_CREATED", "ASSESSMENT_CREATED", "ASSESSMENT_READ", "APPLICANT_READ")))
			.andExpect(jsonPath("$.items[*].solicitanteId").value(everyItem(is(t.applicantId().toString()))))
			// La evaluación se hizo con el consentimiento otorgado: esa es la «autorización».
			.andExpect(jsonPath("$.items[?(@.accion == 'ASSESSMENT_CREATED')].consentimientoId")
				.value(hasItem(t.consentId().toString())))
			.andExpect(jsonPath("$.autorizaciones[*].consentimientoId").value(hasItem(t.consentId().toString())))
			.andExpect(jsonPath("$.autorizaciones[0].solicitanteId").value(t.applicantId().toString()))
			.andExpect(jsonPath("$.autorizaciones[0].fuentes").value(containsInAnyOrder("BURO")))
			.andExpect(jsonPath("$.autorizaciones[0].estado").value("ACTIVO"))
			// Sin datos personales: el titular va por id, nunca su documento.
			.andExpect(result -> assertThat(result.getResponse().getContentAsString()).doesNotContain(t.document()));
	}

	@Test
	void porCorrelationIdMuestraSoloLoQueHizoEsaPeticion() throws Exception {
		Titular t = recorridoCompleto(TENANT_A);

		reporte(TENANT_A, "correlationId", t.assessmentCorrelationId())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.correlationId").value(t.assessmentCorrelationId()))
			.andExpect(jsonPath("$.items[*].correlationId").value(everyItem(is(t.assessmentCorrelationId()))))
			.andExpect(jsonPath("$.items[*].accion").value(hasItem("ASSESSMENT_CREATED")))
			.andExpect(jsonPath("$.items[*].accion").value(org.hamcrest.Matchers.not(hasItem("APPLICANT_READ"))));
	}

	@Test
	void generarElReporteTambienQuedaEnLaBitacora() throws Exception {
		Titular t = recorridoCompleto(TENANT_A);

		reporte(TENANT_A, "solicitanteId", t.applicantId().toString()).andExpect(status().isOk());
		reporte(TENANT_A, "solicitanteId", t.applicantId().toString())
			.andExpect(jsonPath("$.items[0].accion").value("ACCESS_REPORT_GENERATED"));
	}

	@Test
	void paginaPorCursorSinRepetirRegistros() throws Exception {
		Titular t = recorridoCompleto(TENANT_A);

		MvcResult primera = mvc.perform(get("/auditoria/accesos").with(token(TENANT_A, "TENANT_ADMIN"))
			.param("solicitanteId", t.applicantId().toString()).param("tamano", "2"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(2))
			.andExpect(jsonPath("$.siguienteCursor").isNumber())
			.andReturn();
		String body = primera.getResponse().getContentAsString();
		long cursor = Long.parseLong(body.replaceAll(".*\"siguienteCursor\":(\\d+).*", "$1"));

		mvc.perform(get("/auditoria/accesos").with(token(TENANT_A, "TENANT_ADMIN"))
			.param("solicitanteId", t.applicantId().toString()).param("tamano", "2")
			.param("cursor", String.valueOf(cursor)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].secuencia").value(everyItem(org.hamcrest.Matchers.lessThan((int) cursor))));
	}

	@Test
	void sinTitularNiCorrelationIdResponde400() throws Exception {
		mvc.perform(get("/auditoria/accesos").with(token(TENANT_A, "TENANT_ADMIN")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void unTitularDeOtroTenantResponde404() throws Exception {
		Titular t = recorridoCompleto(TENANT_A);

		reporte(TENANT_B, "solicitanteId", t.applicantId().toString())
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("UNKNOWN_APPLICANT"));
	}

	@Test
	void otroTenantNoVeLoQueHizoUnaPeticionAjena() throws Exception {
		Titular t = recorridoCompleto(TENANT_A);

		reporte(TENANT_B, "correlationId", t.assessmentCorrelationId())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items").isEmpty())
			.andExpect(jsonPath("$.autorizaciones").isEmpty());
	}

	@Test
	void soloTenantAdmin() throws Exception {
		Titular t = recorridoCompleto(TENANT_A);

		mvc.perform(get("/auditoria/accesos").with(token(TENANT_A, "ANALYST"))
			.param("solicitanteId", t.applicantId().toString()))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("INSUFFICIENT_ROLE"));
	}

	// --- utilidades ------------------------------------------------------------

	record Titular(String document, UUID applicantId, UUID consentId, String assessmentCorrelationId) {
	}

	/** Alta, consentimiento, evaluación y lecturas: todo por la API, como en la consola. */
	private Titular recorridoCompleto(String tenant) throws Exception {
		String document = "CI-ACC-" + ThreadLocalRandom.current().nextInt(1_000_000, 9_999_999);
		UUID applicantId = idOf(mvc.perform(post("/solicitantes").with(token(tenant, "ANALYST"))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"documento":"%s","actividad":"Comercio","antiguedadActividadMeses":36,
					 "ingresosMensuales":"5000.00","moneda":"BOB","destinoCredito":"Capital"}
					""".formatted(document)))
			.andExpect(status().isCreated()).andReturn());
		UUID consentId = idOf(mvc.perform(post("/consentimientos").with(token(tenant, "ANALYST"))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"titularDocumento":"%s","fuentes":["BURO"],"finalidad":"Evaluación de crédito"}
					""".formatted(document)))
			.andExpect(status().isCreated()).andReturn());
		MvcResult assessment = mvc.perform(post("/credit-assessments").with(token(tenant, "ANALYST"))
			.header("Idempotency-Key", "acc-" + UUID.randomUUID())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"titularDocumento":"%s","consentId":"%s","proposito":"Capital de trabajo",
					 "montoSolicitado":"3000.00","plazoMeses":12}
					""".formatted(document, consentId)))
			.andExpect(status().isCreated()).andReturn();
		UUID assessmentId = idOf(assessment);
		mvc.perform(get("/credit-assessments/{id}", assessmentId).with(token(tenant, "ANALYST")))
			.andExpect(status().isOk());
		mvc.perform(get("/solicitantes/{id}", applicantId).with(token(tenant, "ANALYST")))
			.andExpect(status().isOk());
		return new Titular(document, applicantId, consentId, assessment.getResponse().getHeader("X-Correlation-Id"));
	}

	private ResultActions reporte(String tenant, String filtro, String valor) throws Exception {
		return mvc.perform(get("/auditoria/accesos").with(token(tenant, "TENANT_ADMIN")).param(filtro, valor));
	}

	private static UUID idOf(MvcResult result) throws Exception {
		return UUID.fromString(result.getResponse().getContentAsString().replaceAll(".*?\"id\":\"([^\"]+)\".*", "$1"));
	}

	private static RequestPostProcessor token(String tenant, String rol) {
		return jwt().jwt(t -> t.subject("usuario-" + rol).claim("tenant_id", tenant))
			.authorities(new SimpleGrantedAuthority("ROLE_" + rol));
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
