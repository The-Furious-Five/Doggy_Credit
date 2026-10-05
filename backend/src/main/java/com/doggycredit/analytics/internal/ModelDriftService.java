package com.doggycredit.analytics.internal;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.doggycredit.analytics.ModelDrift;
import com.doggycredit.shared.TenantContext;
import com.doggycredit.tenancy.TenantInfo;
import com.doggycredit.tenancy.TenantRegistry;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * D-08: job semanal de deriva del score (paga la parte de monitoreo de
 * DEBT-005). Para cada tenant y modelo compara la distribución del score de
 * la última semana contra la de las 4 semanas anteriores, guarda el PSI en
 * {@code analytics.model_drift} y lo publica como la métrica
 * {@code doggycredit.scoring.psi} (OTLP → Grafana, panel «Modelos de scoring»).
 */
@Service
class ModelDriftService implements ModelDrift {

	private static final Logger log = LoggerFactory.getLogger(ModelDriftService.class);
	private static final Duration WINDOW = Duration.ofDays(7);
	private static final Duration BASELINE = Duration.ofDays(28);

	private final JdbcTemplate jdbc;
	private final TransactionTemplate transactions;
	private final TenantRegistry tenants;
	private final MeterRegistry meters;
	private final int minSample;
	private final Map<String, AtomicReference<Double>> gauges = new ConcurrentHashMap<>();

	ModelDriftService(JdbcTemplate jdbc, TransactionTemplate transactions, TenantRegistry tenants, MeterRegistry meters,
			@Value("${doggycredit.analytics.drift.min-sample:30}") int minSample) {
		this.jdbc = jdbc;
		this.transactions = transactions;
		this.tenants = tenants;
		this.meters = meters;
		this.minSample = minSample;
	}

	/** Lunes 03:00 (hora de Bolivia), sobre la semana que terminó a medianoche. */
	@Scheduled(cron = "${doggycredit.analytics.drift.cron:0 0 3 * * MON}", zone = "America/La_Paz")
	void runWeekly() {
		Instant windowEnd = Instant.now().truncatedTo(ChronoUnit.DAYS);
		for (TenantInfo tenant : tenants.findAllActive()) {
			try {
				computeFor(tenant.id(), windowEnd);
			}
			catch (RuntimeException e) {
				log.error("No se pudo calcular la deriva del tenant {}", tenant.id(), e);
			}
		}
	}

	/** Calcula y guarda el PSI de cada modelo del tenant para la semana que termina en {@code windowEnd}. */
	List<Status> computeFor(UUID tenantId, Instant windowEnd) {
		Instant windowStart = windowEnd.minus(WINDOW);
		Instant baselineStart = windowStart.minus(BASELINE);
		UUID previous = TenantContext.currentOrNone();
		TenantContext.set(tenantId);
		try {
			return transactions.execute(tx -> {
				Map<String, long[][]> histograms = histograms(baselineStart, windowStart, windowEnd);
				return histograms.entrySet().stream().map(entry -> {
					Status status = status(entry.getKey(), entry.getValue()[0], entry.getValue()[1], windowStart,
							windowEnd);
					save(tenantId, status);
					publish(tenantId, status);
					return status;
				}).toList();
			});
		}
		finally {
			if (previous == null) {
				TenantContext.clear();
			}
			else {
				TenantContext.set(previous);
			}
		}
	}

	@Override
	public List<Status> latest(UUID tenantId) {
		// El tenant lo aplica la RLS (TenantContext del JWT); el filtro explícito es defensa redundante.
		return jdbc.query("""
				SELECT DISTINCT ON (model_version) model_version, psi, status, window_start, window_end,
				       actual_count, baseline_count
				FROM analytics.model_drift
				WHERE tenant_id = ?
				ORDER BY model_version, window_end DESC
				""", (rs, i) -> new Status(rs.getString("model_version"),
						rs.getObject("psi") == null ? null : rs.getBigDecimal("psi").doubleValue(),
						Level.valueOf(rs.getString("status")), rs.getTimestamp("window_start").toInstant(),
						rs.getTimestamp("window_end").toInstant(), rs.getInt("actual_count"),
						rs.getInt("baseline_count")), tenantId);
	}

	/** Por modelo: [0] = línea base, [1] = semana actual, en tramos de 100 puntos. */
	private Map<String, long[][]> histograms(Instant baselineStart, Instant windowStart, Instant windowEnd) {
		Map<String, long[][]> histograms = new LinkedHashMap<>();
		jdbc.query("""
				SELECT model_version, LEAST(score / 100, 9) AS bin, created_at >= ? AS current, count(*) AS n
				FROM assessment.scores_v1
				WHERE created_at >= ? AND created_at < ?
				GROUP BY 1, 2, 3
				ORDER BY 1
				""", rs -> {
			long[][] histogram = histograms.computeIfAbsent(rs.getString("model_version"),
					m -> new long[2][PopulationStabilityIndex.BINS]);
			histogram[rs.getBoolean("current") ? 1 : 0][rs.getInt("bin")] += rs.getLong("n");
		}, Timestamp.from(windowStart), Timestamp.from(baselineStart), Timestamp.from(windowEnd));
		return histograms;
	}

	private Status status(String model, long[] baseline, long[] actual, Instant windowStart, Instant windowEnd) {
		int baselineCount = (int) java.util.Arrays.stream(baseline).sum();
		int actualCount = (int) java.util.Arrays.stream(actual).sum();
		if (baselineCount < minSample || actualCount < minSample) {
			return new Status(model, null, Level.INSUFICIENTE, windowStart, windowEnd, actualCount, baselineCount);
		}
		double psi = PopulationStabilityIndex.psi(baseline, actual);
		return new Status(model, psi, PopulationStabilityIndex.level(psi), windowStart, windowEnd, actualCount,
				baselineCount);
	}

	private void save(UUID tenantId, Status status) {
		jdbc.update("""
				INSERT INTO analytics.model_drift (id, tenant_id, model_version, window_start, window_end, psi,
				  actual_count, baseline_count, status, computed_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, now())
				ON CONFLICT (tenant_id, model_version, window_end) DO UPDATE SET psi = EXCLUDED.psi,
				  actual_count = EXCLUDED.actual_count, baseline_count = EXCLUDED.baseline_count,
				  status = EXCLUDED.status, computed_at = EXCLUDED.computed_at
				""", UUID.randomUUID(), tenantId, status.modelVersion(), Timestamp.from(status.windowStart()),
				Timestamp.from(status.windowEnd()), status.psi(), status.actualCount(), status.baselineCount(),
				status.level().name());
	}

	/** Un gauge por tenant y modelo; queda vacío (NaN) mientras la muestra sea insuficiente. */
	private void publish(UUID tenantId, Status status) {
		gauges.computeIfAbsent(tenantId + "|" + status.modelVersion(), key -> {
			AtomicReference<Double> value = new AtomicReference<>();
			Gauge.builder("doggycredit.scoring.psi", value, ref -> ref.get() == null ? Double.NaN : ref.get())
				.description("PSI semanal del score contra las 4 semanas anteriores (D-08)")
				.tags("tenant", tenantId.toString(), "model", status.modelVersion())
				.register(meters);
			return value;
		}).set(status.psi());
	}

}
