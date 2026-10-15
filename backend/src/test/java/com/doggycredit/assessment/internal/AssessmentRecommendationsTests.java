package com.doggycredit.assessment.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

/**
 * C-06: recomendaciones[] en la evaluación, solo en planes con Feature.RECOMENDACIONES (A-06).
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
			.andExpect(jsonPath("$.recomendaciones").doesNotExist())
			.andReturn().getResponse().getContentAsString();
		String id = body.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

		mvc.perform(get("/credit-assessments/{id}", id).with(token(free)))
			.andExpect(jsonPath("$.recomendaciones").doesNotExist());
	}

	@Test
	void conUnResultadoParcialNoSeRecomiendaNiSiquieraEnPro() throws Exception {
		when(bureau.fetch(any(), anyString()))
			.thenThrow(new SourceUnavailableException(Source.BURO, "simulada", null));
		create(pro, consent(pro), "reco-parcial-0001")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.estado").value("PARCIAL"))
			.andExpect(jsonPath("$.recomendaciones").doesNotExist());
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
	}

	@Test
	void lasRecomendacionesSonInmutablesParaLaApp() {
		for (String sql : List.of("UPDATE assessment.assessment_recommendations SET reason = 'x'",
				"DELETE FROM assessment.assessment_recommendations")) {
			assertThatThrownBy(() -> asTenant(pro, () -> jdbc.update(sql)))
				.isInstanceOfSatisfying(DataAccessException.class, e -> assertThat(e.getMostSpecificCause())
					.isInstanceOfSatisfying(SQLException.class, cause -> assertThat(cause.getSQLState())
						.isEqualTo("42501")));
		}
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
		return jwt().jwt(t -> t.subject("analista-reco").claim("tenant_id", tenant.toString()));
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
