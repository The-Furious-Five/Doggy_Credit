package com.doggycredit.consent.internal;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
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
        Jwt jwt = jwt();
        int days = body.getVigenciaDias() == null ? 90 : body.getVigenciaDias();
        // Conserva las validaciones previas del dominio mientras el contrato v1 no las detalla.
        if (body.getTitularDocumento() == null || body.getTitularDocumento().isBlank()
                || body.getTitularDocumento().length() > 64 || body.getFinalidad() == null
                || body.getFinalidad().isBlank() || body.getFinalidad().length() > 500
                || body.getFuentes() == null || body.getFuentes().isEmpty()
                || body.getFuentes().contains(null) || days < 1 || days > 365) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Consentimiento inválido.");
        }
        // B-07: el alcance es por fuente; las que no vienen aquí no se consultan con este consentimiento.
        String[] sources = body.getFuentes().stream().map(Enum::name).distinct().sorted().toArray(String[]::new);
        Consent consent = consents.grant(tenantId(jwt), DocumentNumber.normalize(body.getTitularDocumento()),
                sources, body.getFinalidad().strip(), days, jwt.getSubject());
        return ResponseEntity.status(HttpStatus.CREATED).header("X-Simulated", "true").body(toContract(consent));
    }

    @Override
    public ResponseEntity<List<Consentimiento>> listarConsentimientos(UUID solicitanteId) {
        return ResponseEntity.ok(consents.listForApplicant(tenantId(jwt()), solicitanteId).stream()
                .map(ConsentController::toContract).toList());
    }

    @Override
    public ResponseEntity<Consentimiento> revocarConsentimiento(UUID id) {
        Jwt jwt = jwt();
        return ResponseEntity.ok(toContract(consents.revoke(tenantId(jwt), id, jwt.getSubject())));
    }

    private static Jwt jwt() {
        return ((JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication()).getToken();
    }

    private static UUID tenantId(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("tenant_id"));
    }

    private static Consentimiento toContract(Consent consent) {
        return new Consentimiento().id(consent.getId())
                .titularDocumento(consent.getTitularDocumento())
                .fuentes(Arrays.stream(consent.getFuentes()).map(Consentimiento.FuentesEnum::fromValue).toList())
                .finalidad(consent.getFinalidad()).vigenciaDias(consent.getVigenciaDias())
                .estado(Consentimiento.EstadoEnum.fromValue(consent.estadoAt(Instant.now())))
                .creadoEn(consent.getOtorgadoEn().atOffset(ZoneOffset.UTC))
                .expiraEn(consent.getExpiraEn().atOffset(ZoneOffset.UTC))
                .revocadoEn(consent.getRevocadoEn() == null ? null : consent.getRevocadoEn().atOffset(ZoneOffset.UTC));
    }
}
