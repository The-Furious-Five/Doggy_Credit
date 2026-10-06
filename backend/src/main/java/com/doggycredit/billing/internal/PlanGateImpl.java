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
    public boolean isAvailable(String planId, Feature feature) {
        return plans.findById(planId).map(plan -> isAvailable(plan, feature)).orElse(false);
    }

    private static boolean isAvailable(PlanInfo plan, Feature feature) {
        return switch (feature) {
            case SCORING_AVANZADO -> plan.scoringAvanzado();
            case API_PRODUCCION -> plan.apiProduccion();
            case WEBHOOKS -> plan.webhooks();
            case ANALYTICS -> plan.analytics();
            case RECOMENDACIONES -> plan.recomendaciones();
        };
    }

    @Override
    public void requireFeature(String planId, Feature feature) {
        PlanInfo plan = plans.requireById(planId);
        if (!isAvailable(plan, feature)) {
            throw new FeatureNotInPlanException(feature.name(), plan.nombre());
        }
    }
}
