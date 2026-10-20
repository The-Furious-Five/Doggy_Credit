package com.doggycredit.tenancy.internal;

import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import com.doggycredit.shared.contract.api.TenantApi;
import com.doggycredit.shared.RoleGuard;
import com.doggycredit.shared.contract.model.TenantActual;
import com.doggycredit.tenancy.PlanIntegrations;
import com.doggycredit.tenancy.TenantId;
import com.doggycredit.tenancy.TenantInfo;
import com.doggycredit.tenancy.TenantRegistry;
import com.doggycredit.billing.Entitlements;
import com.doggycredit.billing.PlanCatalog;
import com.doggycredit.billing.UsageMeter;
import java.util.List;
import com.doggycredit.shared.contract.model.MiembroEquipo;
import com.doggycredit.shared.contract.model.MiembroEquipoNuevo;
import com.doggycredit.shared.contract.model.PreferenciasTenant;
import com.doggycredit.shared.contract.model.UsoTenant;
import com.doggycredit.shared.contract.model.OrganizacionTenantPatch;

@RestController
public class TenantController implements TenantApi {
    private final TenantRegistry tenantRegistry;
    private final TenantPreferencesService preferences;
    private final PlanCatalog planCatalog;
    private final UsageMeter usageMeter;
    private final KeycloakClients keycloak;
    private final TeamInvitationService invitations;
    private final Entitlements entitlements;
    private final PlanIntegrations integrations;

    TenantController(TenantRegistry tenantRegistry, TenantPreferencesService preferences,
            PlanCatalog planCatalog, UsageMeter usageMeter, KeycloakClients keycloak,
            TeamInvitationService invitations, Entitlements entitlements, PlanIntegrations integrations) {
        this.integrations = integrations;
        this.tenantRegistry = tenantRegistry;
        this.preferences = preferences;
        this.planCatalog = planCatalog;
        this.usageMeter = usageMeter;
        this.keycloak = keycloak;
        this.invitations = invitations;
        this.entitlements = entitlements;
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
        // A-01 (LOS-64): límites, funciones y uso del mes del plan vigente, leídos en cada petición.
        Entitlements.Granted granted = entitlements.of(tenantId);
        tenantActual.evaluacionesMes(usageMeter.currentMonthEvaluations(tenantId))
                .limiteMensual(granted.evaluacionesMes())
                .limiteUsuarios(granted.maxUsuarios())
                .limiteIntegraciones(granted.maxIntegraciones())
                .funcionalidades(granted.features().stream().sorted()
                        .map(feature -> TenantActual.FuncionalidadesEnum.fromValue(feature.name())).toList());
        return ResponseEntity.ok(tenantActual);
    }

    @Override
    public ResponseEntity<List<MiembroEquipo>> listarEquipo() {
        UUID tenantId = currentTenantId();
        List<MiembroEquipo> members = keycloak.listTeamMembers(tenantId).stream()
                .map(TenantController::toContract)
                .toList();
        return ResponseEntity.ok(members);
    }

    /** A-10 (LOS-71): el tenant sale del JWT; el cuerpo solo trae a la persona y su rol. */
    @Override
    public ResponseEntity<MiembroEquipo> invitarMiembroEquipo(@Valid @RequestBody MiembroEquipoNuevo body) {
        RoleGuard.require("TENANT_ADMIN");
        String nombre = body.getNombre() == null ? "" : body.getNombre().strip();
        String email = body.getEmail() == null ? "" : body.getEmail().strip();
        if (nombre.length() < 2 || nombre.length() > 100 || !email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")
                || body.getRol() == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "nombre, email y rol son obligatorios y válidos");
        }
        var invited = invitations.invite(currentTenantId(),
                new KeycloakClients.NewMember(nombre, email, body.getRol().getValue()));
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(toContract(invited));
    }

    private static MiembroEquipo toContract(KeycloakClients.TeamMember member) {
        MiembroEquipo.EstadoEnum estado = !member.enabled() ? MiembroEquipo.EstadoEnum.DESACTIVADO
                : member.invited() ? MiembroEquipo.EstadoEnum.INVITADO : MiembroEquipo.EstadoEnum.ACTIVO;
        return new MiembroEquipo().nombre(member.nombre()).email(member.email()).rol(member.rol()).estado(estado);
    }

    @Override
    public ResponseEntity<PreferenciasTenant> obtenerPreferenciasTenant() {
        UUID id = currentTenantId();
        var current = preferences.get(id);
        return ResponseEntity.ok(new PreferenciasTenant().enmascararDatos(current.enmascararDatos()));
    }

    @Override
    public ResponseEntity<PreferenciasTenant> actualizarPreferenciasTenant(@Valid @RequestBody PreferenciasTenant body) {
        RoleGuard.require("TENANT_ADMIN");
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
                .limiteMensual(plan.evaluacionesMes())
                .integracionesActivas(integrations.activeCount(id))
                .limiteIntegraciones(plan.maxIntegraciones()));
    }

    @Override
    public ResponseEntity<TenantActual> actualizarOrganizacionTenant(@Valid @RequestBody OrganizacionTenantPatch body) {
        RoleGuard.require("TENANT_ADMIN");
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
