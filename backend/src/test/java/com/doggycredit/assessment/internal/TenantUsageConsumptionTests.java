package com.doggycredit.assessment.internal;

import static org.assertj.core.api.Assertions.assertThat;
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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
 * B-12 (RF-ANA-01): GET /tenant/actual/uso coincide con el contador de consumo.
 * Tres evaluaciones, una repetida con la misma Idempotency-Key y un CONSENT_REQUIRED
 * suman 3. Tenants de la semilla: Progreso Andino es FREE y Fintech Vecina es PRO.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class TenantUsageConsumptionTests {

	private static final String FREE = "9c7e7c1a-0000-4000-8000-000000000001";
	private static final String PRO = "9c7e7c1a-0000-4000-8000-000000000002";
	private final String document = DocumentNumber.normalize("CI-USO-" + UUID.randomUUID());

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
	void repeticionIdempotenteYConsentRequiredNoConsumen() throws Exception {
		int before = usage(FREE, "ANALYST").evaluacionesMes;
		int proBefore = usage(PRO, "TENANT_ADMIN").evaluacionesMes;
		UUID consent = prepare(FREE);
		String repeated = "uso-" + UUID.randomUUID();
		assess(FREE, consent, repeated).andExpect(status().isCreated());
		assess(FREE, consent, "uso-" + UUID.randomUUID()).andExpect(status().isCreated());
		assess(FREE, consent, "uso-" + UUID.randomUUID()).andExpect(status().isCreated());
		assess(FREE, consent, repeated).andExpect(status().isCreated());
		assess(FREE, UUID.randomUUID(), "uso-" + UUID.randomUUID())
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));

		var uso = usage(FREE, "TENANT_ADMIN");
		assertThat(uso.evaluacionesMes).isEqualTo(before + 3);
		assertThat(uso.limiteMensual).isEqualTo(500);
		// Aislamiento: el consumo de otro tenant no cambia; el buró siempre cuenta como activa.
		assertThat(usage(PRO, "TENANT_ADMIN").evaluacionesMes).isEqualTo(proBefore);
		assertThat(uso.integracionesActivas).isGreaterThanOrEqualTo(1);
		assertThat(uso.limiteIntegraciones).isPositive();
	}

	private record Uso(int evaluacionesMes, int limiteMensual, int integracionesActivas, int limiteIntegraciones) {
	}

	/** Cualquier rol del tenant puede consultar el uso. */
	private Uso usage(String tenant, String role) throws Exception {
		String json = mvc.perform(get("/tenant/actual/uso")
				.with(jwt().jwt(token -> token.subject("uso").claim("tenant_id", tenant))
					.authorities(new SimpleGrantedAuthority("ROLE_" + role))))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
		return new Uso(node.get("evaluacionesMes").asInt(), node.get("limiteMensual").asInt(),
				node.path("integracionesActivas").asInt(-1), node.path("limiteIntegraciones").asInt(-1));
	}

	private org.springframework.test.web.servlet.ResultActions assess(String tenant, UUID consent, String key)
			throws Exception {
		return mvc.perform(post("/credit-assessments")
			.with(jwt().jwt(token -> token.subject("analista-uso").claim("tenant_id", tenant)))
			.header("Idempotency-Key", key)
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
