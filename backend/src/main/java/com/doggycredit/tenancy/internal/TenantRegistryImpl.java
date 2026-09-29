package com.doggycredit.tenancy.internal;

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

    private TenantInfo toInfo(Tenant t) {
        return new TenantInfo(t.getId(), t.getNombre(), t.getNit(),
                t.getTipo(), t.getPlan(), t.getPais(), t.getMoneda(), t.isActivo());
    }
}
