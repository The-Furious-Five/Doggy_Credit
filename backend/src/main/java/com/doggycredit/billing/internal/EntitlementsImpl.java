package com.doggycredit.billing.internal;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.doggycredit.billing.Entitlements;
import com.doggycredit.billing.PlanCatalog;
import com.doggycredit.billing.TenantPlans;

@Service
class EntitlementsImpl implements Entitlements {

    private final TenantPlans tenants;
    private final PlanCatalog plans;

    EntitlementsImpl(TenantPlans tenants, PlanCatalog plans) {
        this.tenants = tenants;
        this.plans = plans;
    }

    @Override
    public Granted of(UUID tenantId) {
        String plan = tenants.planOf(tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));
        return Granted.of(plans.requireById(plan));
    }

}
