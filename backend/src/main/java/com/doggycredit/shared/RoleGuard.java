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
 * porque su AccessDeniedHandler responde siempre INVALID_TENANT_CLAIM (ver
 * ProblemDetailAccessDeniedHandler), que a un usuario con otro rol le diría que su
 * tenant es inválido. Los controladores usan este guard para devolver el error de
 * negocio INSUFFICIENT_ROLE de forma consistente.
 */
public final class RoleGuard {

	private RoleGuard() {
	}

	/** @throws ErrorResponseException 403 INSUFFICIENT_ROLE si el usuario no tiene el rol. */
	public static void require(String role) {
		requireJwt(role);
	}

	/** Exige el rol y devuelve el JWT autenticado para operaciones que necesitan sus claims. */
	public static JwtAuthenticationToken requireJwt(String role) {
		return requireAnyJwt(role);
	}

	/**
	 * Exige al menos uno de los roles (ej. ANALYST o TENANT_ADMIN) y devuelve el JWT.
	 * @throws ErrorResponseException 403 INSUFFICIENT_ROLE si no tiene ninguno.
	 */
	public static JwtAuthenticationToken requireAnyJwt(String... roles) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		java.util.Set<String> accepted = java.util.Arrays.stream(roles).map(role -> "ROLE_" + role)
			.collect(java.util.stream.Collectors.toSet());
		boolean granted = authentication != null && authentication.getAuthorities().stream()
			.map(GrantedAuthority::getAuthority)
			.anyMatch(accepted::contains);
		if (!granted || !(authentication instanceof JwtAuthenticationToken jwt)) {
			throw new ErrorResponseException(HttpStatus.FORBIDDEN, ErrorCode.INSUFFICIENT_ROLE.toProblemDetail(
					"La operación requiere el rol " + String.join(" o ", roles) + "."), null);
		}
		return jwt;
	}

}
