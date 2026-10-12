package com.doggycredit.assessment.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.doggycredit.TestcontainersConfiguration;
import com.doggycredit.consent.Source;
import com.doggycredit.integration.BureauConnector;
import com.doggycredit.integration.SourceUnavailableException;
import com.doggycredit.shared.TenantContext;

/**
 * C-10 (LOS-111), RF-DEC-03: bandeja de revisión manual de punta a punta por la API,
 * contra PostgreSQL real. Los casos se crean con el buró caído: un resultado PARCIAL
 * siempre necesita a una persona (RN-15), sin depender de las políticas del tenant.
 * Tenants de la semilla: Progreso Andino (A) y Fintech Vecina (B).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ManualReviewTests {

	private static final String TENANT_A = "9c7e7c1a-0000-4000-8000-000000000001";
	private static final String TENANT_B = "9c7e7c1a-0000-4000-8000-000000000002";
	private static final String DOCUMENT = "CI-BANDEJA-0001";
	private static final String JUSTIFICACION = "Ingresos verificados con boletas de pago del último trimestre";

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@MockitoBean(name = "bureauConnector")
	BureauConnector bureau;

	@BeforeEach
	void bureauDown() {
		when(bureau.source()).thenReturn(Source.BURO);
		when(bureau.fetch(any(), anyString())).thenThrow(new SourceUnavailableException(Source.BURO, "simulada", null));
	}

	@Test
	void elAnalistaVeElCasoParcialConLosProductosQuePuedeElegir() throws Exception {
		UUID caso = casoParcial(TENANT_A);

		bandeja(TENANT_A, "ANALYST")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[?(@.evaluacionId == '%s')].resultadoParcial".formatted(caso)).value(true))
			.andExpect(jsonPath("$.items[?(@.evaluacionId == '%s')].motivo".formatted(caso)).value("REVISION_MANUAL"))
			.andExpect(jsonPath("$.items[?(@.evaluacionId == '%s')].documento".formatted(caso)).value(DOCUMENT))
			.andExpect(jsonPath("$.items[?(@.evaluacionId == '%s')].razones[*]".formatted(caso)).isNotEmpty())
			.andExpect(jsonPath("$.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
			.andExpect(jsonPath("$.productos").isNotEmpty())
			.andExpect(jsonPath("$.productos[*].activo").value(not(hasItem(false))));
	}

	@Test
	void aprobarConProductoYJustificacionSacaElCasoDeLaBandejaYQuedaAuditado() throws Exception {
		UUID caso = casoParcial(TENANT_A);
		UUID producto = productoActivo(TENANT_A);

		MvcResult result = resolver(TENANT_A, "ANALYST", caso, """
				{"decision":"APROBADO","productoId":"%s","justificacion":"%s"}""".formatted(producto, JUSTIFICACION))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.evaluacionId").value(caso.toString()))
			.andExpect(jsonPath("$.decision").value("APROBADO"))
			.andExpect(jsonPath("$.productoId").value(producto.toString()))
			.andExpect(jsonPath("$.justificacion").value(JUSTIFICACION))
			.andExpect(jsonPath("$.resueltoPor").value("ana.analista"))
			.andExpect(jsonPath("$.resueltoEn").isNotEmpty())
			.andReturn();

		bandeja(TENANT_A, "ANALYST")
			.andExpect(jsonPath("$.items[*].evaluacionId").value(not(hasItem(caso.toString()))));

		// El mismo X-Correlation-Id en la respuesta y en la auditoría.
		String correlationId = result.getResponse().getHeader("X-Correlation-Id");
		List<Map<String, Object>> auditoria = comoTenant(TENANT_A, () -> jdbc.queryForList(
				"select correlation_id from audit.access_log where action = 'MANUAL_REVIEW_RESOLVED' and resource_id = ?",
				caso));
		assertThat(auditoria).hasSize(1);
		assertThat(auditoria.get(0).get("correlation_id")).isEqualTo(correlationId);
	}

	@Test
	void rechazarNoLlevaProducto() throws Exception {
		UUID caso = casoParcial(TENANT_A);

		resolver(TENANT_A, "TENANT_ADMIN", caso, """
				{"decision":"RECHAZADO","justificacion":"%s"}""".formatted(JUSTIFICACION))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.decision").value("RECHAZADO"))
			.andExpect(jsonPath("$.productoId").doesNotExist());
	}

	@Test
	void sinJustificacionResponde400() throws Exception {
		UUID caso = casoParcial(TENANT_A);

		resolver(TENANT_A, "ANALYST", caso, """
				{"decision":"RECHAZADO"}""")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		// Diez espacios pasan el minLength del contrato, pero no son una justificación.
		resolver(TENANT_A, "ANALYST", caso, """
				{"decision":"RECHAZADO","justificacion":"          "}""")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void aprobarSinProductoResponde400() throws Exception {
		UUID caso = casoParcial(TENANT_A);

		resolver(TENANT_A, "ANALYST", caso, """
				{"decision":"APROBADO","justificacion":"%s"}""".formatted(JUSTIFICACION))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void unProductoInexistenteResponde404() throws Exception {
		UUID caso = casoParcial(TENANT_A);

		resolver(TENANT_A, "ANALYST", caso, """
				{"decision":"APROBADO","productoId":"%s","justificacion":"%s"}""".formatted(UUID.randomUUID(),
				JUSTIFICACION))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
	}

	@Test
	void unCasoYaResueltoResponde409() throws Exception {
		UUID caso = casoParcial(TENANT_A);
		String rechazo = """
				{"decision":"RECHAZADO","justificacion":"%s"}""".formatted(JUSTIFICACION);

		resolver(TENANT_A, "ANALYST", caso, rechazo).andExpect(status().isOk());
		resolver(TENANT_A, "TENANT_ADMIN", caso, rechazo)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CONCURRENT_UPDATE"));
	}

	@Test
	void otroTenantNoVeNiResuelveElCaso() throws Exception {
		UUID caso = casoParcial(TENANT_A);

		bandeja(TENANT_B, "ANALYST")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].evaluacionId").value(not(hasItem(caso.toString()))));
		resolver(TENANT_B, "ANALYST", caso, """
				{"decision":"RECHAZADO","justificacion":"%s"}""".formatted(JUSTIFICACION))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("ASSESSMENT_NOT_FOUND"));
	}

	@Test
	void laResolucionNoSeVeDesdeOtroTenantNiSePuedeEditar() throws Exception {
		UUID caso = casoParcial(TENANT_A);
		resolver(TENANT_A, "ANALYST", caso, """
				{"decision":"RECHAZADO","justificacion":"%s"}""".formatted(JUSTIFICACION))
			.andExpect(status().isOk());

		String contar = "select count(*) from assessment.manual_review_resolutions where assessment_id = ?";
		assertThat(comoTenant(TENANT_A, () -> jdbc.queryForObject(contar, Integer.class, caso))).isEqualTo(1);
		assertThat(comoTenant(TENANT_B, () -> jdbc.queryForObject(contar, Integer.class, caso))).isZero();
		// Inmutable: la app no tiene UPDATE ni DELETE sobre la tabla.
		for (String sql : List.of(
				"update assessment.manual_review_resolutions set justification = 'cambiada a mano' where assessment_id = ?",
				"delete from assessment.manual_review_resolutions where assessment_id = ?")) {
			org.assertj.core.api.Assertions.assertThatThrownBy(() -> comoTenant(TENANT_A,
					() -> jdbc.update(sql, caso)))
				.hasStackTraceContaining("permission denied for table manual_review_resolutions");
		}
	}

	@Test
	void otroRolRecibe403() throws Exception {
		bandeja(TENANT_A, "PLATFORM_ADMIN")
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("INSUFFICIENT_ROLE"));
		resolver(TENANT_A, "PLATFORM_ADMIN", UUID.randomUUID(), """
				{"decision":"RECHAZADO","justificacion":"%s"}""".formatted(JUSTIFICACION))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("INSUFFICIENT_ROLE"));
	}

	// --- utilidades ------------------------------------------------------------

	private ResultActions bandeja(String tenant, String rol) throws Exception {
		return mvc.perform(get("/revision-manual").param("tamano", "100").with(token(tenant, rol)));
	}

	private ResultActions resolver(String tenant, String rol, UUID caso, String body) throws Exception {
		return mvc.perform(post("/revision-manual/{id}/resolucion", caso)
			.with(token(tenant, rol))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private static RequestPostProcessor token(String tenant, String rol) {
		return jwt().jwt(t -> t.subject("usuario-" + rol).claim("tenant_id", tenant)
			.claim("preferred_username", "ana.analista"))
			.authorities(new SimpleGrantedAuthority("ROLE_" + rol));
	}

	/** Una evaluación con el buró caído: queda PARCIAL, en la bandeja. */
	private UUID casoParcial(String tenant) throws Exception {
		int alta = mvc.perform(post("/solicitantes")
			.with(token(tenant, "ANALYST"))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"documento":"%s","actividad":"Comercio","antiguedadActividadMeses":36,
					 "ingresosMensuales":"5000.00","moneda":"BOB","destinoCredito":"Capital"}
					""".formatted(DOCUMENT)))
			.andReturn().getResponse().getStatus();
		assertThat(alta).as("201 nuevo o 409 APPLICANT_ALREADY_EXISTS").isIn(201, 409);
		UUID consent = UUID.randomUUID();
		comoTenant(tenant, () -> jdbc.update("""
				insert into consent.consents (id, tenant_id, titular_documento, fuentes, finalidad, vigencia_dias,
				  otorgado_en, expira_en, estado, otorgado_por, payload_sha256)
				values (?, ?::uuid, ?, '{BURO}'::text[], 'evaluacion', 90, now(), now() + interval '1 day',
				  'ACTIVO', 'test', 'x')
				""", consent, tenant, DOCUMENT));
		String response = mvc.perform(post("/credit-assessments")
			.with(token(tenant, "ANALYST"))
			.header("Idempotency-Key", "bandeja-" + UUID.randomUUID())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"titularDocumento":"%s","consentId":"%s","proposito":"Capital de trabajo",
					 "montoSolicitado":"3000.00","plazoMeses":12}
					""".formatted(DOCUMENT, consent)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.estado").value("PARCIAL"))
			.andReturn().getResponse().getContentAsString();
		return UUID.fromString(response.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1"));
	}

	private UUID productoActivo(String tenant) {
		return comoTenant(tenant, () -> jdbc.queryForObject(
				"select id from recommendation.products where active order by product_type limit 1", UUID.class));
	}

	/** SPIKE-1 (RLS): el JDBC directo corre fuera de una petición HTTP. */
	private static <T> T comoTenant(String tenant, Supplier<T> work) {
		TenantContext.set(UUID.fromString(tenant));
		try {
			return work.get();
		}
		finally {
			TenantContext.clear();
		}
	}

}
