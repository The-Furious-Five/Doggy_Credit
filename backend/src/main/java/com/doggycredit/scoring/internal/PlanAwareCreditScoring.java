package com.doggycredit.scoring.internal;

import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.doggycredit.scoring.BasicScoreResult;
import com.doggycredit.scoring.BasicScorecard;
import com.doggycredit.scoring.CreditScoring;
import com.doggycredit.scoring.ScorecardInput;
import com.doggycredit.scoring.ScoringFeatures;
import com.doggycredit.tenancy.TenantId;
import com.doggycredit.tenancy.TenantInfo;
import com.doggycredit.tenancy.TenantRegistry;

@Service
class PlanAwareCreditScoring implements CreditScoring {

	private static final Logger log = LoggerFactory.getLogger(PlanAwareCreditScoring.class);

	/** Planes con SCORING_AVANZADO. TODO(A-02): reemplazar por los entitlements de billing. */
	private static final Set<String> ADVANCED_PLANS = Set.of("PRO", "ENTERPRISE");

	private final TenantRegistry tenants;
	private final BasicScorecard basic;
	private final AdvancedScorer advanced;

	PlanAwareCreditScoring(TenantRegistry tenants, BasicScorecard basic, AdvancedScorer advanced) {
		this.tenants = tenants;
		this.basic = basic;
		this.advanced = advanced;
	}

	@Override
	public BasicScoreResult score(UUID tenantId, ScorecardInput basicInput, ScoringFeatures features) {
		// Un tenant que no está en el registro no tiene plan PRO: falla cerrado al básico.
		boolean advancedPlan = tenants.findById(TenantId.of(tenantId))
			.map(TenantInfo::plan)
			.map(ADVANCED_PLANS::contains)
			.orElse(false);
		if (!advancedPlan) {
			return basic.score(basicInput);
		}
		try {
			return advanced.score(features);
		}
		catch (RuntimeException e) {
			log.warn("Modelo avanzado no disponible para el tenant {}; se usa el scorecard básico", tenantId, e);
			return basic.score(basicInput);
		}
	}

}
