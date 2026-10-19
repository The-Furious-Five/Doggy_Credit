package com.doggycredit.tenancy.internal;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.doggycredit.billing.TenantPlans;
import com.doggycredit.tenancy.TenantId;
import com.doggycredit.tenancy.TenantInfo;
import com.doggycredit.tenancy.TenantRegistry;

/** A-02: el plan vigente sale del registro de tenants en cada consulta, sin caché. */
@Component
class RegistryTenantPlans implements TenantPlans {

	private final TenantRegistry tenants;

	RegistryTenantPlans(TenantRegistry tenants) {
		this.tenants = tenants;
	}

	@Override
	public Optional<String> planOf(UUID tenantId) {
		return tenants.findById(TenantId.of(tenantId)).map(TenantInfo::plan);
	}

}
