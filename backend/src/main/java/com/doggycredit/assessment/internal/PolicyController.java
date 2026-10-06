package com.doggycredit.assessment.internal;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.RestController;

import com.doggycredit.shared.ErrorCode;
import com.doggycredit.shared.contract.api.PoliticasApi;
import com.doggycredit.shared.contract.model.Politica;
import com.doggycredit.shared.contract.model.PoliticaCambio;

/** C-05: GET y PUT /politicas, solo TENANT_ADMIN. El tenant sale del JWT. */
@RestController
class PolicyController implements PoliticasApi {

	private static final String TENANT_ADMIN = "TENANT_ADMIN";

	private final PolicyService policies;

	PolicyController(PolicyService policies) {
		this.policies = policies;
	}

	@Override
	public ResponseEntity<List<Politica>> listarPoliticas() {
		requireRole(TENANT_ADMIN);
		return ResponseEntity.ok(policies.list(currentTenantId()).stream().map(PolicyController::toContract).toList());
	}

	@Override
	public ResponseEntity<Politica> actualizarPolitica(UUID id, PoliticaCambio body) {
		requireRole(TENANT_ADMIN);
		return ResponseEntity.ok(toContract(policies.setActive(currentTenantId(), id, body.getActiva())));
	}

	private static Politica toContract(Policy policy) {
		return new Politica()
			.id(policy.id())
			.codigo(policy.code())
			.nombre(policy.name())
			.condicion(policy.conditionText())
			.resultado(Politica.ResultadoEnum.fromValue(policy.effect()))
			.activa(policy.active());
	}

	private static UUID currentTenantId() {
		var jwt = ((JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication()).getToken();
		return UUID.fromString(jwt.getClaimAsString("tenant_id"));
	}

	/**
	 * Autorización por rol dentro del endpoint, con su propio código de error (mismo patrón que
	 * AuditQueryController): la cadena de Spring Security respondería INVALID_TENANT_CLAIM.
	 */
	private static void requireRole(String role) {
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
