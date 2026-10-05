package com.doggycredit.tenancy.internal;

import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import com.doggycredit.shared.contract.api.TenantApi;
import com.doggycredit.shared.contract.model.TenantActual;
import com.doggycredit.tenancy.TenantId;
import com.doggycredit.tenancy.TenantInfo;
import com.doggycredit.tenancy.TenantRegistry;
import com.doggycredit.billing.PlanCatalog;
import com.doggycredit.billing.UsageMeter;
import java.util.List;
import com.doggycredit.shared.contract.model.MiembroEquipo;
import com.doggycredit.shared.contract.model.PreferenciasTenant;
import com.doggycredit.shared.contract.model.UsoTenant;
import com.doggycredit.shared.contract.model.OrganizacionTenantPatch;

@RestController
public class TenantController implements TenantApi {
    private final TenantRegistry tenantRegistry;
    private final TenantPreferencesService preferences;
    private final PlanCatalog planCatalog;
    private final UsageMeter usageMeter;

    TenantController(TenantRegistry tenantRegistry, TenantPreferencesService preferences,
            PlanCatalog planCatalog, UsageMeter usageMeter) {
        this.tenantRegistry = tenantRegistry;
        this.preferences = preferences;
        this.planCatalog = planCatalog;
        this.usageMeter = usageMeter;
    }

    @Override
    public ResponseEntity<TenantActual> obtenerTenantActual() {
        var jwt = ((JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication()).getToken();
        UUID tenantId = UUID.fromString(jwt.getClaimAsString("tenant_id"));
        
        TenantInfo tenantInfo = tenantRegistry.requireById(TenantId.of(tenantId));
        
        TenantActual tenantActual = new TenantActual()
                .id(tenantInfo.id())
                .nombre(tenantInfo.nombre())
                .nit(tenantInfo.nit())
                .tipo(TenantActual.TipoEnum.fromValue(tenantInfo.tipo()))
                .plan(TenantActual.PlanEnum.fromValue(tenantInfo.plan()))
                .pais(tenantInfo.pais())
                .moneda(tenantInfo.moneda())
                .activo(tenantInfo.activo());
                
        return ResponseEntity.ok(tenantActual);
    }

    @Override
    public ResponseEntity<List<MiembroEquipo>> listarEquipo() {
        var jwt = ((JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication()).getToken();
        String name = jwt.getClaimAsString("name");
        if (name == null || name.isBlank()) name = jwt.getClaimAsString("preferred_username");
        if (name == null || name.isBlank()) name = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        var realm = jwt.getClaimAsMap("realm_access");
        List<String> roles = realm != null && realm.get("roles") instanceof List<?> values
                ? values.stream().filter(String.class::isInstance).map(String.class::cast).sorted().toList()
                : List.of();
        String role = roles.isEmpty() ? "SIN_ROL" : String.join(", ", roles);
        MiembroEquipo member = new MiembroEquipo().nombre(name).email(email).rol(role)
                .estado(MiembroEquipo.EstadoEnum.ACTIVO);
        return ResponseEntity.ok(List.of(member));
    }

    @Override
    public ResponseEntity<PreferenciasTenant> obtenerPreferenciasTenant() {
        UUID id = currentTenantId();
        var current = preferences.get(id);
        return ResponseEntity.ok(new PreferenciasTenant().enmascararDatos(current.enmascararDatos()));
    }

    @Override
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<PreferenciasTenant> actualizarPreferenciasTenant(@Valid @RequestBody PreferenciasTenant body) {
        if (body.getEnmascararDatos() == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "enmascararDatos es obligatorio");
        }
        UUID id = currentTenantId();
        var updated = preferences.update(id, body.getEnmascararDatos());
        return ResponseEntity.ok(new PreferenciasTenant().enmascararDatos(updated.enmascararDatos()));
    }

    @Override
    public ResponseEntity<UsoTenant> obtenerUsoTenant() {
        UUID id = currentTenantId();
        TenantInfo tenant = tenantRegistry.requireById(TenantId.of(id));
        var plan = planCatalog.requireById(tenant.plan());
        return ResponseEntity.ok(new UsoTenant()
                .evaluacionesMes(usageMeter.currentMonthEvaluations(id))
                .limiteMensual(plan.evaluacionesMes()));
    }

    @Override
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<TenantActual> actualizarOrganizacionTenant(@Valid @RequestBody OrganizacionTenantPatch body) {
        if (body.getNombre() == null || body.getNombre().trim().length() < 2 || body.getNombre().trim().length() > 100) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "nombre debe tener entre 2 y 100 caracteres");
        }
        TenantInfo updated = tenantRegistry.updateName(TenantId.of(currentTenantId()), body.getNombre());
        TenantActual response = new TenantActual()
                .id(updated.id()).nombre(updated.nombre()).nit(updated.nit())
                .tipo(TenantActual.TipoEnum.fromValue(updated.tipo()))
                .plan(TenantActual.PlanEnum.fromValue(updated.plan()))
                .pais(updated.pais()).moneda(updated.moneda()).activo(updated.activo());
        return ResponseEntity.ok(response);
    }

    private UUID currentTenantId() {
        var jwt = ((JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication()).getToken();
        return UUID.fromString(jwt.getClaimAsString("tenant_id"));
    }
}
