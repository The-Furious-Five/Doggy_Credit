package com.doggycredit.scoring.internal;

import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.doggycredit.billing.Feature;
import com.doggycredit.billing.PlanGate;
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

	private final TenantRegistry tenants;
	private final PlanGate planGate;
	private final BasicScorecard basic;
	private final AdvancedScorer advanced;

	PlanAwareCreditScoring(TenantRegistry tenants, PlanGate planGate, BasicScorecard basic, AdvancedScorer advanced) {
		this.tenants = tenants;
		this.planGate = planGate;
		this.basic = basic;
		this.advanced = advanced;
	}

	@Override
	public BasicScoreResult score(UUID tenantId, ScorecardInput basicInput, ScoringFeatures features) {
		// Un tenant que no está en el registro no tiene plan PRO: falla cerrado al básico.
		boolean advancedPlan = tenants.findById(TenantId.of(tenantId))
			.map(TenantInfo::plan)
			.map(plan -> planGate.isAvailable(plan, Feature.SCORING_AVANZADO))
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

	@Override
	public Optional<BasicScoreResult> rescore(String modelVersion, ScorecardInput basicInput,
			ScoringFeatures features) {
		// Se elige por la versión guardada, no por el plan: un PRO que degradó al básico
		// se recalcula con el básico, como ocurrió.
		if (modelVersion.equals(advanced.version())) {
			return Optional.of(advanced.score(features));
		}
		BasicScoreResult result = basic.score(basicInput);
		return modelVersion.equals(result.modelVersion()) ? Optional.of(result) : Optional.empty();
	}

}
