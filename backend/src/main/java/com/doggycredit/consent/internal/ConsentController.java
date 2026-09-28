package com.doggycredit.consent.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.doggycredit.consent.Source;
import com.doggycredit.shared.DocumentNumber;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * POST /consentimientos (RF-CON-01.1). Escrito a mano porque SPIKE-5
 * (openapi-generator) aún no existe: se reemplaza por la interfaz generada
 * desde contracts/openapi/doggycredit-api.v1.yaml en cuanto SPIKE-5 cierre.
 * Mapeado sin el prefijo /api/v1 del servidor del contrato (no hay
 * context-path configurado).
 */
@RestController
class ConsentController {

	private static final int DEFAULT_VIGENCIA_DIAS = 90;

	private final ConsentService consents;

	ConsentController(ConsentService consents) {
		this.consents = consents;
	}

	record ConsentimientoNuevo(@NotBlank @Size(max = 64) String titularDocumento,
			@NotEmpty List<@NotNull Source> fuentes, @NotBlank @Size(max = 500) String finalidad,
			@Min(1) @Max(365) Integer vigenciaDias) {
	}

	record Consentimiento(UUID id, String titularDocumento, List<String> fuentes, String finalidad,
			int vigenciaDias, String estado, Instant creadoEn) {
	}

	@PostMapping("/consentimientos")
	@ResponseStatus(HttpStatus.CREATED)
	Consentimiento create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ConsentimientoNuevo body) {
		UUID tenantId = tenantId(jwt);
		String documento = DocumentNumber.normalize(body.titularDocumento());
		String[] fuentes = body.fuentes().stream().map(Source::name).distinct().sorted().toArray(String[]::new);
		String finalidad = body.finalidad().strip();
		int dias = body.vigenciaDias() == null ? DEFAULT_VIGENCIA_DIAS : body.vigenciaDias();

		Consent consent = consents.grant(tenantId, documento, fuentes, finalidad, dias, jwt.getSubject());

		return new Consentimiento(consent.getId(), consent.getTitularDocumento(), List.of(consent.getFuentes()),
				consent.getFinalidad(), consent.getVigenciaDias(), consent.estadoAt(Instant.now()),
				consent.getOtorgadoEn());
	}

	// El tenant sale solo del claim tenant_id del JWT. Sin él (o inválido) se
	// rechaza: nunca hay un tenant por defecto. SPIKE-2 puede mover esto a tenancy.
	private static UUID tenantId(Jwt jwt) {
		String claim = jwt.getClaimAsString("tenant_id");
		if (claim == null || jwt.getSubject() == null) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El token no trae tenant_id o sub.");
		}
		try {
			return UUID.fromString(claim);
		}
		catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El claim tenant_id no es un UUID.");
		}
	}


}
