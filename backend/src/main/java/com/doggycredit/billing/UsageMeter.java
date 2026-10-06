package com.doggycredit.billing;

import java.util.UUID;

/**
 * Public interface to measure tenant usage.
 * LOS-65
 */
public interface UsageMeter {
    int currentMonthEvaluations(UUID tenantId);
    /** Atomically reserves one evaluation when the tenant is below its limit. */
    boolean tryRecordEvaluation(UUID tenantId, int monthlyLimit);
}
