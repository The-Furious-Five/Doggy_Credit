package com.doggycredit.assessment.internal;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.RestController;

import com.doggycredit.assessment.AssessmentCommand;
import com.doggycredit.assessment.AssessmentOperations;
import com.doggycredit.assessment.AssessmentResult;
import com.doggycredit.shared.contract.api.EvaluacionesApi;
import com.doggycredit.shared.contract.model.Evaluacion;
import com.doggycredit.shared.contract.model.EvaluacionNueva;

@RestController
class AssessmentController implements EvaluacionesApi {

	private final AssessmentOperations assessments;

	AssessmentController(AssessmentOperations assessments) {
		this.assessments = assessments;
	}

	@Override
	public ResponseEntity<Evaluacion> crearEvaluacion(String idempotencyKey, EvaluacionNueva body) {
		UUID tenantId = currentTenantId();
		BigDecimal amount = body.getMontoSolicitado() == null ? null : new BigDecimal(body.getMontoSolicitado());
		int term = body.getPlazoMeses() == null ? 0 : body.getPlazoMeses();
		AssessmentResult result = assessments.create(new AssessmentCommand(tenantId, body.getTitularDocumento(),
				body.getConsentId(), body.getProposito(), amount, term, idempotencyKey,
				Objects.requireNonNullElse(MDC.get("correlationId"), "unavailable")));
		return ResponseEntity.status(HttpStatus.CREATED).header("X-Simulated", "true").body(toContract(result));
	}

	@Override
	public ResponseEntity<Evaluacion> obtenerEvaluacion(UUID id) {
		return ResponseEntity.ok(toContract(assessments.get(currentTenantId(), id)));
	}

	private UUID currentTenantId() {
		var jwt = ((JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication()).getToken();
		return UUID.fromString(jwt.getClaimAsString("tenant_id"));
	}

	private Evaluacion toContract(AssessmentResult result) {
		return new Evaluacion()
			.id(result.id())
			.estado(Evaluacion.EstadoEnum.fromValue(result.status().published().name()))
			.decision(result.status().decision()
				.map(decision -> Evaluacion.DecisionEnum.fromValue(decision.name()))
				.orElse(null))
			.score(result.score())
			.banda(Evaluacion.BandaEnum.fromValue(result.band().name()))
			.razones(result.reasons())
			.modelVersion(result.modelVersion())
			.simulado(result.simulated())
			.correlationId(result.correlationId());
	}

}
