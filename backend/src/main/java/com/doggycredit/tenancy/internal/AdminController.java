package com.doggycredit.tenancy.internal;

import java.util.List;
import java.util.UUID;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.stream.Collectors;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.doggycredit.shared.contract.api.AdminApi;
import com.doggycredit.shared.contract.model.TenantActual;
import com.doggycredit.shared.contract.model.TenantPatch;
import com.doggycredit.shared.RoleGuard;
import com.doggycredit.tenancy.TenantId;
import com.doggycredit.tenancy.TenantInfo;
import com.doggycredit.tenancy.TenantRegistry;
import com.doggycredit.billing.OperatorUsageQuery;
import com.doggycredit.billing.PlanCatalog;

@RestController
public class AdminController implements AdminApi {
    private final TenantRegistry registry;
    private final PlanChangeService planChanges;
    private final TenantStatusService statuses;
    private final OperatorUsageQuery usage;
    private final PlanCatalog plans;

    AdminController(TenantRegistry registry, PlanChangeService planChanges, TenantStatusService statuses,
            OperatorUsageQuery usage, PlanCatalog plans) {
        this.registry = registry;
        this.planChanges = planChanges;
        this.statuses = statuses;
        this.usage = usage;
        this.plans = plans;
    }

    @Override
    public ResponseEntity<List<TenantActual>> listarTenants() {
        RoleGuard.require("PLATFORM_ADMIN");
        var evaluations = usage.evaluationsForMonth(YearMonth.now(ZoneOffset.UTC));
        List<TenantActual> response = registry.findAll().stream()
                .map(info -> {
                    TenantActual dto = toDto(info);
                    dto.setEvaluacionesMes(evaluations.getOrDefault(info.id(), 0));
                    dto.setLimiteMensual(plans.requireById(info.plan()).evaluacionesMes());
                    return dto;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<TenantActual> actualizarTenant(@PathVariable UUID id, @Valid @RequestBody TenantPatch body) {
        RoleGuard.require("PLATFORM_ADMIN");
        if (body.getPlan() == null && body.getActivo() == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Debe indicar plan o activo");
        }
        // A-11: el pago es simulado en el MVP; el operador confirma y el plan cambia en el momento.
        if (body.getPlan() != null) {
            planChanges.changeNow(id, body.getPlan().getValue());
        }
        TenantInfo updated = body.getActivo() != null
                ? statuses.setActive(id, body.getActivo())
                : registry.requireById(new TenantId(id));
        return ResponseEntity.ok(toDto(updated));
    }

    private TenantActual toDto(TenantInfo info) {
        TenantActual dto = new TenantActual();
        dto.setId(info.id());
        dto.setNombre(info.nombre());
        dto.setNit(info.nit());
        dto.setTipo(TenantActual.TipoEnum.fromValue(info.tipo()));
        dto.setPlan(TenantActual.PlanEnum.fromValue(info.plan()));
        dto.setPais(info.pais());
        dto.setMoneda(info.moneda());
        dto.setActivo(info.activo());
        return dto;
    }
}
