package com.doggycredit.webhooks.internal;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

@Component
class WebhookHttp {
    private final WebhookTarget targets;
    private final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();

    WebhookHttp(WebhookTarget targets) {
        this.targets = targets;
    }

    int send(String url, String eventType, UUID eventId, String secret, String payload, Instant now) {
        URI destination = URI.create(url);
        targets.validate(destination);
        String timestamp = Long.toString(now.getEpochSecond());
        HttpRequest request = HttpRequest.newBuilder(destination).timeout(Duration.ofSeconds(3))
                .header("Content-Type", "application/json")
                .header("X-DoggyCredit-Event", eventType)
                .header("X-DoggyCredit-Event-Id", eventId.toString())
                .header("X-DoggyCredit-Timestamp", timestamp)
                .header("X-DoggyCredit-Signature", signature(secret, timestamp, payload))
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8)).build();
        try {
            return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Entrega interrumpida", e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Receptor no disponible", e);
        }
    }

    static String signature(String secret, String timestamp, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal(
                    (timestamp + "." + payload).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("HMAC no disponible", e);
        }
    }
}
