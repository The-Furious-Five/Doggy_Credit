package com.doggycredit.tenancy.internal;

import java.util.List;
import java.util.UUID;
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

@RestController
public class AdminController implements AdminApi {
    private final TenantRegistry registry;

    AdminController(TenantRegistry registry) {
        this.registry = registry;
    }

    @Override
    public ResponseEntity<List<TenantActual>> listarTenants() {
        RoleGuard.require("PLATFORM_ADMIN");
        List<TenantActual> response = registry.findAll().stream()
                .map(this::toDto)
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
        TenantInfo updated = registry.update(new TenantId(id), 
            body.getPlan() != null ? body.getPlan().getValue() : null, 
            body.getActivo());
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
