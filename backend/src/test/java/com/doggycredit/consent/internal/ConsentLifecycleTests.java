package com.doggycredit.consent.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.doggycredit.TestcontainersConfiguration;
import com.doggycredit.TestTenants;
import com.doggycredit.consent.ConsentRequiredException;
import com.doggycredit.consent.Source;
import com.doggycredit.integration.BankConnector;
import com.doggycredit.integration.BureauConnector;
import com.doggycredit.integration.BureauReport;
import com.doggycredit.integration.BureauReport.Linaje;
import com.doggycredit.integration.BureauReport.TipoTitular;
import com.doggycredit.integration.ConnectorGateway;
import com.doggycredit.shared.TenantContext;

/** B-07: alcance por fuente, listado por solicitante y revocación con efecto inmediato. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ConsentLifecycleTests {

	private static final String TENANT = "88888888-8888-4888-8888-888888888888";
	private static final String OTRO_TENANT = "99999999-9999-4999-8999-999999999999";
	private static final String PRO_TENANT = "9c7e7c1a-0000-4000-8000-000000000002";

	@Autowired
	MockMvc mvc;

	@Autowired
	ConnectorGateway gateway;

	@Autowired
	PostgreSQLContainer postgres;

	@MockitoBean(name = "bureauConnector")
	BureauConnector bureau;

	@MockitoBean(name = "bankConnector")
	BankConnector bank;

	@Test
	void revocarYLuegoEvaluarResponde422() throws Exception {
		TestTenants.register(postgres, TENANT, "FREE");
		when(bureau.source()).thenReturn(Source.BURO);
		when(bureau.fetch(any(), anyString())).thenReturn(Optional.of(new BureauReport(TipoTitular.PERSONA, 12,
				List.of(), new Linaje(Source.BURO, "v1", "v1", "f", "a".repeat(64), Instant.now()))));
		String document = "CI-REVOCA-" + UUID.randomUUID().toString().substring(0, 8);
		String applicantId = id(mvc.perform(post("/solicitantes").with(as(TENANT))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"documento":"%s","actividad":"Comercio","antiguedadActividadMeses":36,
						 "ingresosMensuales":"5000.00","moneda":"BOB","destinoCredito":"Capital"}
						""".formatted(document)))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
		String consentId = id(mvc.perform(post("/consentimientos").with(as(TENANT))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"titularDocumento":"%s","fuentes":["BURO"],"finalidad":"Evaluación"}
						""".formatted(document)))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

		mvc.perform(get("/consentimientos").param("solicitanteId", applicantId).with(as(TENANT)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].estado").value("ACTIVO"))
			.andExpect(jsonPath("$[0].fuentes[0]").value("BURO"));
		evaluate(document, consentId, "revoca-antes-0001").andExpect(status().isCreated());

		mvc.perform(post("/consentimientos/{id}/revocar", consentId).with(as(TENANT)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.estado").value("REVOCADO"))
			.andExpect(jsonPath("$.revocadoEn").isNotEmpty());
		evaluate(document, consentId, "revoca-despues-0001")
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));

		// Idempotente, y la lista muestra el historial.
		mvc.perform(post("/consentimientos/{id}/revocar", consentId).with(as(TENANT)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.estado").value("REVOCADO"));
		mvc.perform(get("/consentimientos").param("solicitanteId", applicantId).with(as(TENANT)))
			.andExpect(jsonPath("$[0].estado").value("REVOCADO"));

		// Otro tenant: ni lo ve ni lo revoca, y nunca se distingue si existe.
		mvc.perform(post("/consentimientos/{id}/revocar", consentId).with(as(OTRO_TENANT)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("CONSENT_NOT_FOUND"));
		mvc.perform(get("/consentimientos").param("solicitanteId", applicantId).with(as(OTRO_TENANT)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("UNKNOWN_APPLICANT"));
	}

	@Test
	void alcanceParcialSoloBuroNoConsultaElBanco() throws Exception {
		when(bureau.source()).thenReturn(Source.BURO);
		when(bureau.fetch(any(), anyString())).thenReturn(Optional.empty());
		when(bank.source()).thenReturn(Source.BANCO);
		String document = "CI-PARCIAL-" + UUID.randomUUID().toString().substring(0, 8);
		// Tenant PRO de la semilla con el banco activo (B-06): lo que se prueba es el consentimiento.
		mvc.perform(put("/integraciones/BANCO").with(as(PRO_TENANT))
				.contentType(MediaType.APPLICATION_JSON).content("{\"activa\":true}"))
			.andExpect(status().isOk());
		mvc.perform(post("/consentimientos").with(as(PRO_TENANT))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"titularDocumento":"%s","fuentes":["BURO"],"finalidad":"Evaluación"}
						""".formatted(document)))
			.andExpect(status().isCreated());

		TenantContext.set(UUID.fromString(PRO_TENANT));
		try {
			assertThat(gateway.fetch(bureau, UUID.fromString(PRO_TENANT), document)).isEmpty();
			assertThatThrownBy(() -> gateway.fetch(bank, UUID.fromString(PRO_TENANT), document))
				.isInstanceOf(ConsentRequiredException.class);
			verify(bank, never()).fetch(any(), anyString());
		}
		finally {
			TenantContext.clear();
		}
	}

	private org.springframework.test.web.servlet.ResultActions evaluate(String document, String consentId, String key)
			throws Exception {
		return mvc.perform(post("/credit-assessments").with(as(TENANT))
			.header("Idempotency-Key", key)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"titularDocumento":"%s","consentId":"%s","montoSolicitado":"3000.00","plazoMeses":12}
					""".formatted(document, consentId)));
	}

	private static RequestPostProcessor as(String tenant) {
		return jwt().jwt(token -> token.subject("analista-b07").claim("tenant_id", tenant));
	}

	private static String id(String json) {
		return json.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
	}

}
