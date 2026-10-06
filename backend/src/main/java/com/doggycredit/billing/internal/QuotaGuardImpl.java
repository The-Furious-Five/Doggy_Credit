package com.doggycredit.billing.internal;

import java.util.UUID;
import org.springframework.stereotype.Service;
import com.doggycredit.billing.PlanCatalog;
import com.doggycredit.billing.PlanInfo;
import com.doggycredit.billing.QuotaExceededException;
import com.doggycredit.billing.QuotaGuard;
import com.doggycredit.billing.UsageMeter;

@Service
class QuotaGuardImpl implements QuotaGuard {
    private final PlanCatalog plans;
    private final UsageMeter usage;
    
    public QuotaGuardImpl(PlanCatalog plans, UsageMeter usage) {
        this.plans = plans;
        this.usage = usage;
    }
    
    @Override
    public void requireEvaluationQuota(UUID tenantId, String planId) {
        PlanInfo plan = plans.requireById(planId);
        boolean reserved = usage.tryRecordEvaluation(tenantId, plan.evaluacionesMes());
        if (!reserved && plan.evaluacionesMes() > 0) {  // 0 means unlimited
            throw new QuotaExceededException(plan.evaluacionesMes());
        }
    }
}
