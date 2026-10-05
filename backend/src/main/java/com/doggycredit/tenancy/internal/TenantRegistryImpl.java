package com.doggycredit.tenancy.internal;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.doggycredit.tenancy.TenantId;
import com.doggycredit.tenancy.TenantInfo;
import com.doggycredit.tenancy.TenantNotFoundException;
import com.doggycredit.tenancy.TenantRegistry;

/**
 * HAB-03: implementación del registro maestro de tenants.
 */
@Service
class TenantRegistryImpl implements TenantRegistry {

    private final TenantRepository tenants;

    TenantRegistryImpl(TenantRepository tenants) {
        this.tenants = tenants;
    }

    @Override
    public Optional<TenantInfo> findById(TenantId id) {
        return tenants.findById(id.value()).map(this::toInfo);
    }

    @Override
    public TenantInfo requireById(TenantId id) {
        return findById(id).orElseThrow(() -> new TenantNotFoundException(id));
    }

    @Override
    public List<TenantInfo> findAll() {
        return tenants.findAll().stream().map(this::toInfo).collect(java.util.stream.Collectors.toList());
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public TenantInfo update(TenantId id, String plan, Boolean activo) {
        Tenant tenant = tenants.findById(id.value()).orElseThrow(() -> new TenantNotFoundException(id));
        if (plan != null) {
            tenant.setPlan(plan);
        }
        if (activo != null) {
            tenant.setActivo(activo);
        }
        return toInfo(tenants.save(tenant));
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public TenantInfo updateName(TenantId id, String nombre) {
        Tenant tenant = tenants.findById(id.value()).orElseThrow(() -> new TenantNotFoundException(id));
        tenant.setNombre(nombre.trim());
        return toInfo(tenants.save(tenant));
    }

    private TenantInfo toInfo(Tenant t) {
        return new TenantInfo(t.getId(), t.getNombre(), t.getNit(),
                t.getTipo(), t.getPlan(), t.getPais(), t.getMoneda(), t.isActivo());
    }
}
