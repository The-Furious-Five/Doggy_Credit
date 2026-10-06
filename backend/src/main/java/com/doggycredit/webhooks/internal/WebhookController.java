package com.doggycredit.webhooks.internal;

import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.RestController;

import com.doggycredit.shared.RoleGuard;
import com.doggycredit.shared.contract.api.WebhooksApi;
import com.doggycredit.shared.contract.model.EventoWebhook;
import com.doggycredit.shared.contract.model.WebhookCambio;
import com.doggycredit.shared.contract.model.WebhookConfiguracion;

@RestController
class WebhookController implements WebhooksApi {
    private final WebhookService webhooks;

    WebhookController(WebhookService webhooks) { this.webhooks = webhooks; }

    @Override
    public ResponseEntity<WebhookConfiguracion> obtenerWebhooks() {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(dto(webhooks.get(tenant())));
    }

    @Override
    public ResponseEntity<WebhookConfiguracion> actualizarWebhooks(WebhookCambio body) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(dto(webhooks.put(tenant(), body.getUrl(),
                body.getEventos().stream().map(EventoWebhook::getValue).toList(), body.getSecreto())));
    }

    private static UUID tenant() {
        RoleGuard.require("TENANT_ADMIN");
        var jwt = (JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        return UUID.fromString(jwt.getToken().getClaimAsString("tenant_id"));
    }

    private static WebhookConfiguracion dto(WebhookService.Configuration config) {
        return new WebhookConfiguracion().url(config.url() == null ? null : java.net.URI.create(config.url()))
                .eventos(config.events().stream().map(EventoWebhook::fromValue).toList())
                .secretoConfigurado(config.secretConfigured())
                .actualizadaEn(config.updatedAt() == null ? null : config.updatedAt().atOffset(ZoneOffset.UTC));
    }
}
