package com.doggycredit.webhooks.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class WebhookHttpTest {
    @Test
    void firmaLosBytesDelTimestampYPayloadConHmacSha256() {
        // Resultado calculado independientemente con Python hmac/hashlib.
        String signature = WebhookHttp.signature("receiver-test-secret-with-32-characters", "1791312000",
                "{\"eventId\":\"test\",\"score\":700}");
        assertThat(signature).isEqualTo("sha256=3c896c1eab23b30d495d95a25b517c6bae0412d61429253a8447d4feaac56d73");
        assertThat(WebhookHttp.signature("receiver-test-secret-with-32-characters", "1791312001",
                "{\"eventId\":\"test\",\"score\":700}")).isNotEqualTo(signature);
        assertThat(WebhookHttp.signature("receiver-test-secret-with-32-characters", "1791312000",
                "{\"eventId\":\"test\",\"score\":701}")).isNotEqualTo(signature);
    }

    @Test
    void backoffCreceSinDesbordarYElNumeroDeIntentosEsFinito() {
        assertThat(WebhookService.MAX_ATTEMPTS).isEqualTo(5);
        assertThat(WebhookService.backoff(1)).isEqualTo(Duration.ofSeconds(30));
        assertThat(WebhookService.backoff(2)).isEqualTo(Duration.ofSeconds(60));
        assertThat(WebhookService.backoff(3)).isEqualTo(Duration.ofSeconds(120));
        assertThat(WebhookService.backoff(4)).isEqualTo(Duration.ofSeconds(240));
        assertThat(WebhookService.backoff(100)).isEqualTo(Duration.ofSeconds(480));
    }
}
