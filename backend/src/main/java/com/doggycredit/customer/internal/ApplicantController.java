package com.doggycredit.customer.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.RestController;

import com.doggycredit.audit.AccessSubject;
import com.doggycredit.audit.AuditTrail;
import com.doggycredit.customer.ApplicantPage;
import com.doggycredit.customer.ApplicantProfile;
import com.doggycredit.customer.ApplicantRegistration;
import com.doggycredit.customer.ApplicantRegistry;
import com.doggycredit.customer.ApplicantSegment;
import com.doggycredit.customer.FinancialProfile360;
import com.doggycredit.customer.FinancialProfiles;
import com.doggycredit.shared.Money;
import com.doggycredit.shared.contract.api.SolicitantesApi;
import com.doggycredit.shared.contract.model.Dinero;
import com.doggycredit.shared.contract.model.PaginaSolicitantes;
import com.doggycredit.shared.contract.model.PerfilFinanciero360;
import com.doggycredit.shared.contract.model.PerfilFinanciero360FuentesInner;
import com.doggycredit.shared.contract.model.Segmento;
import com.doggycredit.shared.contract.model.Solicitante;
import com.doggycredit.shared.contract.model.SolicitanteDetalle;
import com.doggycredit.shared.contract.model.SolicitanteNuevo;

@RestController
class ApplicantController implements SolicitantesApi {

	private final ApplicantRegistry applicants;
	private final FinancialProfiles profiles;
	private final AuditTrail audit;

	ApplicantController(ApplicantRegistry applicants, FinancialProfiles profiles, AuditTrail audit) {
		this.applicants = applicants;
		this.profiles = profiles;
		this.audit = audit;
	}

	@Override
	public ResponseEntity<Solicitante> crearSolicitante(SolicitanteNuevo body) {
		ApplicantProfile created = applicants.register(currentTenantId(),
				new ApplicantRegistration(body.getDocumento(), body.getActividad(),
						body.getAntiguedadActividadMeses(), new BigDecimal(body.getIngresosMensuales()),
						Currency.getInstance(body.getMoneda().getValue()), body.getDestinoCredito(),
						body.getSegmento() == null ? null : ApplicantSegment.valueOf(body.getSegmento().getValue())));
		return ResponseEntity.status(HttpStatus.CREATED).body(toContract(created));
	}

	/** B-08. El documento de búsqueda llega por cabecera y solo se usa para calcular su HMAC. */
	@Override
	public ResponseEntity<PaginaSolicitantes> listarSolicitantes(Integer pagina, Integer tamano, Segmento segmento,
			String xDocumento) {
		ApplicantPage page = applicants.list(currentTenantId(),
				segmento == null ? null : ApplicantSegment.valueOf(segmento.getValue()), xDocumento, pagina, tamano);
		return ResponseEntity.ok(new PaginaSolicitantes(
				page.items().stream().map(ApplicantController::toContract).toList(), page.total()));
	}

	@Override
	public ResponseEntity<SolicitanteDetalle> obtenerSolicitante(UUID id) {
		UUID tenantId = currentTenantId();
		ApplicantProfile a = applicants.findById(tenantId, id);
		SolicitanteDetalle detail = new SolicitanteDetalle()
			.id(a.id())
			.documento(a.document())
			.actividad(a.activity())
			.antiguedadActividadMeses(a.activitySeniorityMonths())
			.ingresosMensuales(a.monthlyIncome().amount().toPlainString())
			.moneda(SolicitanteDetalle.MonedaEnum.fromValue(a.monthlyIncome().currency().getCurrencyCode()))
			.destinoCredito(a.creditDestination())
			.segmento(Segmento.fromValue(a.segment().name()))
			.creadoEn(a.createdAt().atOffset(ZoneOffset.UTC))
			.perfil(profiles.latest(tenantId, id).map(ApplicantController::toContract).orElse(null));
		// C-11 (RF-CON-03): leer el detalle y el Perfil 360 de un titular también es un acceso.
		audit.record(tenantId, "APPLICANT_READ", "APPLICANT", id, AccessSubject.applicant(id));
		return ResponseEntity.ok(detail);
	}

	private static UUID currentTenantId() {
		var jwt = ((JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication()).getToken();
		return UUID.fromString(jwt.getClaimAsString("tenant_id"));
	}

	private static Solicitante toContract(ApplicantProfile a) {
		return new Solicitante()
			.id(a.id())
			.documento(a.document())
			.actividad(a.activity())
			.antiguedadActividadMeses(a.activitySeniorityMonths())
			.ingresosMensuales(a.monthlyIncome().amount().toPlainString())
			.moneda(Solicitante.MonedaEnum.fromValue(a.monthlyIncome().currency().getCurrencyCode()))
			.destinoCredito(a.creditDestination())
			.segmento(Segmento.fromValue(a.segment().name()))
			.creadoEn(a.createdAt().atOffset(ZoneOffset.UTC));
	}

	private static PerfilFinanciero360 toContract(FinancialProfile360 p) {
		return new PerfilFinanciero360()
			.id(p.id())
			.hash(p.hash())
			.creadoEn(p.createdAt().atOffset(ZoneOffset.UTC))
			.ingresoMensual(money(p.monthlyIncome()))
			.origenIngreso(PerfilFinanciero360.OrigenIngresoEnum.fromValue(p.incomeOrigin().name()))
			.deudaVigente(money(p.currentDebt()))
			.obligaciones(p.obligations())
			.cuotaMensual(money(p.monthlyInstallment()))
			.endeudamientoPorcentaje(p.debtToIncomePercent() == null ? null : p.debtToIncomePercent().toPlainString())
			.capacidadPago(money(p.paymentCapacity()))
			.antiguedadSistemaFinancieroMeses(p.financialSeniorityMonths())
			.fuentes(p.sources().stream().map(s -> new PerfilFinanciero360FuentesInner()
				.fuente(PerfilFinanciero360FuentesInner.FuenteEnum.fromValue(s.source().name()))
				.estado(PerfilFinanciero360FuentesInner.EstadoEnum.fromValue(s.state().name()))
				.consultadaEn(offset(s.obtainedAt()))
				.sha256(s.sha256())).toList());
	}

	private static Dinero money(Money m) {
		return m == null ? null
				: new Dinero(m.amount().toPlainString(), Dinero.CurrencyEnum.fromValue(m.currency().getCurrencyCode()));
	}

	private static java.time.OffsetDateTime offset(Instant instant) {
		return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
	}

}
