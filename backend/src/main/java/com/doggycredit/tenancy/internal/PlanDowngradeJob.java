package com.doggycredit.tenancy.internal;

import java.time.Instant;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * A-12: aplica las bajadas de plan programadas cuando cierra su período. Revisa
 * cada minuto, así que la bajada se nota a lo sumo un minuto después del cierre.
 */
@Component
class PlanDowngradeJob {

    private final PlanChangeService planChanges;

    PlanDowngradeJob(PlanChangeService planChanges) {
        this.planChanges = planChanges;
    }

    @Scheduled(fixedDelayString = "${doggycredit.plans.downgrade-poll-delay:60000}", initialDelayString = "60000")
    void applyDue() {
        planChanges.applyDue(Instant.now());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    static class Scheduling {
    }

}
