package com.doggycredit.tenancy.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.doggycredit.TestcontainersConfiguration;
import com.doggycredit.shared.TenantContext;

/**
 * D-09: reglas de las claves de API con Keycloak simulado (el flujo real contra
 * Keycloak está en {@link KeycloakApiKeyIntegrationTests}). Tenants de la
 * semilla: Progreso Andino es FREE y Fintech Vecina es PRO.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ApiKeyControllerTests {

	private static final String FREE = "9c7e7c1a-0000-4000-8000-000000000001";
	private static final String PRO = "9c7e7c1a-0000-4000-8000-000000000002";

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@MockitoBean
	KeycloakClients keycloak;

	@BeforeEach
	void keycloakAnswers() {
		when(keycloak.create(any())).thenAnswer(invocation -> UUID.randomUUID());
		when(keycloak.tokenUrl()).thenReturn("https://localhost/auth/realms/doggycredit/protocol/openid-connect/token");
	}

	@Test
	void unAdminCreaUnaClaveDeSandboxConElTenantDeSuToken() throws Exception {
		String body = create(FREE, "TENANT_ADMIN", "App móvil", "sandbox")
			.andExpect(status().isCreated())
			.andExpect(header().string("Cache-Control", "no-store"))
			.andExpect(jsonPath("$.nombre").value("App móvil"))
			.andExpect(jsonPath("$.entorno").value("sandbox"))
			.andExpect(jsonPath("$.clientId").value(org.hamcrest.Matchers.startsWith("dc-test-")))
			.andExpect(jsonPath("$.secreto").isNotEmpty())
			.andExpect(jsonPath("$.tokenUrl").value(org.hamcrest.Matchers.endsWith("/openid-connect/token")))
			.andReturn().getResponse().getContentAsString();

		ArgumentCaptor<KeycloakClients.NewClient> sent = ArgumentCaptor.forClass(KeycloakClients.NewClient.class);
		verify(keycloak).create(sent.capture());
		assertThat(sent.getValue().tenantId()).isEqualTo(UUID.fromString(FREE));
		assertThat(sent.getValue().secret()).hasSizeGreaterThanOrEqualTo(43);
		assertThat(body).contains("\"sufijoSecreto\":\"" + sent.getValue().secret().substring(39) + "\"");

		// El secreto no se persiste: solo su sufijo.
		String stored = comoTenant(FREE, () -> jdbc.queryForObject(
				"select string_agg(nombre || sufijo_secreto || client_id, ',') from tenancy.api_keys", String.class));
		assertThat(stored).doesNotContain(sent.getValue().secret());
	}

	@Test
	void unAnalistaNoGestionaClaves() throws Exception {
		create(FREE, "ANALYST", "No debería", "sandbox")
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("INSUFFICIENT_ROLE"));
		mvc.perform(get("/api-keys").with(as(FREE, "ANALYST")))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("INSUFFICIENT_ROLE"));
		verify(keycloak, never()).create(any());
	}

	@Test
	void produccionRequiereElPlanPro() throws Exception {
		create(FREE, "TENANT_ADMIN", "Producción", "produccion")
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FEATURE_NOT_IN_PLAN"));
		verify(keycloak, never()).create(any());

		create(PRO, "TENANT_ADMIN", "Producción", "produccion")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.entorno").value("produccion"))
			.andExpect(jsonPath("$.clientId").value(org.hamcrest.Matchers.startsWith("dc-live-")));
	}

	@Test
	void nombreInvalidoEsErrorDeValidacion() throws Exception {
		// Lo rechaza el @Size del DTO generado desde el contrato.
		create(FREE, "TENANT_ADMIN", " ", "sandbox").andExpect(status().isBadRequest());
		// Pasa el @Size pero, sin espacios, queda en 1 carácter.
		create(FREE, "TENANT_ADMIN", "  a  ", "sandbox")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		verify(keycloak, never()).create(any());
	}

	@Test
	void cadaTenantVeSoloSusClavesYElListadoNoMuestraSecretos() throws Exception {
		String mine = idOf(create(PRO, "TENANT_ADMIN", "Del PRO " + UUID.randomUUID(), "sandbox"));
		String theirs = idOf(create(FREE, "TENANT_ADMIN", "Del FREE " + UUID.randomUUID(), "sandbox"));

		String list = mvc.perform(get("/api-keys").with(as(PRO, "TENANT_ADMIN")))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		assertThat(list).contains(mine).doesNotContain(theirs).doesNotContain("secreto\"");

		// Revocar la clave de otro tenant es indistinguible de que no exista.
		mvc.perform(delete("/api-keys/{id}", theirs).with(as(PRO, "TENANT_ADMIN")))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("API_KEY_NOT_FOUND"));
	}

	@Test
	void revocarBorraElClienteYLaSacaDelListado() throws Exception {
		String id = idOf(create(PRO, "TENANT_ADMIN", "A revocar", "sandbox"));

		mvc.perform(delete("/api-keys/{id}", id).with(as(PRO, "TENANT_ADMIN")))
			.andExpect(status().isNoContent());
		verify(keycloak).delete(any());
		mvc.perform(get("/api-keys").with(as(PRO, "TENANT_ADMIN")))
			.andExpect(jsonPath("$[?(@.id == '" + id + "')]").isEmpty());
		mvc.perform(delete("/api-keys/{id}", id).with(as(PRO, "TENANT_ADMIN")))
			.andExpect(status().isNotFound());
	}

	@Test
	void siKeycloakFallaNoQuedaNadaAMedias() throws Exception {
		when(keycloak.create(any())).thenThrow(new KeycloakClients.KeycloakUnavailableException("caído", null));
		String nombre = "Keycloak caído " + UUID.randomUUID();
		create(FREE, "TENANT_ADMIN", nombre, "sandbox")
			.andExpect(status().isBadGateway())
			.andExpect(jsonPath("$.code").value("PROVIDER_UNAVAILABLE"));
		assertThat(comoTenant(FREE, () -> jdbc.queryForObject(
				"select count(*) from tenancy.api_keys where nombre = ?", Integer.class, nombre))).isZero();

		// Revocar con Keycloak caído: la clave sigue vigente y se puede reintentar.
		doAnswer(invocation -> UUID.randomUUID()).when(keycloak).create(any());
		String id = idOf(create(FREE, "TENANT_ADMIN", "Sigue vigente", "sandbox"));
		doThrow(new KeycloakClients.KeycloakUnavailableException("caído", null)).when(keycloak).delete(any());
		mvc.perform(delete("/api-keys/{id}", id).with(as(FREE, "TENANT_ADMIN")))
			.andExpect(status().isBadGateway());
		mvc.perform(get("/api-keys").with(as(FREE, "TENANT_ADMIN")))
			.andExpect(jsonPath("$[?(@.id == '" + id + "')]").isNotEmpty());
	}

	private ResultActions create(String tenant, String role, String nombre, String entorno) throws Exception {
		return mvc.perform(post("/api-keys")
			.with(as(tenant, role))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"nombre":"%s","entorno":"%s"}
					""".formatted(nombre, entorno)));
	}

	private static JwtRequestPostProcessor as(String tenant, String role) {
		return jwt().jwt(token -> token.subject("usuario-" + role).claim("tenant_id", tenant))
			.authorities(new SimpleGrantedAuthority("ROLE_" + role));
	}

	private static String idOf(ResultActions created) throws Exception {
		String body = created.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return body.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
	}

	/** SPIKE-1 (RLS): el JDBC directo corre fuera de una petición HTTP. */
	private <T> T comoTenant(String tenant, java.util.function.Supplier<T> work) {
		TenantContext.set(UUID.fromString(tenant));
		try {
			return work.get();
		}
		finally {
			TenantContext.clear();
		}
	}

}
