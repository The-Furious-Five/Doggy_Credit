package com.doggycredit.tenancy.internal;

import java.util.UUID;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import com.doggycredit.shared.ErrorCode;

/**
 * D-09: puerto hacia la administración de clientes de Keycloak. Cada clave de
 * API es un cliente {@code client_credentials} cuyo token lleva el
 * {@code tenant_id} fijo y el rol {@code API_CLIENT}.
 */
interface KeycloakClients {

	/**
	 * Registra el cliente y devuelve su id interno de Keycloak.
	 * @throws KeycloakUnavailableException si Keycloak no responde o rechaza la operación;
	 *         en ese caso no queda ningún cliente a medio crear.
	 */
	UUID create(NewClient client);

	/** Borra el cliente; si ya no existe, no hace nada. */
	void delete(UUID keycloakId);

	/** Lista usuarios humanos y roles del tenant; nunca confía solo en el filtro remoto. */
	List<TeamMember> listTeamMembers(UUID tenantId);

	/** Endpoint público de token, para que el integrador sepa dónde canjear la clave. */
	String tokenUrl();

	record NewClient(String clientId, String secret, UUID tenantId, ApiKey.Environment environment, String name) {
	}

	record TeamMember(String nombre, String email, String rol, boolean activo) { }

	/** 502 PROVIDER_UNAVAILABLE: la clave no cambió y se puede reintentar. */
	class KeycloakUnavailableException extends ErrorResponseException {

		private final String reason;

		KeycloakUnavailableException(String reason, Throwable cause) {
			super(HttpStatus.BAD_GATEWAY, ErrorCode.PROVIDER_UNAVAILABLE.toProblemDetail(
					"El proveedor de identidad no respondió. La clave no cambió; intenta de nuevo en unos minutos."),
					cause);
			this.reason = reason;
		}

		@Override
		public String getMessage() {
			return reason;
		}

	}

}
