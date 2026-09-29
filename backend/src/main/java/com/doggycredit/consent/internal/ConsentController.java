package com.doggycredit.consent.internal;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import com.doggycredit.shared.DocumentNumber;
import com.doggycredit.shared.contract.api.ConsentimientosApi;
import com.doggycredit.shared.contract.model.Consentimiento;
import com.doggycredit.shared.contract.model.ConsentimientoNuevo;

/** SPIKE-5: ruta y DTOs provienen exclusivamente del contrato OpenAPI. */
@RestController
class ConsentController implements ConsentimientosApi {
    private final ConsentService consents;

    ConsentController(ConsentService consents) { this.consents = consents; }

    @Override
    public ResponseEntity<Consentimiento> crearConsentimiento(ConsentimientoNuevo body) {
        var jwt = ((JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication()).getToken();
        UUID tenantId = UUID.fromString(jwt.getClaimAsString("tenant_id"));
        int days = body.getVigenciaDias() == null ? 90 : body.getVigenciaDias();
        // Conserva las validaciones previas del dominio mientras el contrato v1 no las detalla.
        if (body.getTitularDocumento() == null || body.getTitularDocumento().isBlank()
                || body.getTitularDocumento().length() > 64 || body.getFinalidad() == null
                || body.getFinalidad().isBlank() || body.getFinalidad().length() > 500
                || body.getFuentes() == null || body.getFuentes().isEmpty()
                || body.getFuentes().contains(null) || days < 1 || days > 365) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Consentimiento inválido.");
        }
        String[] sources = body.getFuentes().stream().map(Enum::name).distinct().sorted().toArray(String[]::new);
        Consent consent = consents.grant(tenantId, DocumentNumber.normalize(body.getTitularDocumento()),
                sources, body.getFinalidad().strip(), days, jwt.getSubject());
        var result = new Consentimiento().id(consent.getId())
                .titularDocumento(consent.getTitularDocumento())
                .fuentes(Arrays.stream(consent.getFuentes()).map(Consentimiento.FuentesEnum::fromValue).toList())
                .finalidad(consent.getFinalidad()).vigenciaDias(consent.getVigenciaDias())
                .estado(Consentimiento.EstadoEnum.fromValue(consent.estadoAt(Instant.now())))
                .creadoEn(consent.getOtorgadoEn().atOffset(ZoneOffset.UTC));
        return ResponseEntity.status(HttpStatus.CREATED).header("X-Simulated", "true").body(result);
    }
}
