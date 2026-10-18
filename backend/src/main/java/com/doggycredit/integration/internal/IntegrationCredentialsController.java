package com.doggycredit.integration.internal;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.doggycredit.integration.ConnectorCredentials.Provider;
import com.doggycredit.shared.RoleGuard;
import com.doggycredit.shared.contract.api.CredencialesIntegracionesApi;
import com.doggycredit.shared.contract.model.CredencialesIntegracionNuevas;
import com.doggycredit.shared.contract.model.EstadoCredencialesIntegracion;
import com.doggycredit.shared.contract.model.ProveedorCredenciales;

@RestController
class IntegrationCredentialsController implements CredencialesIntegracionesApi {
    private final IntegrationCredentialsService credentials;

    IntegrationCredentialsController(IntegrationCredentialsService credentials) {
        this.credentials = credentials;
    }

    @Override
    public ResponseEntity<EstadoCredencialesIntegracion> obtenerCredencialesIntegracion(ProveedorCredenciales fuente) {
        return ResponseEntity.ok(contract(credentials.state(tenantId(), Provider.valueOf(fuente.getValue()))));
    }

    @Override
    public ResponseEntity<EstadoCredencialesIntegracion> guardarCredencialesIntegracion(
            ProveedorCredenciales fuente, CredencialesIntegracionNuevas body) {
        return ResponseEntity.ok(contract(credentials.save(tenantId(), Provider.valueOf(fuente.getValue()), body.getClave())));
    }

    @Override
    public ResponseEntity<Void> eliminarCredencialesIntegracion(ProveedorCredenciales fuente) {
        credentials.delete(tenantId(), Provider.valueOf(fuente.getValue()));
        return ResponseEntity.noContent().build();
    }

    private static UUID tenantId() {
        return UUID.fromString(RoleGuard.requireJwt("TENANT_ADMIN").getToken().getClaimAsString("tenant_id"));
    }

    private static EstadoCredencialesIntegracion contract(IntegrationCredentialsService.State state) {
        return new EstadoCredencialesIntegracion(state.configured(), state.lastFour());
    }
}
