package com.doggycredit.assessment.internal;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.doggycredit.TestTenants;
import com.doggycredit.TestcontainersConfiguration;
import com.doggycredit.shared.TenantContext;

/** C-09: GET /credit-assessments paginado, filtrado y aislado por tenant (RLS real en Postgres). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AssessmentListTests {

	private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PostgreSQLContainer postgres;

	/** Tenants nuevos en cada prueba: los conteos no dependen de otras pruebas. */
	private UUID tenant;
	private UUID otherTenant;
	private final UUID applicant = UUID.randomUUID();
	private final UUID otherApplicant = UUID.randomUUID();

	@BeforeEach
	void tenants() {
		tenant = UUID.randomUUID();
		otherTenant = UUID.randomUUID();
		TestTenants.register(postgres, tenant.toString(), "FREE");
		TestTenants.register(postgres, otherTenant.toString(), "FREE");
	}

	@Test
	void paginaDeLaMasRecienteALaMasAntiguaConElTotal() throws Exception {
		UUID oldest = insert(tenant, applicant, NOW.minus(3, ChronoUnit.DAYS), "REVISION_MANUAL", 700, "B");
		UUID middle = insert(tenant, applicant, NOW.minus(2, ChronoUnit.DAYS), "PARCIAL", 340, "D");
		UUID newest = insert(tenant, otherApplicant, NOW.minus(1, ChronoUnit.DAYS), "PREAPROBADO", 820, "A");

		list(tenant, "?pagina=1&tamano=2")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.total").value(3))
			.andExpect(jsonPath("$.items.length()").value(2))
			.andExpect(jsonPath("$.items[0].id").value(newest.toString()))
			.andExpect(jsonPath("$.items[0].estado").value("PREAPROBADO"))
			.andExpect(jsonPath("$.items[0].score").value(820))
			.andExpect(jsonPath("$.items[0].banda").value("A"))
			.andExpect(jsonPath("$.items[0].montoSolicitado").value("3000.00"))
			.andExpect(jsonPath("$.items[0].plazoMeses").value(12))
			.andExpect(jsonPath("$.items[1].id").value(middle.toString()));

		list(tenant, "?pagina=2&tamano=2")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.total").value(3))
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].id").value(oldest.toString()));
	}

	@Test
	void filtraPorSolicitanteYPorFecha() throws Exception {
		insert(tenant, applicant, NOW.minus(10, ChronoUnit.DAYS), "REVISION_MANUAL", 700, "B");
		UUID recent = insert(tenant, applicant, NOW.minus(1, ChronoUnit.DAYS), "PARCIAL", 340, "D");
		insert(tenant, otherApplicant, NOW.minus(1, ChronoUnit.DAYS), "PREAPROBADO", 820, "A");

		list(tenant, "?solicitanteId=" + applicant)
			.andExpect(jsonPath("$.total").value(2))
			.andExpect(jsonPath("$.items[0].solicitanteId").value(applicant.toString()))
			.andExpect(jsonPath("$.items[1].solicitanteId").value(applicant.toString()));

		list(tenant, "?solicitanteId=" + applicant + "&desde=" + NOW.minus(2, ChronoUnit.DAYS))
			.andExpect(jsonPath("$.total").value(1))
			.andExpect(jsonPath("$.items[0].id").value(recent.toString()));
	}

	@Test
	void unTenantNuncaVeLasEvaluacionesDeOtro() throws Exception {
		insert(tenant, applicant, NOW, "REVISION_MANUAL", 700, "B");
		insert(otherTenant, otherApplicant, NOW, "PREAPROBADO", 820, "A");

		list(otherTenant, "")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.total").value(1))
			.andExpect(jsonPath("$.items[0].solicitanteId").value(otherApplicant.toString()));
		// Ni pidiendo explícitamente el solicitante del otro tenant.
		list(otherTenant, "?solicitanteId=" + applicant)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.total").value(0))
			.andExpect(jsonPath("$.items.length()").value(0));
	}

	@Test
	void unaEvaluacionQuePideDatosSeListaComoRevisionManual() throws Exception {
		// C-05: el enum `estado` del contrato v1 no incluye SOLICITAR_DATOS (agregarlo rompía a
		// los consumidores); sin publicar el equivalente, el listado respondería 500.
		insert(tenant, applicant, NOW, "SOLICITAR_DATOS", 820, "A");

		list(tenant, "")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].estado").value("REVISION_MANUAL"))
			.andExpect(jsonPath("$.items[0].decision").value("SOLICITAR_DATOS"));
	}

	@Test
	void cadaEvaluacionTraeSuDecisionYUnParcialNuncaEsPreaprobado() throws Exception {
		insert(tenant, applicant, NOW.minus(1, ChronoUnit.DAYS), "PARCIAL", 820, "A");
		insert(tenant, applicant, NOW, "PREAPROBADO", 820, "A");

		list(tenant, "")
			.andExpect(jsonPath("$.items[0].decision").value("PREAPROBADO"))
			.andExpect(jsonPath("$.items[1].estado").value("PARCIAL"))
			.andExpect(jsonPath("$.items[1].decision").value("REVISION_MANUAL"));
	}

	@Test
	void sinTokenResponde401YRechazaParametrosFueraDeRango() throws Exception {
		mvc.perform(get("/credit-assessments")).andExpect(status().isUnauthorized());
		// Antes respondía 500 (ConstraintViolationException sin traducir); es error del cliente.
		list(tenant, "?tamano=101")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			// El texto del mensaje depende del idioma de la JVM; el nombre del parámetro no.
			.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.startsWith("tamano: ")));
		list(tenant, "?pagina=0")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	private ResultActions list(UUID tenantId, String query) throws Exception {
		return mvc.perform(get("/credit-assessments" + query)
			.with(jwt().jwt(token -> token.subject("analista-9").claim("tenant_id", tenantId.toString()))));
	}

	@Test
	void detalleProPublicaElSnapshotYLoAislaDeOtrosTenants() throws Exception {
		UUID pro = UUID.randomUUID();
		TestTenants.register(postgres, pro.toString(), "PRO");
		UUID id = recommended(pro);
		detail(pro, id)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.recomendacionesDisponibles").value(true))
			.andExpect(jsonPath("$.recomendaciones.length()").value(1))
			.andExpect(jsonPath("$.recomendaciones[0].tipo").value("MICROCREDITO"))
			.andExpect(jsonPath("$.recomendaciones[0].montoRecomendado").value("4123.25"));
		detail(otherTenant, id).andExpect(status().isNotFound());
		mvc.perform(get("/credit-assessments/{id}", id)).andExpect(status().isUnauthorized());
	}

	@Test
	void detalleFreeNoRevelaUnProductoPersistido() throws Exception {
		UUID id = recommended(tenant);
		detail(tenant, id)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.recomendacionesDisponibles").value(false))
			.andExpect(jsonPath("$.recomendaciones").isEmpty());
	}

	private ResultActions detail(UUID tenantId, UUID id) throws Exception {
		return mvc.perform(get("/credit-assessments/{id}", id)
			.with(jwt().jwt(token -> token.subject("analista-9").claim("tenant_id", tenantId.toString()))));
	}

	private UUID recommended(UUID tenantId) {
		UUID id = insert(tenantId, applicant, NOW, "REVISION_MANUAL", 800, "A");
		TenantContext.set(tenantId);
		try {
			jdbc.update("""
					update assessment.credit_assessments
					set recommended_product_type = 'MICROCREDITO', recommended_amount = 4123.25
					where id = ?
					""", id);
		}
		finally {
			TenantContext.clear();
		}
		return id;
	}

	/** Inserta como el tenant (la RLS exige app.tenant_id), igual que haría la app. */
	private UUID insert(UUID tenantId, UUID applicantId, Instant createdAt, String status, int score, String band) {
		UUID id = UUID.randomUUID();
		TenantContext.set(tenantId);
		try {
			jdbc.update("""
					insert into assessment.credit_assessments (id, tenant_id, applicant_id, consent_id, idempotency_key,
					  request_hash, purpose, requested_amount, term_months, status, score, band, reasons, model_version,
					  simulated, correlation_id, created_at)
					values (?, ?, ?, ?, ?, ?, 'Capital de trabajo', 3000.00, 12, ?, ?, ?,
					  '{R1,R2,R3}'::text[], 'basic-v1', true, 'corr-test', ?)
					""", id, tenantId, applicantId, UUID.randomUUID(), "key-" + id, "h".repeat(64), status, score, band,
					Timestamp.from(createdAt));
		}
		finally {
			TenantContext.clear();
		}
		return id;
	}

}
