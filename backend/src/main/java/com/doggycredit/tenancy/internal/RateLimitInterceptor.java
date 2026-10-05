package com.doggycredit.tenancy.internal;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.handler.MappedInterceptor;

import com.doggycredit.shared.ErrorCode;
import com.doggycredit.shared.TenantContext;

import io.github.bucket4j.ConsumptionProbe;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * A-07: toda petición autenticada consume del bucket de su tenant (el del
 * JWT, que TenantClaimFilter ya dejó en {@link TenantContext}). Sin tenant
 * —p. ej. {@code /actuator/health}— no se limita aquí. Al agotarse responde
 * 429 RATE_LIMITED con {@code Retry-After}, independiente de la cuota mensual.
 * <p>
 * Se registra como {@link MappedInterceptor} desde {@link Registration} (y no
 * como {@code WebMvcConfigurer}) para que los slices {@code @WebMvcTest} no
 * lo levanten sin su limitador.
 */
class RateLimitInterceptor implements HandlerInterceptor {

	private final TenantRateLimiter limiter;

	RateLimitInterceptor(TenantRateLimiter limiter) {
		this.limiter = limiter;
	}

	@Configuration(proxyBeanMethods = false)
	static class Registration {

		@Bean
		MappedInterceptor rateLimitInterceptor(TenantRateLimiter limiter) {
			return new MappedInterceptor(null, new RateLimitInterceptor(limiter));
		}

	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		UUID tenantId = TenantContext.currentOrNone();
		if (tenantId == null) {
			return true;
		}
		ConsumptionProbe probe = limiter.tryConsume(tenantId);
		if (probe.isConsumed()) {
			response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
			return true;
		}
		long retryAfter = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill() + 999_999_999));
		throw new RateLimitedException(retryAfter);
	}

	static class RateLimitedException extends ErrorResponseException {

		private final HttpHeaders headers = new HttpHeaders();

		RateLimitedException(long retryAfterSeconds) {
			super(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.RATE_LIMITED.toProblemDetail(
					"Superaste el límite de solicitudes por minuto de tu plan. Reintenta en " + retryAfterSeconds
							+ " s."), null);
			headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
		}

		@Override
		public HttpHeaders getHeaders() {
			return headers;
		}

	}

}
