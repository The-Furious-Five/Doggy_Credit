package com.doggycredit.billing.internal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.doggycredit.billing.Feature;
import com.doggycredit.billing.FeatureNotInPlanException;
import com.doggycredit.billing.PlanCatalog;
import com.doggycredit.billing.PlanInfo;
import com.doggycredit.shared.ErrorCode;

class PlanGateImplTest {

    private PlanGateImpl planGate;

    @BeforeEach
    void setUp() {
        PlanCatalog stubCatalog = new PlanCatalog() {
            @Override
            public Optional<PlanInfo> findById(String planId) {
                return Optional.ofNullable(requireById(planId));
            }

            @Override
            public PlanInfo requireById(String planId) {
                if ("FREE".equals(planId)) {
                    return new PlanInfo("FREE", "Free", 100, 1, 0, false, false, false);
                } else if ("PRO".equals(planId)) {
                    return new PlanInfo("PRO", "Pro", 1000, 10, 5, true, true, true);
                }
                throw new IllegalArgumentException("Unknown plan: " + planId);
            }
        };
        planGate = new PlanGateImpl(stubCatalog);
    }

    @Test
    void testFreePlanBlocksFeatures() {
        org.junit.jupiter.api.Assertions.assertFalse(planGate.isAvailable("FREE", Feature.SCORING_AVANZADO));
        org.junit.jupiter.api.Assertions.assertFalse(planGate.isAvailable("FREE", Feature.WEBHOOKS));
        org.junit.jupiter.api.Assertions.assertFalse(planGate.isAvailable("FREE", Feature.ANALYTICS));
        assertThrowsFeatureNotInPlan("FREE", Feature.SCORING_AVANZADO);
        assertThrowsFeatureNotInPlan("FREE", Feature.WEBHOOKS);
        assertThrowsFeatureNotInPlan("FREE", Feature.ANALYTICS);
    }

    @Test
    void testProPlanAllowsFeatures() {
        org.junit.jupiter.api.Assertions.assertTrue(planGate.isAvailable("PRO", Feature.SCORING_AVANZADO));
        org.junit.jupiter.api.Assertions.assertTrue(planGate.isAvailable("PRO", Feature.WEBHOOKS));
        org.junit.jupiter.api.Assertions.assertTrue(planGate.isAvailable("PRO", Feature.ANALYTICS));
        assertDoesNotThrow(() -> planGate.requireFeature("PRO", Feature.SCORING_AVANZADO));
        assertDoesNotThrow(() -> planGate.requireFeature("PRO", Feature.WEBHOOKS));
        assertDoesNotThrow(() -> planGate.requireFeature("PRO", Feature.ANALYTICS));
    }

    private void assertThrowsFeatureNotInPlan(String planId, Feature feature) {
        FeatureNotInPlanException exception = assertThrows(
                FeatureNotInPlanException.class,
                () -> planGate.requireFeature(planId, feature)
        );
        assertEquals(ErrorCode.FEATURE_NOT_IN_PLAN.status(), exception.getStatusCode().value());
    }
}
