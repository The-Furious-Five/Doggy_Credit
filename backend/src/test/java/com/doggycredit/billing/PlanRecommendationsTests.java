package com.doggycredit.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.doggycredit.TestcontainersConfiguration;

/** A-06 / C-06: las recomendaciones son solo de PRO y ENTERPRISE (migración real en PostgreSQL). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PlanRecommendationsTests {

    @Autowired
    PlanCatalog plans;

    @Autowired
    PlanGate gate;

    @Test
    void soloProYEnterpriseIncluyenRecomendaciones() {
        assertThat(plans.requireById("FREE").recomendaciones()).isFalse();
        assertThat(plans.requireById("PRO").recomendaciones()).isTrue();
        assertThat(plans.requireById("ENTERPRISE").recomendaciones()).isTrue();
    }

    @Test
    void unTenantFreeRecibeFeatureNotInPlan() {
        assertThatThrownBy(() -> gate.requireFeature("FREE", Feature.RECOMENDACIONES))
            .isInstanceOf(FeatureNotInPlanException.class);
        gate.requireFeature("PRO", Feature.RECOMENDACIONES);
    }
}
