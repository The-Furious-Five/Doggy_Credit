package com.doggycredit.webhooks.internal;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;

import com.doggycredit.assessment.CreditAssessmentCompleted;
import com.doggycredit.consent.ConsentGranted;
import com.doggycredit.consent.ConsentRevoked;

@Component
class WebhookEvents {
    private final WebhookService webhooks;

    WebhookEvents(WebhookService webhooks) { this.webhooks = webhooks; }

    @ApplicationModuleListener(id = "webhooks.assessment", propagation = Propagation.NOT_SUPPORTED)
    void on(CreditAssessmentCompleted event) {
        webhooks.enqueue(event.tenantId(), event.eventId(), CreditAssessmentCompleted.SCHEMA, event);
    }

    @ApplicationModuleListener(id = "webhooks.granted", propagation = Propagation.NOT_SUPPORTED)
    void on(ConsentGranted event) {
        webhooks.enqueue(event.tenantId(), event.eventId(), ConsentGranted.SCHEMA, event);
    }

    @ApplicationModuleListener(id = "webhooks.revoked", propagation = Propagation.NOT_SUPPORTED)
    void on(ConsentRevoked event) {
        webhooks.enqueue(event.tenantId(), event.eventId(), ConsentRevoked.SCHEMA, event);
    }
}
