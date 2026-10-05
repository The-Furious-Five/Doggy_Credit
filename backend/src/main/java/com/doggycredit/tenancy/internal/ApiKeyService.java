package com.doggycredit.tenancy.internal;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.ErrorResponseException;

import com.doggycredit.audit.AuditTrail;
import com.doggycredit.shared.ErrorCode;
import com.doggycredit.tenancy.TenantId;
import com.doggycredit.tenancy.TenantRegistry;

/**
 * D-09: alta, listado y revocación de claves de API. El tenant siempre es el
 * del JWT de quien opera (lo recibe del controlador, que lo toma del token).
 */
@Service
class ApiKeyService {

	/** Planes con API_AVANZADA. TODO(A-02): reemplazar por PlanGate cuando los entitlements estén en BD. */
	private static final Set<String> PLANS_WITH_PRODUCTION_KEYS = Set.of("PRO", "ENTERPRISE");

	private static final SecureRandom RANDOM = new SecureRandom();

	private final ApiKeyRepository keys;
	private final KeycloakClients keycloak;
	private final TenantRegistry tenants;
	private final AuditTrail audit;

	ApiKeyService(ApiKeyRepository keys, KeycloakClients keycloak, TenantRegistry tenants, AuditTrail audit) {
		this.keys = keys;
		this.keycloak = keycloak;
		this.tenants = tenants;
		this.audit = audit;
	}

	@Transactional(readOnly = true)
	List<ApiKey> list() {
		return keys.findByRevocadaEnIsNullOrderByCreadaEnDesc();
	}

	/**
	 * Crea el cliente en Keycloak y luego la fila. Si la fila no se puede
	 * guardar, se borra el cliente: nunca queda una credencial sin registro.
	 */
	@Transactional
	Created create(UUID tenantId, String nombre, ApiKey.Environment environment, String createdBy) {
		if (environment == ApiKey.Environment.PRODUCCION
				&& !PLANS_WITH_PRODUCTION_KEYS.contains(tenants.requireById(TenantId.of(tenantId)).plan())) {
			throw new ErrorResponseException(HttpStatus.FORBIDDEN, ErrorCode.FEATURE_NOT_IN_PLAN.toProblemDetail(
					"Las claves de producción requieren el plan Pro. Puedes crear una clave de sandbox."), null);
		}
		String clientId = "dc-" + (environment == ApiKey.Environment.PRODUCCION ? "live" : "test") + "-"
				+ HexFormat.of().formatHex(randomBytes(6));
		String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(32));
		UUID keycloakId = keycloak.create(
				new KeycloakClients.NewClient(clientId, secret, tenantId, environment, nombre));
		try {
			ApiKey key = keys.save(new ApiKey(tenantId, nombre, clientId, keycloakId, environment,
					secret.substring(secret.length() - 4), createdBy, Instant.now().truncatedTo(ChronoUnit.MICROS)));
			keys.flush();
			audit.record(tenantId, "API_KEY_CREATED", "API_KEY", key.id());
			return new Created(key, secret, keycloak.tokenUrl());
		}
		catch (RuntimeException e) {
			keycloak.delete(keycloakId);
			throw e;
		}
	}

	/**
	 * Primero se borra en Keycloak (deja de emitir tokens), después se marca
	 * revocada. Si Keycloak falla, la clave sigue vigente y se puede reintentar.
	 */
	@Transactional
	void revoke(UUID tenantId, UUID id) {
		ApiKey key = keys.findByIdAndRevocadaEnIsNull(id)
			.orElseThrow(() -> new ErrorResponseException(HttpStatus.NOT_FOUND, ErrorCode.API_KEY_NOT_FOUND
				.toProblemDetail("La clave no existe, ya fue revocada o pertenece a otro tenant."), null));
		keycloak.delete(key.keycloakId());
		key.revoke(Instant.now().truncatedTo(ChronoUnit.MICROS));
		audit.record(tenantId, "API_KEY_REVOKED", "API_KEY", key.id());
	}

	private static byte[] randomBytes(int length) {
		byte[] bytes = new byte[length];
		RANDOM.nextBytes(bytes);
		return bytes;
	}

	record Created(ApiKey key, String secret, String tokenUrl) {
	}

}
