package com.doggycredit.tenancy.internal;

import java.util.UUID;

record TenantSecurityPreferences(UUID tenantId, boolean enmascararDatos) {
}
