package com.doggycredit.shared;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.ErrorResponseException;

/**
 * Autorización por rol dentro de un endpoint, con su propio código de error
 * ({@link ErrorCode#INSUFFICIENT_ROLE}). No se hace en la cadena de Spring Security
 * porque su AccessDeniedHandler responde siempre INVALID_TENANT_CLAIM (ver
 * ProblemDetailAccessDeniedHandler), que a un usuario con otro rol le diría que su
 * tenant es inválido. Cuando exista la autorización por rol general (A-10), esto
 * puede moverse ahí.
 */
public final class RoleGuard {

	private RoleGuard() {
	}

	/** @throws ErrorResponseException 403 INSUFFICIENT_ROLE si el usuario no tiene el rol. */
	public static void require(String role) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		boolean granted = authentication != null && authentication.getAuthorities().stream()
			.map(GrantedAuthority::getAuthority)
			.anyMatch(("ROLE_" + role)::equals);
		if (!granted) {
			throw new ErrorResponseException(HttpStatus.FORBIDDEN,
					ErrorCode.INSUFFICIENT_ROLE.toProblemDetail("La operación requiere el rol " + role + "."), null);
		}
	}

}
