package com.doggycredit.assessment.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.doggycredit.TestcontainersConfiguration;
import com.doggycredit.assessment.AssessmentNotFoundException;
import com.doggycredit.assessment.internal.ScoreReproducer.Outcome;
import com.doggycredit.assessment.internal.ScoreReproducer.Reproduction;
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
 * C-12 (LOS-113), RF-SCO-04: re-ejecutar con el mismo vector y la misma versión de modelo
 * da exactamente el mismo resultado. De punta a punta por la API, contra PostgreSQL real,
 * en los dos planes (FREE: scorecard básico; PRO: modelo ONNX). La manipulación se simula
 * como en la demo de auditoría: un superusuario edita la fila, que la app no puede editar.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ScoreReproducibilityTests {

	private static final String FREE = "9c7e7c1a-0000-4000-8000-000000000001";
	private static final String PRO = "9c7e7c1a-0000-4000-8000-000000000002";
	private static final String DOCUMENT = "CI-REPRO-0001";

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	ScoreReproducer reproducer;

	@Autowired
	PostgreSQLContainer postgres;

	@MockitoBean(name = "bureauConnector")
	BureauConnector bureau;

	@BeforeEach
	void bureauAnswers() {
		when(bureau.source()).thenReturn(Source.BURO);
		when(bureau.fetch(any(), anyString())).thenReturn(Optional.of(report()));
	}

	@Test
	void planFreeRecalculaElMismoScoreConElScorecardBasico() throws Exception {
		UUID id = assess(FREE);

		Reproduction reproduction = comoTenant(FREE, () -> reproducer.reproduce(UUID.fromString(FREE), id));

		assertThat(reproduction.outcome()).isEqualTo(Outcome.IDENTICAL);
		assertThat(reproduction.recalculated()).get().extracting(r -> r.modelVersion()).isEqualTo("basic-v1");
	}

	@Test
	void planProRecalculaElMismoScoreConElModeloOnnx() throws Exception {
		UUID id = assess(PRO);

		Reproduction reproduction = comoTenant(PRO, () -> reproducer.reproduce(UUID.fromString(PRO), id));

		assertThat(reproduction.outcome()).isEqualTo(Outcome.IDENTICAL);
		assertThat(reproduction.recalculated()).get().extracting(r -> r.modelVersion()).isEqualTo("scoring-v2");
	}

	@Test
	void conElBuroCaidoGuardaLosDatosComoDesconocidosYTambienSeReproduce() throws Exception {
		when(bureau.fetch(any(), anyString())).thenThrow(new SourceUnavailableException(Source.BURO, "simulada", null));
		UUID id = assess(FREE);

		// RN-15: «desconocido» queda como null, nunca como 0 ni «sin registro».
		String input = storedInput(FREE, id);
		assertThat(input).contains("\"maxDaysPastDue12m\":null", "\"bureauRecordFound\":null");
		assertThat(comoTenant(FREE, () -> reproducer.reproduce(UUID.fromString(FREE), id)).outcome())
			.isEqualTo(Outcome.IDENTICAL);
	}

	@Test
	void unVectorAlteradoNoSeRecalcula() throws Exception {
		UUID id = assess(FREE);
		assertThat(storedInput(FREE, id)).contains("\"monthlyIncome\":\"5000.00\"");

		manipularComoSuperusuario("""
				UPDATE assessment.credit_assessments
				   SET scoring_input = replace(scoring_input, '"monthlyIncome":"5000.00"', '"monthlyIncome":"50000.00"')
				 WHERE id = ?""", id);

		Reproduction reproduction = comoTenant(FREE, () -> reproducer.reproduce(UUID.fromString(FREE), id));
		assertThat(reproduction.outcome()).isEqualTo(Outcome.INPUT_TAMPERED);
		assertThat(reproduction.recalculated()).isEmpty();
	}

	@Test
	void unaVersionDeModeloQueYaNoEstaCargadaNoSeSustituyePorOtra() throws Exception {
		UUID id = assess(FREE);

		manipularComoSuperusuario("UPDATE assessment.credit_assessments SET model_version = 'basic-v0' WHERE id = ?",
				id);

		assertThat(comoTenant(FREE, () -> reproducer.reproduce(UUID.fromString(FREE), id)).outcome())
			.isEqualTo(Outcome.MODEL_VERSION_UNAVAILABLE);
	}

	@Test
	void unaEvaluacionAnteriorSinVectorNoInventaUno() throws Exception {
		UUID id = assess(FREE);

		manipularComoSuperusuario("""
				UPDATE assessment.credit_assessments
				   SET scoring_input = NULL, scoring_input_sha256 = NULL
				 WHERE id = ?""", id);

		assertThat(comoTenant(FREE, () -> reproducer.reproduce(UUID.fromString(FREE), id)).outcome())
			.isEqualTo(Outcome.NO_INPUT);
	}

	@Test
	void otroTenantNoPuedeReproducirUnaEvaluacionAjena() throws Exception {
		UUID id = assess(FREE);

		assertThatThrownBy(() -> comoTenant(PRO, () -> reproducer.reproduce(UUID.fromString(PRO), id)))
			.isInstanceOf(AssessmentNotFoundException.class);
	}

	// --- utilidades ------------------------------------------------------------

	private UUID assess(String tenant) throws Exception {
		UUID consent = prepare(tenant);
		String response = mvc.perform(post("/credit-assessments")
			.with(jwt().jwt(token -> token.subject("analista-repro").claim("tenant_id", tenant)))
			.header("Idempotency-Key", "repro-" + UUID.randomUUID())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"titularDocumento":"%s","consentId":"%s","proposito":"Capital de trabajo",
					 "montoSolicitado":"3000.00","plazoMeses":12}
					""".formatted(DOCUMENT, consent)))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return UUID.fromString(response.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1"));
	}

	private UUID prepare(String tenant) throws Exception {
		// El documento se guarda cifrado: en vez de buscarlo, se crea y se acepta que ya exista.
		int created = mvc.perform(post("/solicitantes")
			.with(jwt().jwt(token -> token.subject("analista-repro").claim("tenant_id", tenant)))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"documento":"%s","actividad":"Comercio","antiguedadActividadMeses":36,
					 "ingresosMensuales":"5000.00","moneda":"BOB","destinoCredito":"Capital"}
					""".formatted(DOCUMENT)))
			.andReturn().getResponse().getStatus();
		assertThat(created).as("201 nuevo o 409 APPLICANT_ALREADY_EXISTS").isIn(201, 409);
		UUID consent = UUID.randomUUID();
		comoTenant(tenant, () -> jdbc.update("""
				insert into consent.consents (id, tenant_id, titular_documento, fuentes, finalidad, vigencia_dias,
				  otorgado_en, expira_en, estado, otorgado_por, payload_sha256)
				values (?, ?::uuid, ?, '{BURO}'::text[], 'evaluacion', 90, now(), now() + interval '1 day',
				  'ACTIVO', 'test', 'x')
				""", consent, tenant, DOCUMENT));
		return consent;
	}

	private String storedInput(String tenant, UUID id) {
		return comoTenant(tenant, () -> jdbc.queryForObject(
				"select scoring_input from assessment.credit_assessments where id = ?", String.class, id));
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

	/** La app solo tiene SELECT, INSERT: alterar una evaluación exige un superusuario. */
	private void manipularComoSuperusuario(String sql, UUID id) throws SQLException {
		try (Connection admin = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
				postgres.getPassword()); PreparedStatement ps = admin.prepareStatement(sql)) {
			ps.setObject(1, id);
			assertThat(ps.executeUpdate()).isEqualTo(1);
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
