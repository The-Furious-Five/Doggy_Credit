package com.doggycredit.tenancy.internal;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import com.doggycredit.tenancy.RateLimits;
import com.doggycredit.tenancy.TenantId;
import com.doggycredit.tenancy.TenantInfo;
import com.doggycredit.tenancy.TenantRegistry;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;

/**
 * A-07: un bucket por tenant, en memoria (DEBT-003). Ventana fija de un
 * minuto: el bucket se rellena entero cada minuto, así "consumido" es
 * exactamente lo que muestra la barra de la consola. El plan se vuelve a
 * leer cada minuto, para que un cambio de plan se note sin reiniciar.
 */
@Component
class TenantRateLimiter implements RateLimits {

	private static final Duration WINDOW = Duration.ofMinutes(1);

	private final TenantRegistry tenants;
	private final Properties properties;
	private final Map<UUID, Entry> buckets = new ConcurrentHashMap<>();

	TenantRateLimiter(TenantRegistry tenants, Properties properties) {
		this.tenants = tenants;
		this.properties = properties;
	}

	ConsumptionProbe tryConsume(UUID tenantId) {
		return entry(tenantId).bucket().tryConsumeAndReturnRemaining(1);
	}

	@Override
	public RateLimitUsage usage(UUID tenantId) {
		Entry entry = entry(tenantId);
		long available = entry.bucket().getAvailableTokens();
		return new RateLimitUsage(entry.limit(), (int) Math.max(0, entry.limit() - available));
	}

	private Entry entry(UUID tenantId) {
		Instant now = Instant.now();
		return buckets.compute(tenantId, (id, current) -> {
			if (current != null && now.isBefore(current.planCheckedAt().plus(WINDOW))) {
				return current;
			}
			int limit = limitFor(id);
			if (current != null && current.limit() == limit) {
				return new Entry(limit, current.bucket(), now);
			}
			Bucket bucket = Bucket.builder()
				.addLimit(Bandwidth.builder().capacity(limit).refillIntervally(limit, WINDOW).build())
				.build();
			return new Entry(limit, bucket, now);
		});
	}

	/** Un tenant fuera del registro recibe el límite más bajo (falla cerrado). */
	private int limitFor(UUID tenantId) {
		String plan = tenants.findById(TenantId.of(tenantId)).map(TenantInfo::plan).orElse("FREE");
		Integer limit = properties.requestsPerMinute().get(plan);
		return limit != null ? limit : properties.requestsPerMinute().getOrDefault("FREE", 30);
	}

	private record Entry(int limit, Bucket bucket, Instant planCheckedAt) {
	}

	/** @param requestsPerMinute límite por plan ({@code FREE}, {@code PRO}, {@code ENTERPRISE}). */
	@ConfigurationProperties("doggycredit.rate-limit")
	record Properties(Map<String, Integer> requestsPerMinute) {
	}

}
