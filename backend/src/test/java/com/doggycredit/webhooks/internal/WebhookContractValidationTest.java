package com.doggycredit.webhooks.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.doggycredit.shared.contract.model.EventoWebhook;
import com.doggycredit.shared.contract.model.WebhookCambio;

import jakarta.validation.Validation;

/** Ejecuta Bean Validation real sobre los DTO generados, sin necesitar Docker. */
class WebhookContractValidationTest {
    @Test
    void validarUnaUrlUriNoAplicaRestriccionesDeStringIncompatibles() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var change = new WebhookCambio(List.of(EventoWebhook.CREDIT_ASSESSMENT_COMPLETED_V1))
                    .url(URI.create("https://receiver.example/events")).secreto("x".repeat(32));
            assertThat(factory.getValidator().validate(change)).isEmpty();
            assertThat(factory.getValidator().validate(new WebhookCambio(List.of()).url(null))).isEmpty();
        }
    }

    @Test
    void elContratoGeneradoValidaLongitudDeSecretoYCantidadDeEventos() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var change = new WebhookCambio(List.of(EventoWebhook.CONSENT_GRANTED_V1,
                    EventoWebhook.CONSENT_REVOKED_V1, EventoWebhook.CREDIT_ASSESSMENT_COMPLETED_V1,
                    EventoWebhook.CONSENT_GRANTED_V1)).secreto("short");
            assertThat(factory.getValidator().validate(change)).extracting(v -> v.getPropertyPath().toString())
                    .containsExactlyInAnyOrder("eventos", "secreto");
        }
    }

    @Test
    void elServidorMantieneElLimiteDeLongitudDeUrl() {
        assertThatThrownBy(() -> new WebhookTarget(true).validate(URI.create("https://receiver.example/" + "x".repeat(2048))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
