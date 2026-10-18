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

	/** Lista las cuentas humanas vinculadas al tenant y sus roles de realm. */
	List<TeamMember> listTeamMembers(UUID tenantId);

	/**
	 * A-10: crea la cuenta humana con el {@code tenant_id} y el rol, y le pide definir
	 * su contraseña en el primer ingreso. El correo con el enlace es de mejor esfuerzo.
	 * @throws MemberAlreadyExistsException si ya hay una cuenta con ese email
	 * @throws KeycloakUnavailableException si Keycloak no responde; no queda una cuenta a medias
	 */
	TeamMember invite(UUID tenantId, NewMember member);

	/** Borra el cliente; si ya no existe, no hace nada. */
	void delete(UUID keycloakId);

	/** Endpoint público de token, para que el integrador sepa dónde canjear la clave. */
	String tokenUrl();

	record NewClient(String clientId, String secret, UUID tenantId, ApiKey.Environment environment, String name) {
	}

	record TeamMember(String nombre, String email, String rol, boolean enabled, boolean invited) {
	}

	record NewMember(String nombre, String email, String rol) {
	}

	/** 409 MEMBER_ALREADY_EXISTS: Keycloak ya tiene una cuenta con ese email o usuario. */
	class MemberAlreadyExistsException extends ErrorResponseException {

		MemberAlreadyExistsException() {
			super(HttpStatus.CONFLICT, ErrorCode.MEMBER_ALREADY_EXISTS.toProblemDetail(
					"Ya existe una cuenta con ese email."), null);
		}

	}

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
