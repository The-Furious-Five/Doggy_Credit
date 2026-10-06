package com.doggycredit.tenancy.internal;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * D-09: {@link KeycloakClients} sobre la API REST de administración de
 * Keycloak, autenticado como el cliente confidencial
 * {@code doggycredit-api-admin} (cuenta de servicio con roles de
 * {@code realm-management}; ver {@code scripts/generate_realm.py}).
 * <p>
 * El cliente que crea no lleva el mapper de atributo de usuario de los
 * clientes sembrados: fija {@code tenant_id} con un claim constante, así el
 * tenant queda atado a la credencial y no a un atributo editable.
 */
@Component
class KeycloakAdminClients implements KeycloakClients {

	private static final Duration TOKEN_MARGIN = Duration.ofSeconds(30);
	private static final Set<String> TEAM_ROLES = Set.of("TENANT_ADMIN", "ANALYST", "PLATFORM_ADMIN");

	private final Properties properties;
	private final RestClient http;
	private String accessToken;
	private Instant accessTokenExpiry = Instant.MIN;

	KeycloakAdminClients(Properties properties) {
		this.properties = properties;
		HttpClient client = HttpClient.newBuilder()
			.version(HttpClient.Version.HTTP_1_1)
			.connectTimeout(properties.timeout())
			.build();
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
		factory.setReadTimeout(properties.timeout());
		this.http = RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(factory).build();
	}

	@Override
	public UUID create(NewClient client) {
		UUID keycloakId;
		try {
			URI location = http.post()
				.uri("/admin/realms/{realm}/clients", properties.realm())
				.headers(h -> h.setBearerAuth(token()))
				.contentType(MediaType.APPLICATION_JSON)
				.body(representation(client))
				.retrieve()
				.toBodilessEntity()
				.getHeaders()
				.getLocation();
			if (location == null) {
				throw new KeycloakUnavailableException("Keycloak no devolvió la ubicación del cliente creado", null);
			}
			String path = location.getPath();
			keycloakId = UUID.fromString(path.substring(path.lastIndexOf('/') + 1));
		}
		catch (RestClientException | IllegalArgumentException e) {
			throw new KeycloakUnavailableException("No se pudo crear el cliente en Keycloak", e);
		}
		try {
			grantApiClientRole(keycloakId);
			return keycloakId;
		}
		catch (RuntimeException e) {
			// Compensación: sin el rol, la clave no sirve; no se deja un cliente huérfano.
			delete(keycloakId);
			throw e instanceof KeycloakUnavailableException k ? k
					: new KeycloakUnavailableException("No se pudo asignar el rol API_CLIENT", e);
		}
	}

	@Override
	public void delete(UUID keycloakId) {
		try {
			http.delete()
				.uri("/admin/realms/{realm}/clients/{id}", properties.realm(), keycloakId)
				.headers(h -> h.setBearerAuth(token()))
				.retrieve()
				.toBodilessEntity();
		}
		catch (HttpClientErrorException.NotFound alreadyGone) {
			// Ya no existe: la revocación es idempotente.
		}
		catch (RestClientException e) {
			throw new KeycloakUnavailableException("No se pudo borrar el cliente en Keycloak", e);
		}
	}

	@Override
	public List<TeamMember> listTeamMembers(UUID tenantId) {
		try {
			List<TeamMember> members = new ArrayList<>();
			int first = 0;
			while (true) {
				int page = first;
				List<?> users = http.get()
					.uri(uri -> uri.path("/admin/realms/{realm}/users")
						.queryParam("first", page)
						.queryParam("max", 100)
						.queryParam("briefRepresentation", false)
						.build(properties.realm()))
					.headers(h -> h.setBearerAuth(token()))
					.retrieve()
					.body(List.class);
				if (users == null || users.isEmpty()) break;
				for (Object value : users) {
					if (value instanceof Map<?, ?> user && belongsToTenant(user, tenantId) && !isServiceAccount(user)) {
						members.add(toTeamMember(user));
					}
				}
				first += users.size();
				if (users.size() < 100) break;
			}
			return members.stream().sorted(Comparator.comparing(TeamMember::nombre, String.CASE_INSENSITIVE_ORDER))
				.toList();
		}
		catch (RestClientException e) {
			throw new KeycloakUnavailableException("No se pudo consultar el equipo en Keycloak", e);
		}
	}

	private TeamMember toTeamMember(Map<?, ?> user) {
		String userId = String.valueOf(user.get("id"));
		String name = (stringValue(user.get("firstName")) + " " + stringValue(user.get("lastName"))).strip();
		if (name.isBlank()) name = stringValue(user.get("username"));
		List<?> mappings = http.get()
			.uri("/admin/realms/{realm}/users/{user}/role-mappings/realm", properties.realm(), userId)
			.headers(h -> h.setBearerAuth(token()))
			.retrieve().body(List.class);
		String roles = mappings == null ? "" : mappings.stream().filter(Map.class::isInstance).map(Map.class::cast)
			.map(role -> role.get("name")).filter(String.class::isInstance).map(String.class::cast)
			.filter(TEAM_ROLES::contains).sorted().collect(java.util.stream.Collectors.joining(", "));
		return new TeamMember(name, stringValue(user.get("email")), roles.isBlank() ? "SIN_ROL" : roles,
			Boolean.TRUE.equals(user.get("enabled")));
	}

	private static boolean belongsToTenant(Map<?, ?> user, UUID tenantId) {
		Object attributes = user.get("attributes");
		if (!(attributes instanceof Map<?, ?> values)) return false;
		Object raw = values.get("tenant_id");
		if (raw instanceof Collection<?> collection) return collection.stream().anyMatch(tenantId.toString()::equals);
		return tenantId.toString().equals(raw);
	}

	private static boolean isServiceAccount(Map<?, ?> user) {
		return stringValue(user.get("username")).startsWith("service-account-")
				|| user.containsKey("serviceAccountClientId");
	}

	private static String stringValue(Object value) {
		return value instanceof String string ? string : "";
	}

	@Override
	public String tokenUrl() {
		return properties.publicIssuerUri() + "/protocol/openid-connect/token";
	}

	private void grantApiClientRole(UUID keycloakId) {
		try {
			Map<?, ?> serviceAccount = http.get()
				.uri("/admin/realms/{realm}/clients/{id}/service-account-user", properties.realm(), keycloakId)
				.headers(h -> h.setBearerAuth(token()))
				.retrieve()
				.body(Map.class);
			Map<?, ?> role = http.get()
				.uri("/admin/realms/{realm}/roles/{role}", properties.realm(), "API_CLIENT")
				.headers(h -> h.setBearerAuth(token()))
				.retrieve()
				.body(Map.class);
			http.post()
				.uri("/admin/realms/{realm}/users/{user}/role-mappings/realm", properties.realm(),
						serviceAccount.get("id"))
				.headers(h -> h.setBearerAuth(token()))
				.contentType(MediaType.APPLICATION_JSON)
				.body(List.of(Map.of("id", role.get("id"), "name", role.get("name"))))
				.retrieve()
				.toBodilessEntity();
		}
		catch (RestClientException | NullPointerException e) {
			throw new KeycloakUnavailableException("No se pudo asignar el rol API_CLIENT", e);
		}
	}

	private static Map<String, Object> representation(NewClient client) {
		String tenant = client.tenantId().toString();
		String environment = client.environment().name().toLowerCase();
		return Map.ofEntries(
				Map.entry("clientId", client.clientId()),
				Map.entry("name", client.name()),
				Map.entry("description", "Clave de API de DoggyCredit (" + environment + ")"),
				Map.entry("enabled", true),
				Map.entry("protocol", "openid-connect"),
				Map.entry("publicClient", false),
				Map.entry("clientAuthenticatorType", "client-secret"),
				Map.entry("secret", client.secret()),
				Map.entry("standardFlowEnabled", false),
				Map.entry("implicitFlowEnabled", false),
				Map.entry("directAccessGrantsEnabled", false),
				Map.entry("serviceAccountsEnabled", true),
				Map.entry("defaultClientScopes", List.of("basic", "roles")),
				Map.entry("optionalClientScopes", List.of()),
				Map.entry("attributes", Map.of("dc.tenant_id", tenant, "dc.entorno", environment)),
				Map.entry("protocolMappers", List.of(
						constantClaim("tenant_id", tenant),
						constantClaim("dc_entorno", environment),
						Map.of("name", "api-audience", "protocol", "openid-connect",
								"protocolMapper", "oidc-audience-mapper",
								"config", Map.of("included.custom.audience", "doggycredit-api",
										"access.token.claim", "true", "id.token.claim", "false",
										"introspection.token.claim", "true")))));
	}

	private static Map<String, Object> constantClaim(String claim, String value) {
		return Map.of("name", claim, "protocol", "openid-connect", "protocolMapper", "oidc-hardcoded-claim-mapper",
				"config", Map.of("claim.name", claim, "claim.value", value, "jsonType.label", "String",
						"access.token.claim", "true", "id.token.claim", "false",
						"userinfo.token.claim", "false", "introspection.token.claim", "true"));
	}

	/** Token de la cuenta de servicio administradora, reutilizado hasta poco antes de vencer. */
	private synchronized String token() {
		if (accessToken != null && Instant.now().isBefore(accessTokenExpiry)) {
			return accessToken;
		}
		if (properties.clientSecret() == null || properties.clientSecret().isBlank()) {
			throw new KeycloakUnavailableException("Falta DC_KEYCLOAK_API_ADMIN_SECRET", null);
		}
		LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "client_credentials");
		form.add("client_id", properties.clientId());
		form.add("client_secret", properties.clientSecret());
		try {
			Map<?, ?> response = http.post()
				.uri("/realms/{realm}/protocol/openid-connect/token", properties.realm())
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(form)
				.retrieve()
				.onStatus(HttpStatusCode::isError, (request, reply) -> {
					throw new KeycloakUnavailableException(
							"Keycloak rechazó la cuenta de servicio administradora: HTTP " + reply.getStatusCode(), null);
				})
				.body(Map.class);
			accessToken = (String) response.get("access_token");
			long expiresIn = ((Number) response.get("expires_in")).longValue();
			accessTokenExpiry = Instant.now().plusSeconds(expiresIn).minus(TOKEN_MARGIN);
			return accessToken;
		}
		catch (RestClientException | NullPointerException | ClassCastException e) {
			throw new KeycloakUnavailableException("No se pudo obtener el token de administración de Keycloak", e);
		}
	}

	/**
	 * @param baseUrl         Keycloak por la red interna, con el path relativo ({@code http://keycloak:8080/auth}).
	 * @param publicIssuerUri issuer público del realm, el que ven los integradores.
	 */
	@ConfigurationProperties("doggycredit.keycloak.admin")
	record Properties(String baseUrl, String realm, String clientId, String clientSecret, String publicIssuerUri,
			Duration timeout) {

		Properties {
			timeout = timeout == null ? Duration.ofSeconds(3) : timeout;
		}

	}

}
