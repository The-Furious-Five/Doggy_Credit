package com.doggycredit.webhooks.internal;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.modulith.events.core.TargetEventPublication;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.doggycredit.tenancy.TenantRegistry;

@Component
@ConditionalOnProperty(name = "doggycredit.webhooks.worker-enabled", matchIfMissing = true)
class WebhookWorker {
    private static final Logger log = LoggerFactory.getLogger(WebhookWorker.class);
    private final TenantRegistry tenants;
    private final WebhookService webhooks;
    private final IncompleteEventPublications publications;

    WebhookWorker(TenantRegistry tenants, WebhookService webhooks, IncompleteEventPublications publications) {
        this.tenants = tenants;
        this.webhooks = webhooks;
        this.publications = publications;
    }

    @Scheduled(fixedDelayString = "${doggycredit.webhooks.poll-delay:5000}", initialDelayString = "5000")
    void dispatch() {
        for (var tenant : tenants.findAllActive()) {
            try {
                for (int i = 0; i < 20 && !Thread.currentThread().isInterrupted()
                        && webhooks.deliverNext(tenant.id(), Instant.now()); i++) { }
            } catch (RuntimeException e) {
                log.warn("No se pudo procesar la cola Webhooks del tenant {}", tenant.id());
            }
        }
    }

    @Scheduled(fixedDelayString = "60000", initialDelayString = "60000")
    void recoverOutbox() {
        publications.resubmitIncompletePublications(ResubmissionOptions.defaults().withMinAge(Duration.ofMinutes(1))
                .withBatchSize(20).withMaxInFlight(20).withFilter(publication -> {
                    return publication instanceof TargetEventPublication target
                            && target.getTargetIdentifier().getValue().startsWith("webhooks.");
                }));
    }
}
