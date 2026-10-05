package com.doggycredit.billing;

import java.util.UUID;

/** Verifies that the tenant's quota allows the operation. */
public interface QuotaGuard {
    void requireEvaluationQuota(UUID tenantId, String planId);
}
