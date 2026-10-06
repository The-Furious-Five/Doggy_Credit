package com.doggycredit.assessment.internal;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.RestController;

import com.doggycredit.shared.RoleGuard;
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
		RoleGuard.require(TENANT_ADMIN);
		return ResponseEntity.ok(policies.list(currentTenantId()).stream().map(PolicyController::toContract).toList());
	}

	@Override
	public ResponseEntity<Politica> actualizarPolitica(UUID id, PoliticaCambio body) {
		RoleGuard.require(TENANT_ADMIN);
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

}
