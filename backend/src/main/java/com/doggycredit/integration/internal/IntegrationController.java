package com.doggycredit.integration.internal;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.doggycredit.integration.internal.IntegrationService.Integration;
import com.doggycredit.shared.contract.api.IntegracionesApi;
import com.doggycredit.shared.contract.model.FuenteIntegracion;
import com.doggycredit.shared.contract.model.Integracion;
import com.doggycredit.shared.contract.model.IntegracionCambio;

@RestController
class IntegrationController implements IntegracionesApi {

	private final IntegrationService integrations;

	IntegrationController(IntegrationService integrations) {
		this.integrations = integrations;
	}

	@Override
	public ResponseEntity<List<Integracion>> listarIntegraciones() {
		return ResponseEntity.ok(integrations.list(tenantId(jwt())).stream().map(IntegrationController::toContract).toList());
	}

	@Override
	public ResponseEntity<Integracion> actualizarIntegracion(FuenteIntegracion fuente, IntegracionCambio body) {
		Jwt jwt = jwt();
		try {
			return ResponseEntity.ok(toContract(integrations.update(tenantId(jwt),
					IntegrationService.Entry.valueOf(fuente.getValue()), body.getActiva(), jwt.getSubject())));
		}
		catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
		}
	}

	private static Integracion toContract(Integration integration) {
		return new Integracion(FuenteIntegracion.fromValue(integration.entry().name()),
				Integracion.EstadoEnum.fromValue(integration.status().name()));
	}

	private static Jwt jwt() {
		return ((JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication()).getToken();
	}

	private static UUID tenantId(Jwt jwt) {
		return UUID.fromString(jwt.getClaimAsString("tenant_id"));
	}

}
