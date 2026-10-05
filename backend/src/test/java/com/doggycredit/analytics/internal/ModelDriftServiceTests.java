package com.doggycredit.analytics.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.doggycredit.TestcontainersConfiguration;
import com.doggycredit.analytics.ModelDrift;
import com.doggycredit.analytics.ModelDrift.Level;
import com.doggycredit.analytics.ModelDrift.Status;
import com.doggycredit.shared.TenantContext;

import io.micrometer.core.instrument.MeterRegistry;

/** D-08: el job contra la base real, con RLS, y la métrica que va a Grafana. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ModelDriftServiceTests {

	private static final Instant WINDOW_END = Instant.now().truncatedTo(ChronoUnit.DAYS);

	@Autowired
	ModelDriftService drift;

	@Autowired
	ModelDrift api;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	MeterRegistry meters;

	@Test
	void detectaLaDerivaPorModeloLaGuardaYLaPublica() {
		UUID tenant = UUID.randomUUID();
		// Línea base (4 semanas antes): ambos modelos centrados en 700.
		insert(tenant, "basic-v1", 120, 700, WINDOW_END.minus(Duration.ofDays(20)));
		insert(tenant, "scoring-v2", 120, 700, WINDOW_END.minus(Duration.ofDays(20)));
		// Semana evaluada: basic-v1 igual; scoring-v2 se corre a 400.
		insert(tenant, "basic-v1", 60, 700, WINDOW_END.minus(Duration.ofDays(3)));
		insert(tenant, "scoring-v2", 60, 400, WINDOW_END.minus(Duration.ofDays(3)));
		// Fuera de las ventanas: no cuenta.
		insert(tenant, "scoring-v2", 50, 100, WINDOW_END.minus(Duration.ofDays(60)));

		Map<String, Status> result = byModel(drift.computeFor(tenant, WINDOW_END));
		assertThat(result.get("basic-v1").level()).isEqualTo(Level.ESTABLE);
		assertThat(result.get("basic-v1").psi()).isLessThan(0.10);
		assertThat(result.get("scoring-v2").level()).isEqualTo(Level.ALERTA);
		assertThat(result.get("scoring-v2").actualCount()).isEqualTo(60);
		assertThat(result.get("scoring-v2").baselineCount()).isEqualTo(120);

		// Persistido y consultable por el tenant (lo que usará GET /analytics/resumen).
		Map<String, Status> latest = byModel(comoTenant(tenant, () -> api.latest(tenant)));
		assertThat(latest.get("scoring-v2").level()).isEqualTo(Level.ALERTA);
		assertThat(latest.get("scoring-v2").psi()).isEqualTo(result.get("scoring-v2").psi(),
				org.assertj.core.api.Assertions.within(1e-6));

		// La métrica que exporta OTLP.
		double gauge = meters.get("doggycredit.scoring.psi").tags("tenant", tenant.toString(), "model", "scoring-v2")
			.gauge().value();
		assertThat(gauge).isCloseTo(result.get("scoring-v2").psi(), org.assertj.core.api.Assertions.within(1e-9));

		// Otro tenant no ve nada (RLS).
		UUID other = UUID.randomUUID();
		assertThat(comoTenant(other, () -> api.latest(tenant))).isEmpty();
		assertThat(drift.computeFor(other, WINDOW_END)).isEmpty();
	}

	@Test
	void conPocasEvaluacionesEsInsuficienteYNoCalculaPsi() {
		UUID tenant = UUID.randomUUID();
		insert(tenant, "scoring-v2", 40, 700, WINDOW_END.minus(Duration.ofDays(20)));
		insert(tenant, "scoring-v2", 10, 300, WINDOW_END.minus(Duration.ofDays(2)));
		Status status = drift.computeFor(tenant, WINDOW_END).getFirst();
		assertThat(status.level()).isEqualTo(Level.INSUFICIENTE);
		assertThat(status.psi()).isNull();
		assertThat(Double.isNaN(meters.get("doggycredit.scoring.psi")
			.tags("tenant", tenant.toString(), "model", "scoring-v2").gauge().value())).isTrue();
	}

	@Test
	void recalcularLaMismaSemanaNoDuplica() {
		UUID tenant = UUID.randomUUID();
		insert(tenant, "basic-v1", 40, 650, WINDOW_END.minus(Duration.ofDays(15)));
		insert(tenant, "basic-v1", 40, 650, WINDOW_END.minus(Duration.ofDays(1)));
		drift.computeFor(tenant, WINDOW_END);
		drift.computeFor(tenant, WINDOW_END);
		assertThat(comoTenant(tenant, () -> jdbc.queryForObject(
				"select count(*) from analytics.model_drift where tenant_id = ?", Integer.class, tenant))).isEqualTo(1);
	}

	/** {@code count} evaluaciones con scores alrededor de {@code center} (±60, repartidos en 3 tramos). */
	private void insert(UUID tenant, String model, int count, int center, Instant at) {
		comoTenant(tenant, () -> {
			for (int i = 0; i < count; i++) {
				int score = center + (i % 3 - 1) * 60;
				jdbc.update("""
						insert into assessment.credit_assessments (id, tenant_id, applicant_id, consent_id,
						  idempotency_key, request_hash, status, score, band, reasons, model_version, correlation_id,
						  created_at)
						values (?, ?, ?, ?, ?, 'x', 'REVISION_MANUAL', ?, 'B', '{A,B,C}'::text[], ?, 'drift', ?)
						""", UUID.randomUUID(), tenant, UUID.randomUUID(), UUID.randomUUID(),
						"drift-" + UUID.randomUUID(), score, model, Timestamp.from(at.plusSeconds(i)));
			}
			return null;
		});
	}

	private static Map<String, Status> byModel(List<Status> statuses) {
		return statuses.stream().collect(Collectors.toMap(Status::modelVersion, Function.identity()));
	}

	/** SPIKE-1 (RLS): el JDBC directo corre fuera de una petición HTTP. */
	private <T> T comoTenant(UUID tenant, java.util.function.Supplier<T> work) {
		TenantContext.set(tenant);
		try {
			return work.get();
		}
		finally {
			TenantContext.clear();
		}
	}

}
