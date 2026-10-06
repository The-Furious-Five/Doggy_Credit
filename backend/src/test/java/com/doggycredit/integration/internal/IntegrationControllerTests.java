package com.doggycredit.integration.internal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.doggycredit.TestcontainersConfiguration;
import com.doggycredit.consent.Source;
import com.doggycredit.integration.ConnectorGateway;
import com.doggycredit.integration.SourceConnector;
import com.doggycredit.integration.SourceDisabledException;
import com.doggycredit.shared.TenantContext;

/** B-06 con los tenants demo de seed/tenants.yaml: Progreso Andino (FREE) y Fintech Vecina (PRO). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class IntegrationControllerTests {

	private static final String FREE = "9c7e7c1a-0000-4000-8000-000000000001";
	private static final String PRO = "9c7e7c1a-0000-4000-8000-000000000002";

	@Autowired
	MockMvc mvc;

	@Autowired
	ConnectorGateway gateway;

	@Test
	void catalogoDeSeisConEstadoSegunElPlan() throws Exception {
		mvc.perform(get("/integraciones").with(as(FREE)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(6))
			.andExpect(jsonPath("$[0].fuente").value("BURO"))
			.andExpect(jsonPath("$[0].estado").value("CONECTADO"))
			.andExpect(jsonPath("$[1].estado").value("REQUIERE_PRO"))
			.andExpect(jsonPath("$[2].estado").value("REQUIERE_PRO"))
			.andExpect(jsonPath("$[3].estado").value("PROXIMAMENTE"))
			.andExpect(jsonPath("$[4].estado").value("PROXIMAMENTE"))
			.andExpect(jsonPath("$[5].estado").value("PROXIMAMENTE"));
	}

	@Test
	void tenantFreeConUnaIntegracionNoPuedeActivarLaSegunda() throws Exception {
		set(FREE, "BANCO", true)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PLAN_LIMIT_REACHED"));
		mvc.perform(get("/integraciones").with(as(FREE))).andExpect(jsonPath("$[1].estado").value("REQUIERE_PRO"));
	}

	@Test
	void tenantProActivaYDesactiva() throws Exception {
		set(PRO, "WALLET", true).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CONECTADO"));
		mvc.perform(get("/integraciones").with(as(PRO))).andExpect(jsonPath("$[2].estado").value("CONECTADO"));
		set(PRO, "WALLET", false).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("DISPONIBLE"));
	}

	@Test
	void nadaSinImplementacionNiBuroApagado() throws Exception {
		set(PRO, "OPEN_BANKING", true).andExpect(status().isBadRequest());
		set(PRO, "BURO", false).andExpect(status().isBadRequest());
		set(PRO, "BURO", true).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CONECTADO"));
	}

	@Test
	void laEvaluacionNoConsultaUnaFuenteInactiva() {
		AtomicInteger calls = new AtomicInteger();
		SourceConnector<String> wallet = new SourceConnector<>() {
			@Override
			public Source source() {
				return Source.WALLET;
			}

			@Override
			public String fetch(UUID tenantId, String documento) {
				calls.incrementAndGet();
				return "movimientos";
			}
		};
		TenantContext.set(UUID.fromString(FREE));
		try {
			assertThatThrownBy(() -> gateway.fetch(wallet, UUID.fromString(FREE), "CI-DEMO-0001001"))
				.isInstanceOf(SourceDisabledException.class);
			Assertions.assertThat(calls).hasValue(0);
		}
		finally {
			TenantContext.clear();
		}
	}

	private ResultActions set(String tenant, String fuente, boolean activa) throws Exception {
		return mvc.perform(put("/integraciones/{fuente}", fuente).with(as(tenant))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"activa\":" + activa + "}"));
	}

	private static RequestPostProcessor as(String tenant) {
		return jwt().jwt(token -> token.subject("admin-b06").claim("tenant_id", tenant));
	}

}
