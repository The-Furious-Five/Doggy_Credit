package com.doggycredit.assessment.internal;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
import com.doggycredit.assessment.AssessmentPage;
import com.doggycredit.assessment.AssessmentResult;
import com.doggycredit.assessment.AssessmentSummary;
import com.doggycredit.shared.contract.api.EvaluacionesApi;
import com.doggycredit.shared.contract.model.Evaluacion;
import com.doggycredit.shared.contract.model.EvaluacionNueva;
import com.doggycredit.shared.contract.model.EvaluacionResumen;
import com.doggycredit.shared.contract.model.PaginaEvaluaciones;
import com.doggycredit.shared.contract.model.ElegibilidadProducto;
import com.doggycredit.shared.contract.model.CriterioElegibilidad;
import com.doggycredit.shared.contract.model.Recomendacion;

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

	/** C-09. El tenant sale del JWT; nunca de los parámetros. */
	@Override
	public ResponseEntity<PaginaEvaluaciones> listarEvaluaciones(UUID solicitanteId, OffsetDateTime desde,
			Integer pagina, Integer tamano) {
		AssessmentPage page = assessments.list(currentTenantId(), solicitanteId,
				desde == null ? null : desde.toInstant(), pagina, tamano);
		return ResponseEntity.ok(new PaginaEvaluaciones(
				page.items().stream().map(AssessmentController::toContract).toList(), page.total()));
	}

	@Override
	public ResponseEntity<Evaluacion> obtenerEvaluacion(UUID id) {
		return ResponseEntity.ok(toContract(assessments.get(currentTenantId(), id)));
	}

	private UUID currentTenantId() {
		var jwt = ((JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication()).getToken();
		return UUID.fromString(jwt.getClaimAsString("tenant_id"));
	}

	private static EvaluacionResumen toContract(AssessmentSummary summary) {
		return new EvaluacionResumen()
			.id(summary.id())
			.solicitanteId(summary.applicantId())
			// El enum de EvaluacionResumen no incluye SOLICITAR_DATOS: se publica el equivalente.
			.estado(EvaluacionResumen.EstadoEnum.fromValue(summary.status().published().name()))
			.decision(summary.status().decision()
				.map(decision -> EvaluacionResumen.DecisionEnum.fromValue(decision.name()))
				.orElse(null))
			.score(summary.score())
			.banda(EvaluacionResumen.BandaEnum.fromValue(summary.band().name()))
			.montoSolicitado(summary.requestedAmount() == null ? null : summary.requestedAmount().toPlainString())
			.plazoMeses(summary.termMonths())
			.creadoEn(summary.createdAt().atOffset(ZoneOffset.UTC));
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
			.correlationId(result.correlationId())
            .elegibilidad(result.eligibility() == null ? null : result.eligibility().stream()
                .map(p -> new ElegibilidadProducto()
                    .productoId(p.productId())
                    .tipoProducto(ElegibilidadProducto.TipoProductoEnum.fromValue(p.productType()))
                    .moneda(ElegibilidadProducto.MonedaEnum.fromValue(p.currency()))
                    .elegible(p.eligible())
                    .criterios(p.criteria().stream().map(c -> new CriterioElegibilidad()
                        .codigo(CriterioElegibilidad.CodigoEnum.fromValue(c.code().name()))
                        .cumple(c.met()).detalle(c.detail())).toList()))
                .toList())
			// C-06: ausente si no corresponden (plan sin la función o PARCIAL); [] si ninguna aplica.
			.recomendaciones(result.recommendations() == null ? null : result.recommendations().stream()
				.map(r -> new Recomendacion()
					.tipoProducto(Recomendacion.TipoProductoEnum.fromValue(r.productType()))
					.moneda(Recomendacion.MonedaEnum.fromValue(r.currency()))
					.montoSugerido(r.suggestedAmount().toPlainString())
					.razon(r.reason())
                    .plazoSugeridoMeses(r.suggestedTermMonths())
                    .razones(r.reasons()))
				.toList());
	}

}
