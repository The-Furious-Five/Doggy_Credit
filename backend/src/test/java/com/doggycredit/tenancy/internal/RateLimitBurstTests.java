package com.doggycredit.tenancy.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.doggycredit.TestcontainersConfiguration;
import com.doggycredit.tenancy.RateLimits;

/**
 * A-07 — criterio de "Lista cuando": una ráfaga por encima del límite del
 * plan recibe 429 RATE_LIMITED con Retry-After. Límites bajos solo aquí; el
 * resto de las pruebas corre con límites altos (src/test/resources/config).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = { "doggycredit.rate-limit.requests-per-minute.FREE=5",
		"doggycredit.rate-limit.requests-per-minute.PRO=8" })
@AutoConfigureMockMvc
class RateLimitBurstTests {

	private static final String FREE = "9c7e7c1a-0000-4000-8000-000000000001";
	private static final String PRO = "9c7e7c1a-0000-4000-8000-000000000002";

	@Autowired
	MockMvc mvc;

	@Autowired
	RateLimits rateLimits;

	@Test
	void unaRafagaPorEncimaDelLimiteDelPlanRecibe429ConRetryAfter() throws Exception {
		for (int i = 0; i < 5; i++) {
			mvc.perform(anyEndpoint(FREE)).andExpect(status().isNotFound())
				.andExpect(header().string("X-RateLimit-Remaining", String.valueOf(4 - i)));
		}
		mvc.perform(anyEndpoint(FREE))
			.andExpect(status().isTooManyRequests())
			.andExpect(header().exists("Retry-After"))
			.andExpect(jsonPath("$.code").value("RATE_LIMITED"));

		RateLimits.RateLimitUsage usage = rateLimits.usage(UUID.fromString(FREE));
		assertThat(usage.limitPerMinute()).isEqualTo(5);
		assertThat(usage.usedThisMinute()).isEqualTo(5);
		int retryAfter = Integer.parseInt(mvc.perform(anyEndpoint(FREE)).andReturn().getResponse()
			.getHeader("Retry-After"));
		assertThat(retryAfter).isBetween(1, 60);
	}

	@Test
	void elLimiteEsPorTenantYSegunSuPlan() throws Exception {
		// Otro tenant tiene su propio bucket, del tamaño de su plan (PRO = 8 aquí).
		for (int i = 0; i < 8; i++) {
			mvc.perform(anyEndpoint(PRO)).andExpect(status().isNotFound());
		}
		mvc.perform(anyEndpoint(PRO)).andExpect(status().isTooManyRequests());
		assertThat(rateLimits.usage(UUID.fromString(PRO)).limitPerMinute()).isEqualTo(8);

		// Un tenant fuera del registro recibe el límite más bajo.
		String unknown = UUID.randomUUID().toString();
		assertThat(rateLimits.usage(UUID.fromString(unknown)).limitPerMinute()).isEqualTo(5);
	}

	@Test
	void sinTokenNoSeConsumeDelBucket() throws Exception {
		mvc.perform(get("/actuator/health")).andExpect(status().isOk());
		mvc.perform(get("/credit-assessments/{id}", UUID.randomUUID())).andExpect(status().isUnauthorized());
	}

	private static MockHttpServletRequestBuilder anyEndpoint(String tenant) {
		return get("/credit-assessments/{id}", UUID.randomUUID())
			.with(jwt().jwt(token -> token.subject("rafaga").claim("tenant_id", tenant)));
	}

}
