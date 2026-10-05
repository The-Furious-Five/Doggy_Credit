package com.doggycredit.audit.internal;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.doggycredit.TestcontainersConfiguration;
import com.doggycredit.audit.AuditTrail;
import com.doggycredit.shared.TenantContext;

/** C-04 (LOS-84): GET /auditoria — rol, paginación por cursor, filtro por fecha y aislamiento. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AuditQueryControllerTests {

	@Autowired
	MockMvc mvc;

	@Autowired
	AuditTrail audit;

	@Test
	void elAdministradorDelTenantVeSuBitacoraDeLaMasRecienteALaMasAntigua() throws Exception {
		UUID tenant = UUID.randomUUID();
		registrar(tenant, 3);

		mvc.perform(comoAdmin(get("/auditoria"), tenant))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(3))
			.andExpect(jsonPath("$.items[0].secuencia").value(3))
			.andExpect(jsonPath("$.items[2].secuencia").value(1))
			.andExpect(jsonPath("$.items[0].accion").value("APPLICANT_READ"))
			.andExpect(jsonPath("$.items[0].hash").isString())
			.andExpect(jsonPath("$.siguienteCursor").doesNotExist());
	}

	@Test
	void unAnalistaNoPuedeConsultarLaAuditoria() throws Exception {
		mvc.perform(get("/auditoria").with(jwt().jwt(token -> token.subject("analista")
				.claim("tenant_id", UUID.randomUUID().toString())).authorities(new SimpleGrantedAuthority("ROLE_ANALYST"))))
			.andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("INSUFFICIENT_ROLE"));
	}

	@Test
	void paginaPorCursor() throws Exception {
		UUID tenant = UUID.randomUUID();
		registrar(tenant, 3);

		mvc.perform(comoAdmin(get("/auditoria").param("tamano", "2"), tenant))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(2))
			.andExpect(jsonPath("$.siguienteCursor").value(2));

		mvc.perform(comoAdmin(get("/auditoria").param("tamano", "2").param("cursor", "2"), tenant))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].secuencia").value(1))
			.andExpect(jsonPath("$.siguienteCursor").doesNotExist());
	}

	@Test
	void filtraPorFecha() throws Exception {
		UUID tenant = UUID.randomUUID();
		registrar(tenant, 2);

		mvc.perform(comoAdmin(get("/auditoria").param("desde", "2999-01-01T00:00:00Z"), tenant))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(0));
		mvc.perform(comoAdmin(get("/auditoria").param("hasta", "2999-01-01T00:00:00Z"), tenant))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(2));
	}

	@Test
	void unTenantNoVeLaAuditoriaDeOtro() throws Exception {
		UUID tenantA = UUID.randomUUID();
		UUID tenantB = UUID.randomUUID();
		registrar(tenantB, 2);

		mvc.perform(comoAdmin(get("/auditoria"), tenantA))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(0));
	}

	// --- utilidades ------------------------------------------------------------

	private static MockHttpServletRequestBuilder comoAdmin(MockHttpServletRequestBuilder request, UUID tenant) {
		return request.with(jwt().jwt(token -> token.subject("admin").claim("tenant_id", tenant.toString()))
			.authorities(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN")));
	}

	private void registrar(UUID tenant, int cuantos) {
		TenantContext.set(tenant);
		try {
			for (int i = 0; i < cuantos; i++) {
				audit.record(tenant, "APPLICANT_READ", "APPLICANT", UUID.randomUUID());
			}
		}
		finally {
			TenantContext.clear();
		}
	}

}
