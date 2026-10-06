package com.doggycredit.webhooks.internal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WebhookTargetTest {
    @ParameterizedTest
    @ValueSource(strings = { "http://8.8.8.8/events", "https://user:password@8.8.8.8/events", "/relative",
        "file:///etc/passwd", "https://8.8.8.8/events#fragment", "https://127.0.0.1/events",
        "https://10.0.0.1/events", "https://172.16.0.1/events", "https://192.168.1.1/events",
        "https://169.254.169.254/events", "https://[::1]/events", "https://[fc00::1]/events",
        "https://8.8.8.8:0/events", "https://8.8.8.8:65536/events" })
    void rechazaDestinosNoPublicosOCredenciales(String target) {
        assertThatThrownBy(() -> new WebhookTarget(false).validate(URI.create(target)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void httpsPublicoEsValidoYHttpPrivadoRequiereConfiguracionExplicitaDePruebas() {
        assertThatCode(() -> new WebhookTarget(false).validate(URI.create("https://8.8.8.8/events"))).doesNotThrowAnyException();
        assertThatCode(() -> new WebhookTarget(true).validate(URI.create("http://127.0.0.1:8080/events"))).doesNotThrowAnyException();
        assertThatThrownBy(() -> new WebhookTarget(true).validate(URI.create("http://user:password@localhost/events")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
