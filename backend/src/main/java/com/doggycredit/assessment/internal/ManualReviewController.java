package com.doggycredit.assessment.internal;

import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.RestController;

import com.doggycredit.assessment.AssessmentStatus;
import com.doggycredit.assessment.internal.ManualReviewResolution.Decision;
import com.doggycredit.shared.RoleGuard;
import com.doggycredit.shared.contract.api.RevisionManualApi;
import com.doggycredit.shared.contract.model.CasoRevision;
import com.doggycredit.shared.contract.model.PaginaCasosRevision;
import com.doggycredit.shared.contract.model.Resolucion;
import com.doggycredit.shared.contract.model.ResolucionNueva;

/**
 * C-10 (LOS-111): GET /revision-manual y POST /revision-manual/{evaluacionId}/resolucion,
 * para ANALYST y TENANT_ADMIN. Ruta y DTOs vienen del contrato; el tenant sale del JWT.
 */
@RestController
class ManualReviewController implements RevisionManualApi {

	static final String[] ALLOWED_ROLES = { "ANALYST", "TENANT_ADMIN" };

	private final ManualReviewService reviews;

	ManualReviewController(ManualReviewService reviews) {
		this.reviews = reviews;
	}

	@Override
	public ResponseEntity<PaginaCasosRevision> listarCasosRevision(Integer pagina, Integer tamano) {
		var jwt = RoleGuard.requireAnyJwt(ALLOWED_ROLES);
		ManualReviewService.Inbox inbox = reviews.inbox(tenantId(jwt), pagina, tamano);
		return ResponseEntity.ok(new PaginaCasosRevision()
			.items(inbox.items().stream().map(ManualReviewController::toContract).toList())
			.total(inbox.total())
			.productos(inbox.products()));
	}

	@Override
	public ResponseEntity<Resolucion> resolverCasoRevision(UUID evaluacionId, ResolucionNueva body) {
		var jwt = RoleGuard.requireAnyJwt(ALLOWED_ROLES);
		ManualReviewResolution resolution = reviews.resolve(tenantId(jwt), evaluacionId,
				Decision.valueOf(body.getDecision().getValue()), body.getProductoId(), body.getJustificacion(),
				resolvedBy(jwt));
		return ResponseEntity.ok(new Resolucion()
			.evaluacionId(resolution.assessmentId())
			.decision(Resolucion.DecisionEnum.fromValue(resolution.decision().name()))
			.productoId(resolution.productId())
			.justificacion(resolution.justification())
			.resueltoPor(resolution.resolvedBy())
			.resueltoEn(resolution.resolvedAt().atOffset(ZoneOffset.UTC)));
	}

	private static CasoRevision toContract(ManualReviewService.Item item) {
		var reviewCase = item.reviewCase();
		// El enum v1 del contrato no lleva PARCIAL: un PARCIAL queda en revisión manual (RN-15).
		var motivo = reviewCase.status() == AssessmentStatus.SOLICITAR_DATOS ? CasoRevision.MotivoEnum.SOLICITAR_DATOS
				: CasoRevision.MotivoEnum.REVISION_MANUAL;
		return new CasoRevision()
			.evaluacionId(reviewCase.id())
			.solicitanteId(reviewCase.applicantId())
			.documento(item.document())
			.creadoEn(reviewCase.createdAt().atOffset(ZoneOffset.UTC))
			.score(reviewCase.score())
			.banda(CasoRevision.BandaEnum.fromValue(reviewCase.band().name()))
			.motivo(motivo)
			.resultadoParcial(reviewCase.status() == AssessmentStatus.PARCIAL)
			.razones(reviewCase.reasons())
			.montoSolicitado(reviewCase.requestedAmount() == null ? null : reviewCase.requestedAmount().toPlainString())
			.plazoMeses(reviewCase.termMonths())
			.proposito(reviewCase.purpose());
	}

	private static UUID tenantId(JwtAuthenticationToken jwt) {
		return UUID.fromString(jwt.getToken().getClaimAsString("tenant_id"));
	}

	/** El contrato pide el claim preferred_username; si el token no lo trae, el sub. */
	private static String resolvedBy(JwtAuthenticationToken jwt) {
		String username = jwt.getToken().getClaimAsString("preferred_username");
		return username == null || username.isBlank() ? jwt.getName() : username;
	}

}
