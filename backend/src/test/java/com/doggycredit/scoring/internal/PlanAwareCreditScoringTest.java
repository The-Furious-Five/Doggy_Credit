package com.doggycredit.scoring.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.doggycredit.scoring.BasicScoreResult;
import com.doggycredit.scoring.BasicScorecard;
import com.doggycredit.scoring.ScoreBand;
import com.doggycredit.scoring.ScorecardInput;
import com.doggycredit.scoring.ScoringFeatures;
import com.doggycredit.tenancy.TenantId;
import com.doggycredit.tenancy.TenantInfo;
import com.doggycredit.tenancy.TenantRegistry;
import com.doggycredit.billing.Feature;
import com.doggycredit.billing.PlanGate;

/** D-02: qué scoring recibe cada plan, y la degradación si el modelo avanzado falla. */
class PlanAwareCreditScoringTest {

	private static final ScorecardInput INPUT = new ScorecardInput(OptionalInt.of(0), 36, OptionalInt.of(48), BigDecimal.ONE,
			BigDecimal.TEN, 12, Optional.of(true));
	private static final ScoringFeatures FEATURES = new ScoringFeatures(null, null, null, null, null, null, null,
			null, null, null, null, null, null, null, null);
	private static final BasicScoreResult BASIC = new BasicScoreResult(700, ScoreBand.B, List.of(), "basic-v1");
	private static final BasicScoreResult ADVANCED = new BasicScoreResult(640, ScoreBand.B, List.of(), "scoring-v2");

	private final TenantRegistry tenants = mock(TenantRegistry.class);
	private final PlanGate planGate = mock(PlanGate.class);
	private final BasicScorecard basic = mock(BasicScorecard.class);
	private final AdvancedScorer advanced = mock(AdvancedScorer.class);
	private final PlanAwareCreditScoring scoring = new PlanAwareCreditScoring(tenants, planGate, basic, advanced);

	@Test
	void freeUsaElScorecardBasico() {
		UUID tenant = tenantWithPlan("FREE");
		when(planGate.isAvailable("FREE", Feature.SCORING_AVANZADO)).thenReturn(false);
		when(basic.score(INPUT)).thenReturn(BASIC);
		assertThat(scoring.score(tenant, INPUT, FEATURES).modelVersion()).isEqualTo("basic-v1");
		verify(advanced, never()).score(any());
	}

	@Test
	void proUsaElModeloAvanzado() {
		UUID tenant = tenantWithPlan("PRO");
		when(planGate.isAvailable("PRO", Feature.SCORING_AVANZADO)).thenReturn(true);
		when(advanced.score(FEATURES)).thenReturn(ADVANCED);
		assertThat(scoring.score(tenant, INPUT, FEATURES).modelVersion()).isEqualTo("scoring-v2");
		verify(basic, never()).score(any());
	}

	@Test
	void unTenantFueraDelRegistroNoRecibeElModeloAvanzado() {
		UUID tenant = UUID.randomUUID();
		when(tenants.findById(TenantId.of(tenant))).thenReturn(Optional.empty());
		when(basic.score(INPUT)).thenReturn(BASIC);
		assertThat(scoring.score(tenant, INPUT, FEATURES).modelVersion()).isEqualTo("basic-v1");
	}

	@Test
	void siElModeloAvanzadoFallaDegradaAlBasico() {
		UUID tenant = tenantWithPlan("PRO");
		when(planGate.isAvailable("PRO", Feature.SCORING_AVANZADO)).thenReturn(true);
		when(advanced.score(FEATURES)).thenThrow(new IllegalStateException("ONNX caído"));
		when(basic.score(INPUT)).thenReturn(BASIC);
		assertThat(scoring.score(tenant, INPUT, FEATURES).modelVersion()).isEqualTo("basic-v1");
	}

	private UUID tenantWithPlan(String plan) {
		UUID tenant = UUID.randomUUID();
		when(tenants.findById(TenantId.of(tenant))).thenReturn(Optional.of(
				new TenantInfo(tenant, "Demo", "1", "IMF", plan, "BO", "BOB", true)));
		return tenant;
	}

}
