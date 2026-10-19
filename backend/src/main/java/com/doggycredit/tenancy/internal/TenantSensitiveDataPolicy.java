package com.doggycredit.tenancy.internal;

import java.util.Set;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import com.doggycredit.shared.SensitiveDataPolicy;

/**
 * A-10 (LOS-71): enmascara para {@code ANALYST} cuando el tenant tiene activo
 * «Ocultar documentos y montos a roles sin permiso». {@code TENANT_ADMIN} y
 * {@code PLATFORM_ADMIN} ven el dato completo; las integraciones M2M
 * ({@code API_CLIENT}) también, porque consultan sus propias solicitudes.
 */
@Component
class TenantSensitiveDataPolicy implements SensitiveDataPolicy {

	private static final Set<String> FULL_ACCESS = Set.of("ROLE_TENANT_ADMIN", "ROLE_PLATFORM_ADMIN");

	private final TenantPreferencesService preferences;

	TenantSensitiveDataPolicy(TenantPreferencesService preferences) {
		this.preferences = preferences;
	}

	@Override
	public boolean maskForCurrentUser() {
		if (!(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken jwt)) {
			return false;
		}
		Set<String> roles = jwt.getAuthorities().stream().map(GrantedAuthority::getAuthority)
			.collect(java.util.stream.Collectors.toSet());
		if (!roles.contains("ROLE_ANALYST") || roles.stream().anyMatch(FULL_ACCESS::contains)) {
			return false;
		}
		String tenant = jwt.getToken().getClaimAsString("tenant_id");
		return tenant != null && preferences.get(UUID.fromString(tenant)).enmascararDatos();
	}

}
