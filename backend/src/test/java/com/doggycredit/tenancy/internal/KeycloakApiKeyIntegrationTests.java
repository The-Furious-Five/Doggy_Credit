package com.doggycredit.tenancy.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * D-09 — criterio de "Lista cuando", contra un Keycloak real (misma imagen y
 * mismo realm generado que infra/compose.yaml): una clave creada por
 * {@link KeycloakAdminClients} obtiene un token client_credentials con el
 * tenant correcto, el rol API_CLIENT y la audiencia de la API; al revocarla,
 * ya no obtiene tokens.
 */
class KeycloakApiKeyIntegrationTests {

	private static final String ADMIN_SECRET = "test-admin-secret-" + UUID.randomUUID();

	static final GenericContainer<?> KEYCLOAK = new GenericContainer<>(
			DockerImageName.parse("quay.io/keycloak/keycloak:26.4"))
		.withCommand("start-dev", "--http-port=8080", "--import-realm")
		.withEnv("KC_HTTP_RELATIVE_PATH", "/auth")
		// Heap acotado: sin esto Keycloak toma el 70 % de la memoria del host de Docker.
		.withEnv("JAVA_OPTS_KC_HEAP", "-Xms128m -Xmx512m")
		.withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
		.withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin")
		.withEnv("DC_KEYCLOAK_API_ADMIN_SECRET", ADMIN_SECRET)
		.withEnv("DC_SEED_ADMIN_PASSWORD", "x")
		.withEnv("DC_SEED_ANALYST_PASSWORD", "x")
		.withEnv("DC_SEED_ADMIN_PASSWORD_2", "x")
		.withEnv("DC_SEED_M2M_SECRET", "x")
		.withEnv("DC_SEED_M2M_SECRET_2", "x")
		.withCopyFileToContainer(MountableFile.forHostPath(repoPath("infra/keycloak/doggycredit-realm.json")),
				"/opt/keycloak/data/import/doggycredit-realm.json")
		.withExposedPorts(8080)
		.waitingFor(Wait.forHttp("/auth/realms/doggycredit/.well-known/openid-configuration")
			.withStartupTimeout(Duration.ofMinutes(5)));

	private static KeycloakAdminClients keycloak;
	private static RestClient http;

	@BeforeAll
	static void start() {
		KEYCLOAK.start();
		String base = "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080) + "/auth";
		keycloak = new KeycloakAdminClients(new KeycloakAdminClients.Properties(base, "doggycredit",
				"doggycredit-api-admin", ADMIN_SECRET, base + "/realms/doggycredit", Duration.ofSeconds(10)));
		http = RestClient.builder().baseUrl(base).requestFactory(new SimpleClientHttpRequestFactory()).build();
	}

	@AfterAll
	static void stop() {
		KEYCLOAK.stop();
	}

	@Test
	void laClaveCreadaObtieneTokenDelTenantYLaRevocacionLaInvalida() {
		UUID tenant = UUID.fromString("9c7e7c1a-0000-4000-8000-000000000002");
		String clientId = "dc-test-" + UUID.randomUUID().toString().substring(0, 12);
		String secret = "s3cr3t-" + UUID.randomUUID();

		UUID keycloakId = keycloak.create(
				new KeycloakClients.NewClient(clientId, secret, tenant, ApiKey.Environment.SANDBOX, "Prueba"));
		assertThat(keycloak.tokenUrl()).endsWith("/auth/realms/doggycredit/protocol/openid-connect/token");

		Token token = requestToken(clientId, secret);
		assertThat(token.status()).isEqualTo(200);
		JsonNode claims = token.claims();
		assertThat(claims.get("tenant_id").asString()).isEqualTo(tenant.toString());
		assertThat(claims.get("dc_entorno").asString()).isEqualTo("sandbox");
		assertThat(claims.get("realm_access").get("roles").valueStream().map(JsonNode::asString))
			.contains("API_CLIENT");
		assertThat(audiences(claims)).contains("doggycredit-api");
		assertThat(claims.get("sub")).as("TenantClaimFilter exige sub").isNotNull();

		keycloak.delete(keycloakId);
		assertThat(requestToken(clientId, secret).status()).isEqualTo(401);

		// Revocar dos veces no falla: Keycloak ya no tiene el cliente.
		keycloak.delete(keycloakId);
	}

	@Test
	void elTokenDeAdministracionNoSirveContraLaApi() {
		Token token = requestToken("doggycredit-api-admin", ADMIN_SECRET);
		assertThat(token.status()).isEqualTo(200);
		assertThat(token.claims().has("tenant_id")).isFalse();
		assertThat(audiences(token.claims())).doesNotContain("doggycredit-api");
	}

	@Test
	void listaUsuariosHumanosYRolesSoloDelTenantSolicitado() {
		UUID progreso = UUID.fromString("9c7e7c1a-0000-4000-8000-000000000001");
		UUID fintech = UUID.fromString("9c7e7c1a-0000-4000-8000-000000000002");

		var progresoMembers = keycloak.listTeamMembers(progreso);
		assertThat(progresoMembers).extracting(KeycloakClients.TeamMember::email)
			.containsExactlyInAnyOrder("admin.progreso@doggycredit.invalid", "analista.progreso@doggycredit.invalid");
		assertThat(progresoMembers).extracting(KeycloakClients.TeamMember::rol)
			.contains("TENANT_ADMIN", "ANALYST");
		assertThat(keycloak.listTeamMembers(fintech)).extracting(KeycloakClients.TeamMember::email)
			.containsExactly("admin.vecina@doggycredit.invalid");
	}

	private static Token requestToken(String clientId, String secret) {
		LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "client_credentials");
		form.add("client_id", clientId);
		form.add("client_secret", secret);
		return http.post()
			.uri("/realms/doggycredit/protocol/openid-connect/token")
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.body(form)
			.exchange((request, response) -> new Token(response.getStatusCode().value(),
					new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8)));
	}

	private static java.util.List<String> audiences(JsonNode claims) {
		JsonNode aud = claims.get("aud");
		if (aud == null) {
			return java.util.List.of();
		}
		return aud.isArray() ? aud.valueStream().map(JsonNode::asString).toList() : java.util.List.of(aud.asString());
	}

	private record Token(int status, String body) {

		JsonNode claims() {
			String accessToken = JsonMapper.builder().build().readTree(body).get("access_token").asString();
			String payload = accessToken.split("\\.")[1];
			return JsonMapper.builder().build().readTree(new String(Base64.getUrlDecoder().decode(payload),
					StandardCharsets.UTF_8));
		}

	}

	private static Path repoPath(String relative) {
		for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
			if (Files.exists(dir.resolve(relative))) {
				return dir.resolve(relative);
			}
		}
		throw new IllegalStateException("No se encontró " + relative + " subiendo desde " + Path.of("").toAbsolutePath());
	}

}
