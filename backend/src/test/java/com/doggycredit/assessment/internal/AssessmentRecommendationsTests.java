package com.doggycredit.assessment.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.doggycredit.TestTenants;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * C-06 / E-16: elegibilidad en todos los planes y ranking bajo Feature.RECOMENDACIONES (A-06).
 * Tenants nuevos en cada prueba: el resultado no depende del orden de las pruebas.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "doggycredit.assessment.collection-budget=100ms")
@AutoConfigureMockMvc
class AssessmentRecommendationsTests {

	private static final String DOCUMENT = "CI-RECO-0001";

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PostgreSQLContainer postgres;

	@MockitoBean(name = "bureauConnector")
	BureauConnector bureau;

	private UUID pro;
	private UUID free;

	@BeforeEach
	void tenants() throws Exception {
		when(bureau.source()).thenReturn(Source.BURO);
		pro = UUID.randomUUID();
		free = UUID.randomUUID();
		TestTenants.register(postgres, pro.toString(), "PRO");
		TestTenants.register(postgres, free.toString(), "FREE");
		for (UUID tenant : List.of(pro, free)) {
			product(tenant, "MICROCREDITO");
			product(tenant, "VERDE");
            product(tenant, "PYME");
			registerApplicant(tenant);
		}
	}

	@Test
	void unTenantProRecibeLasRecomendacionesConSuRazonYSeConservanAlConsultar() throws Exception {
		when(bureau.fetch(any(), anyString())).thenReturn(Optional.of(report()));
		UUID consent = consent(pro);

		String body = create(pro, consent, "reco-pro-0001")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.recomendaciones.length()").value(1))
			.andExpect(jsonPath("$.recomendaciones[0].tipoProducto").value("MICROCREDITO"))
			.andExpect(jsonPath("$.recomendaciones[0].moneda").value("BOB"))
			.andExpect(jsonPath("$.recomendaciones[0].montoSugerido").value("3000.00"))
			.andExpect(jsonPath("$.recomendaciones[0].razon").isNotEmpty())
            .andExpect(jsonPath("$.recomendaciones[0].plazoSugeridoMeses").value(12))
            .andExpect(jsonPath("$.recomendaciones[0].razones.length()").value(greaterThanOrEqualTo(2)))
            .andExpect(jsonPath("$.elegibilidad.length()").value(3))
			.andReturn().getResponse().getContentAsString();
		String id = body.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

		// La consulta y la repetición idempotente devuelven lo mismo que vio el analista.
		mvc.perform(get("/credit-assessments/{id}", id).with(token(pro)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.recomendaciones.length()").value(1))
			.andExpect(jsonPath("$.recomendaciones[0].tipoProducto").value("MICROCREDITO"));
		create(pro, consent, "reco-pro-0001")
			.andExpect(jsonPath("$.id").value(id))
			.andExpect(jsonPath("$.recomendaciones.length()").value(1));
		assertThat(asTenant(pro, () -> jdbc.queryForObject(
				"SELECT count(*) FROM assessment.assessment_recommendations WHERE assessment_id = ?::uuid",
				Integer.class, id))).isEqualTo(1);
	}

	@Test
	void unTenantFreeNoRecibeRecomendaciones() throws Exception {
		when(bureau.fetch(any(), anyString())).thenReturn(Optional.of(report()));
		String body = create(free, consent(free), "reco-free-0001")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.recomendaciones").doesNotHaveJsonPath())
            .andExpect(jsonPath("$.elegibilidad.length()").value(3))
            .andExpect(jsonPath("$.elegibilidad[*].criterios.length()", everyItem(is(6))))
            .andExpect(jsonPath("$.elegibilidad[?(@.tipoProducto == 'MICROCREDITO')].elegible", contains(true)))
            .andExpect(jsonPath("$.elegibilidad[?(@.tipoProducto == 'PYME')].elegible", contains(false)))
			.andReturn().getResponse().getContentAsString();
		String id = body.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

		mvc.perform(get("/credit-assessments/{id}", id).with(token(free)))
			.andExpect(jsonPath("$.recomendaciones").doesNotHaveJsonPath())
            .andExpect(jsonPath("$.elegibilidad.length()").value(3));
	}

	@Test
	void conUnResultadoParcialNoSeRecomiendaNiSiquieraEnPro() throws Exception {
		when(bureau.fetch(any(), anyString()))
			.thenThrow(new SourceUnavailableException(Source.BURO, "simulada", null));
		create(pro, consent(pro), "reco-parcial-0001")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.estado").value("PARCIAL"))
			.andExpect(jsonPath("$.recomendaciones").doesNotExist())
            .andExpect(jsonPath("$.elegibilidad.length()").value(3))
            .andExpect(jsonPath("$.elegibilidad[*].elegible", everyItem(is(false))))
            .andExpect(jsonPath("$.elegibilidad[*].criterios[?(@.codigo == 'CAPACIDAD_PAGO')].cumple",
                    everyItem(is(false))));
	}

	@Test
	void unTenantNoVeLasRecomendacionesDeOtro() throws Exception {
		when(bureau.fetch(any(), anyString())).thenReturn(Optional.of(report()));
		String body = create(pro, consent(pro), "reco-aislado-0001")
			.andReturn().getResponse().getContentAsString();
		String id = body.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

		mvc.perform(get("/credit-assessments/{id}", id).with(token(free)))
			.andExpect(status().isNotFound());
		assertThat(asTenant(free, () -> jdbc.queryForObject(
				"SELECT count(*) FROM assessment.assessment_recommendations WHERE assessment_id = ?::uuid",
				Integer.class, id))).isZero();
        assertThat(asTenant(free, () -> jdbc.queryForObject(
                "SELECT count(*) FROM assessment.eligibility_snapshots WHERE assessment_id = ?::uuid",
                Integer.class, id))).isZero();
        assertThatThrownBy(() -> asTenant(free, () -> jdbc.update("""
                INSERT INTO assessment.eligibility_snapshots (id, tenant_id, assessment_id, products)
                VALUES (?, ?, ?, '[]'::jsonb)
                """, UUID.randomUUID(), pro, UUID.randomUUID()))).isInstanceOf(DataAccessException.class);
	}

	@Test
	void lasRecomendacionesSonInmutablesParaLaApp() {
		for (String sql : List.of("UPDATE assessment.assessment_recommendations SET reason = 'x'",
				"DELETE FROM assessment.assessment_recommendations",
                "UPDATE assessment.eligibility_snapshots SET products = '[]'::jsonb",
                "DELETE FROM assessment.eligibility_snapshots")) {
			assertThatThrownBy(() -> asTenant(pro, () -> jdbc.update(sql)))
				.isInstanceOfSatisfying(DataAccessException.class, e -> assertThat(e.getMostSpecificCause())
					.isInstanceOfSatisfying(SQLException.class, cause -> assertThat(cause.getSQLState())
						.isEqualTo("42501")));
		}
	}

    @Test
    void losCambiosDelCatalogoNoAlteranElSnapshotNiLaRepeticionIdempotente() throws Exception {
        when(bureau.fetch(any(), anyString())).thenReturn(Optional.of(report()));
        UUID consent = consent(pro);
        JsonNode original = body(create(pro, consent, "frozen-products"));
        String id = original.path("id").asString();
        adminSql("UPDATE recommendation.products SET active = false WHERE tenant_id = ? AND product_type = 'MICROCREDITO'", pro);
        adminSql("UPDATE recommendation.products SET min_band = 'A', min_amount = 25000 WHERE tenant_id = ? AND product_type = 'VERDE'", pro);

        JsonNode read = body(mvc.perform(get("/credit-assessments/{id}", id).with(token(pro))).andExpect(status().isOk()));
        JsonNode replay = body(create(pro, consent, "frozen-products"));
        for (JsonNode response : List.of(read, replay)) {
            assertThat(response.path("elegibilidad")).isEqualTo(original.path("elegibilidad"));
            assertThat(response.path("recomendaciones")).isEqualTo(original.path("recomendaciones"));
        }
        assertThat(asTenant(pro, () -> jdbc.queryForObject(
                "SELECT count(*) FROM assessment.eligibility_snapshots", Integer.class))).isEqualTo(1);
        assertThat(asTenant(pro, () -> jdbc.queryForObject(
                "SELECT evaluaciones FROM billing.monthly_usage", Integer.class))).isEqualTo(1);
        // El perfil carga la evaluación por el id del listado: se conserva el mismo resultado.
        mvc.perform(get("/credit-assessments").with(token(pro))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(id));
        create(pro, consent, "new-catalog-snapshot").andExpect(status().isCreated())
                .andExpect(jsonPath("$.elegibilidad.length()").value(2))
                .andExpect(jsonPath("$.elegibilidad[*].tipoProducto", not(hasItem("MICROCREDITO"))));
    }

    @Test
    void noListaProductosInactivosYDistingueCatalogoVacioDeNingunProductoElegible() throws Exception {
        when(bureau.fetch(any(), anyString())).thenReturn(Optional.of(report()));
        adminSql("UPDATE recommendation.products SET active = false WHERE tenant_id = ? AND product_type = 'VERDE'", pro);
        create(pro, consent(pro), "inactive-products").andExpect(status().isCreated())
                .andExpect(jsonPath("$.elegibilidad.length()").value(2))
                .andExpect(jsonPath("$.elegibilidad[*].tipoProducto", not(hasItem("VERDE"))));
        adminSql("UPDATE recommendation.products SET min_band = 'A', min_amount = 25000 WHERE tenant_id = ?", pro);
        create(pro, consent(pro), "no-product-applies").andExpect(status().isCreated())
                .andExpect(jsonPath("$.elegibilidad.length()").value(2))
                .andExpect(jsonPath("$.elegibilidad[*].elegible", everyItem(is(false))))
                .andExpect(jsonPath("$.recomendaciones", empty()));
        adminSql("UPDATE recommendation.products SET active = false WHERE tenant_id = ?", pro);
        JsonNode empty = body(create(pro, consent(pro), "empty-catalog").andExpect(status().isCreated())
                .andExpect(jsonPath("$.elegibilidad", empty())).andExpect(jsonPath("$.recomendaciones", empty())));
        mvc.perform(get("/credit-assessments/{id}", empty.path("id").asString()).with(token(pro)))
                .andExpect(jsonPath("$.elegibilidad", empty()));
    }

    @Test
    void elPlanVigenteOcultaElRankingTrasBajarAFreeYLoRecuperaAlVolverAPro() throws Exception {
        when(bureau.fetch(any(), anyString())).thenReturn(Optional.of(report()));
        UUID consent = consent(pro);
        JsonNode original = body(create(pro, consent, "plan-gated-history"));
        String id = original.path("id").asString();
        changePlan(pro, "FREE");
        for (JsonNode response : List.of(body(mvc.perform(get("/credit-assessments/{id}", id).with(token(pro)))),
                body(create(pro, consent, "plan-gated-history")))) {
            assertThat(response.has("recomendaciones")).isFalse();
            assertThat(response.path("elegibilidad")).isEqualTo(original.path("elegibilidad"));
        }
        changePlan(pro, "PRO");
        JsonNode restored = body(mvc.perform(get("/credit-assessments/{id}", id).with(token(pro))));
        assertThat(restored.path("recomendaciones")).isEqualTo(original.path("recomendaciones"));
    }

    @Test
    void subirAProNoRecalculaElHistoricoFree() throws Exception {
        when(bureau.fetch(any(), anyString())).thenReturn(Optional.of(report()));
        JsonNode original = body(create(free, consent(free), "free-history"));
        changePlan(free, "PRO");
        JsonNode upgraded = body(mvc.perform(get("/credit-assessments/{id}", original.path("id").asString()).with(token(free))));
        assertThat(upgraded.has("recomendaciones")).isFalse();
        assertThat(upgraded.path("elegibilidad")).isEqualTo(original.path("elegibilidad"));
    }

    @Test
    void unSnapshotC06ConservaSuRazonSinInventarCriteriosPlazosORazonesNuevas() throws Exception {
        when(bureau.fetch(any(), anyString())).thenReturn(Optional.of(report()));
        JsonNode original = body(create(pro, consent(pro), "legacy-snapshot"));
        UUID id = UUID.fromString(original.path("id").asString());
        // Simula una evaluación de C-06 anterior a la migración de E-16, con privilegios de fixture.
        adminSql("DELETE FROM assessment.eligibility_snapshots WHERE assessment_id = ?", id);
        adminSql("UPDATE assessment.assessment_recommendations SET suggested_term_months = null, reasons = null WHERE assessment_id = ?", id);
        mvc.perform(get("/credit-assessments/{id}", id).with(token(pro)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.elegibilidad").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.recomendaciones[0].razon").value(original.path("recomendaciones").get(0).path("razon").asString()))
                .andExpect(jsonPath("$.recomendaciones[0].plazoSugeridoMeses").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.recomendaciones[0].razones").doesNotHaveJsonPath());
    }

    @Test
    void unAnalistaLeeElegibilidadSinPoderAdministrarElCatalogoYSeExigeAutenticacion() throws Exception {
        when(bureau.fetch(any(), anyString())).thenReturn(Optional.of(report()));
        JsonNode original = body(create(free, consent(free), "analyst-eligibility").andExpect(status().isCreated())
                .andExpect(jsonPath("$.elegibilidad.length()").value(3)));
        mvc.perform(get("/productos").with(token(free))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_ROLE"));
        mvc.perform(get("/productos").with(jwt().jwt(t -> t.subject("admin").claim("tenant_id", free.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN")))).andExpect(status().isOk());
        mvc.perform(get("/credit-assessments/{id}", original.path("id").asString())).andExpect(status().isUnauthorized());
        mvc.perform(post("/credit-assessments").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

	// --- utilidades --------------------------------------------------------------

	private ResultActions create(UUID tenant, UUID consent, String key) throws Exception {
		return mvc.perform(post("/credit-assessments").with(token(tenant))
			.header("Idempotency-Key", key)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"titularDocumento":"%s","consentId":"%s",
					 "proposito":"Capital de trabajo","montoSolicitado":"3000.00","plazoMeses":12}
					""".formatted(DOCUMENT, consent)));
	}

	private static org.springframework.test.web.servlet.request.RequestPostProcessor token(UUID tenant) {
		return jwt().jwt(t -> t.subject("analista-reco").claim("tenant_id", tenant.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_ANALYST"));
	}

    private static JsonNode body(ResultActions response) throws Exception {
        return JsonMapper.builder().build().readTree(response.andReturn().getResponse().getContentAsString());
    }

    private void changePlan(UUID tenant, String plan) throws Exception {
        mvc.perform(patch("/admin/tenants/{id}", tenant)
                .with(jwt().jwt(t -> t.subject("operator").claim("tenant_id", free.toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"plan\":\"" + plan + "\"}"))
                .andExpect(status().isOk());
    }

    private void adminSql(String sql, Object... arguments) throws SQLException {
        try (Connection admin = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                PreparedStatement ps = admin.prepareStatement(sql)) {
            for (int i = 0; i < arguments.length; i++) ps.setObject(i + 1, arguments[i]);
            ps.executeUpdate();
        }
    }

	private void registerApplicant(UUID tenant) throws Exception {
		mvc.perform(post("/solicitantes").with(token(tenant))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"documento":"%s","actividad":"Comercio","antiguedadActividadMeses":36,
					 "ingresosMensuales":"5000.00","moneda":"BOB","destinoCredito":"Capital"}
					""".formatted(DOCUMENT)))
			.andExpect(status().isCreated());
	}

	private UUID consent(UUID tenant) {
		UUID id = UUID.randomUUID();
		asTenant(tenant, () -> jdbc.update("""
				insert into consent.consents (id, tenant_id, titular_documento, fuentes, finalidad, vigencia_dias,
				  otorgado_en, expira_en, estado, otorgado_por, payload_sha256)
				values (?, ?, ?, '{BURO}'::text[], 'evaluacion', 90, now(), now() + interval '1 day',
				  'ACTIVO', 'test', 'x')
				""", id, tenant, DOCUMENT));
		return id;
	}

	/** Como superusuario del contenedor (la app no tiene INSERT sobre el catálogo). */
	private void product(UUID tenant, String type) throws SQLException {
		try (Connection admin = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
				postgres.getPassword()); PreparedStatement ps = admin.prepareStatement("""
						INSERT INTO recommendation.products (id, tenant_id, product_type, min_amount, max_amount,
						  min_term_months, max_term_months, annual_rate, min_band)
						VALUES (?, ?, ?, 1000, 30000, 6, 36, 11.5, 'C')
						""")) {
			ps.setObject(1, UUID.randomUUID());
			ps.setObject(2, tenant);
			ps.setString(3, type);
			ps.executeUpdate();
		}
	}

	private static <T> T asTenant(UUID tenant, Supplier<T> work) {
		TenantContext.set(tenant);
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
