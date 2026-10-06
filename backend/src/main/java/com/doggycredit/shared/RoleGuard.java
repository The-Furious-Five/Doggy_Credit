package com.doggycredit.shared;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.ErrorResponseException;

/**
 * Autorización por rol dentro de un endpoint, con su propio código de error
 * ({@link ErrorCode#INSUFFICIENT_ROLE}). No se hace en la cadena de Spring Security
 * porque su AccessDeniedHandler responde INVALID_TENANT_CLAIM, que a un usuario
 * con otro rol le diría que su tenant es inválido. Los controladores protegidos
 * usan este guard para devolver INSUFFICIENT_ROLE.
 */
public final class RoleGuard {

	private RoleGuard() {
	}

	/** @throws ErrorResponseException 403 INSUFFICIENT_ROLE si el usuario no tiene el rol. */
	public static void require(String role) {
		requireJwt(role);
	}

	/** Requiere el rol y devuelve la autenticación JWT para extraer claims de forma segura. */
	public static JwtAuthenticationToken requireJwt(String role) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		boolean granted = authentication != null && authentication.getAuthorities().stream()
			.map(GrantedAuthority::getAuthority)
			.anyMatch(("ROLE_" + role)::equals);
		if (!granted || !(authentication instanceof JwtAuthenticationToken jwt)) {
			throw new ErrorResponseException(HttpStatus.FORBIDDEN,
					ErrorCode.INSUFFICIENT_ROLE.toProblemDetail("La operación requiere el rol " + role + "."), null);
		}
		return jwt;
	}

}
