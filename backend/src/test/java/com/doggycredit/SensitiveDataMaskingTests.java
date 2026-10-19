package com.doggycredit;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * A-10 (LOS-71): el servidor enmascara documentos y montos para ANALYST cuando el
 * tenant activa «Ocultar documentos y montos a roles sin permiso». No depende de la
 * consola: la misma llamada a la API con el token del analista ya llega enmascarada.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
// Registra tenants y filas propias: se descarta su contenedor para no alterar a
// TenantRegistryTest ni a ProductCatalogTableTests, que cuentan lo sembrado.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SensitiveDataMaskingTests {

	private static final String DOCUMENT = "7712345-LP";

	@Autowired
	MockMvc mvc;

	@Autowired
	PostgreSQLContainer postgres;

	@Test
	void conLaPreferenciaActivaElAnalistaRecibeDocumentoYMontosEnmascarados() throws Exception {
		String tenant = tenant();
		String id = applicant(tenant);

		mvc.perform(get("/solicitantes/{id}", id).with(as(tenant, "ANALYST")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.documento").value("••••••" + DOCUMENT.substring(6)))
			.andExpect(jsonPath("$.ingresosMensuales").value("••••••"));
		mvc.perform(get("/solicitantes").with(as(tenant, "ANALYST")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].documento").value("••••••" + DOCUMENT.substring(6)))
			.andExpect(jsonPath("$.items[0].ingresosMensuales").value("••••••"));
	}

	@Test
	void losAdministradoresYLasIntegracionesVenElDatoCompleto() throws Exception {
		String tenant = tenant();
		String id = applicant(tenant);

		for (String role : new String[] { "TENANT_ADMIN", "PLATFORM_ADMIN", "API_CLIENT" }) {
			mvc.perform(get("/solicitantes/{id}", id).with(as(tenant, role)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.documento").value(DOCUMENT))
				.andExpect(jsonPath("$.ingresosMensuales").value("4500.00"));
		}
	}

	@Test
	void sinLaPreferenciaElAnalistaVeElDatoYLaPreferenciaNoCruzaTenants() throws Exception {
		String tenant = tenant();
		String other = tenant();
		String id = applicant(tenant);
		String otherId = applicant(other);

		mvc.perform(patch("/tenant/actual/preferencias").with(as(tenant, "TENANT_ADMIN"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"enmascararDatos\": false}"))
			.andExpect(status().isOk());

		mvc.perform(get("/solicitantes/{id}", id).with(as(tenant, "ANALYST")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.documento").value(DOCUMENT))
			.andExpect(jsonPath("$.ingresosMensuales").value("4500.00"));
		// El otro tenant conserva el valor por defecto: enmascarar.
		mvc.perform(get("/solicitantes/{id}", otherId).with(as(other, "ANALYST")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.ingresosMensuales").value("••••••"));
	}

	private String tenant() {
		String tenant = UUID.randomUUID().toString();
		TestTenants.register(postgres, tenant, "FREE");
		return tenant;
	}

	/** Lo registra un administrador: el alta devuelve el dato completo a quien lo escribió. */
	private String applicant(String tenant) throws Exception {
		String response = mvc.perform(post("/solicitantes").with(as(tenant, "TENANT_ADMIN"))
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"documento":"%s","actividad":"Comercio","antiguedadActividadMeses":36,
						 "ingresosMensuales":"4500.00","moneda":"BOB","destinoCredito":"Capital de trabajo"}
						""".formatted(DOCUMENT)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.documento").value(DOCUMENT))
			.andReturn().getResponse().getContentAsString();
		return response.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
	}

	private static RequestPostProcessor as(String tenant, String role) {
		return jwt().jwt(token -> token.subject(role.toLowerCase() + "-" + tenant.substring(0, 8))
			.claim("tenant_id", tenant)).authorities(new SimpleGrantedAuthority("ROLE_" + role));
	}

}
