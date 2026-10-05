package com.doggycredit.billing.internal;

import org.springframework.stereotype.Service;

import com.doggycredit.billing.Feature;
import com.doggycredit.billing.FeatureNotInPlanException;
import com.doggycredit.billing.PlanCatalog;
import com.doggycredit.billing.PlanGate;
import com.doggycredit.billing.PlanInfo;

@Service
class PlanGateImpl implements PlanGate {
    private final PlanCatalog plans;
    
    public PlanGateImpl(PlanCatalog plans) {
        this.plans = plans;
    }

    @Override
    public void requireFeature(String planId, Feature feature) {
        PlanInfo plan = plans.requireById(planId);
        boolean allowed = switch (feature) {
            case SCORING_AVANZADO -> plan.scoringAvanzado();
            case WEBHOOKS -> plan.webhooks();
            case ANALYTICS -> plan.analytics();
        };
        if (!allowed) {
            throw new FeatureNotInPlanException(feature.name(), plan.nombre());
        }
    }
}
