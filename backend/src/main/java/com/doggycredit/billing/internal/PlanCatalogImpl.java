package com.doggycredit.billing.internal;

import com.doggycredit.billing.PlanCatalog;
import com.doggycredit.billing.PlanInfo;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Implementation of PlanCatalog.
 * LOS-65
 */
@Service
class PlanCatalogImpl implements PlanCatalog {

    private final PlanRepository planRepository;

    PlanCatalogImpl(PlanRepository planRepository) {
        this.planRepository = planRepository;
    }

    @Override
    public Optional<PlanInfo> findById(String planId) {
        return planRepository.findById(planId).map(this::toPlanInfo);
    }

    @Override
    public PlanInfo requireById(String planId) {
        return findById(planId)
            .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + planId));
    }

    private PlanInfo toPlanInfo(Plan plan) {
        return new PlanInfo(
            plan.getId(),
            plan.getNombre(),
            plan.getEvaluacionesMes(),
            plan.getMaxUsuarios(),
            plan.getMaxIntegraciones(),
            plan.isScoringAvanzado(),
            plan.isWebhooks(),
            plan.isAnalytics(),
            plan.isRecomendaciones()
        );
    }
}
