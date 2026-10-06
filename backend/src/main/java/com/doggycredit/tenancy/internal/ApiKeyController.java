package com.doggycredit.tenancy.internal;

import java.net.URI;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.RestController;

import com.doggycredit.shared.ErrorCode;
import com.doggycredit.shared.RoleGuard;
import com.doggycredit.shared.contract.api.ClavesApiApi;
import com.doggycredit.shared.contract.model.ClaveApi;
import com.doggycredit.shared.contract.model.ClaveApiCreada;
import com.doggycredit.shared.contract.model.ClaveApiNueva;

/**
 * D-09 (LOS-98): claves de API. Ruta y DTOs vienen del contrato OpenAPI.
 * Solo TENANT_ADMIN; el tenant sale del JWT, nunca del cuerpo ni de la ruta.
 */
@RestController
class ApiKeyController implements ClavesApiApi {

	static final String REQUIRED_ROLE = "TENANT_ADMIN";

	private final ApiKeyService keys;

	ApiKeyController(ApiKeyService keys) {
		this.keys = keys;
	}

	@Override
	public ResponseEntity<List<ClaveApi>> listarClavesApi() {
		requireRole(REQUIRED_ROLE);
		return ResponseEntity.ok(keys.list().stream().map(ApiKeyController::toDto).toList());
	}

	@Override
	public ResponseEntity<ClaveApiCreada> crearClaveApi(ClaveApiNueva body) {
		JwtAuthenticationToken jwt = requireRole(REQUIRED_ROLE);
		String nombre = body == null || body.getNombre() == null ? "" : body.getNombre().strip();
		if (nombre.length() < 2 || nombre.length() > 80) {
			throw new ErrorResponseException(HttpStatus.BAD_REQUEST,
					ErrorCode.VALIDATION_ERROR.toProblemDetail("El nombre debe tener entre 2 y 80 caracteres."), null);
		}
		ApiKey.Environment environment = body.getEntorno() == ClaveApiNueva.EntornoEnum.PRODUCCION
				? ApiKey.Environment.PRODUCCION : ApiKey.Environment.SANDBOX;
		UUID tenantId = UUID.fromString(jwt.getToken().getClaimAsString("tenant_id"));

		ApiKeyService.Created created = keys.create(tenantId, nombre, environment, jwt.getToken().getSubject());
		ApiKey key = created.key();
		ClaveApiCreada result = new ClaveApiCreada().id(key.id())
			.nombre(key.nombre())
			.clientId(key.clientId())
			.sufijoSecreto(key.sufijoSecreto())
			.entorno(ClaveApiCreada.EntornoEnum.fromValue(key.entorno().name().toLowerCase()))
			.creadaEn(key.creadaEn().atOffset(ZoneOffset.UTC))
			.secreto(created.secret())
			.tokenUrl(URI.create(created.tokenUrl()));
		// El secreto viaja una sola vez: que ningún intermediario lo guarde.
		return ResponseEntity.status(HttpStatus.CREATED).header("Cache-Control", "no-store").body(result);
	}

	@Override
	public ResponseEntity<Void> revocarClaveApi(UUID id) {
		JwtAuthenticationToken jwt = requireRole(REQUIRED_ROLE);
		keys.revoke(UUID.fromString(jwt.getToken().getClaimAsString("tenant_id")), id);
		return ResponseEntity.noContent().build();
	}

	private static ClaveApi toDto(ApiKey key) {
		return new ClaveApi().id(key.id())
			.nombre(key.nombre())
			.clientId(key.clientId())
			.sufijoSecreto(key.sufijoSecreto())
			.entorno(ClaveApi.EntornoEnum.fromValue(key.entorno().name().toLowerCase()))
			.creadaEn(key.creadaEn().atOffset(ZoneOffset.UTC));
	}

	/**
	 * Autorización por rol dentro del endpoint, con su propio código de error
	 * (mismo criterio que C-04): el AccessDeniedHandler de la cadena de
	 * seguridad responde siempre INVALID_TENANT_CLAIM.
	 */
	private static JwtAuthenticationToken requireRole(String role) {
		return RoleGuard.requireJwt(role);
	}

}
