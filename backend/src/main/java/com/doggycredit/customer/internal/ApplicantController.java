package com.doggycredit.customer.internal;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.RestController;

import com.doggycredit.customer.ApplicantProfile;
import com.doggycredit.customer.ApplicantRegistration;
import com.doggycredit.customer.ApplicantRegistry;
import com.doggycredit.shared.contract.api.SolicitantesApi;
import com.doggycredit.shared.contract.model.Solicitante;
import com.doggycredit.shared.contract.model.SolicitanteNuevo;

@RestController
class ApplicantController implements SolicitantesApi {

	private final ApplicantRegistry applicants;

	ApplicantController(ApplicantRegistry applicants) {
		this.applicants = applicants;
	}

	@Override
	public ResponseEntity<Solicitante> crearSolicitante(SolicitanteNuevo body) {
		var jwt = ((JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication()).getToken();
		UUID tenantId = UUID.fromString(jwt.getClaimAsString("tenant_id"));
		ApplicantProfile created = applicants.register(tenantId,
				new ApplicantRegistration(body.getDocumento(), body.getActividad(),
						body.getAntiguedadActividadMeses(), new BigDecimal(body.getIngresosMensuales()),
						Currency.getInstance(body.getMoneda().getValue()), body.getDestinoCredito()));
		Solicitante response = new Solicitante()
			.id(created.id())
			.documento(created.document())
			.actividad(created.activity())
			.antiguedadActividadMeses(created.activitySeniorityMonths())
			.ingresosMensuales(created.monthlyIncome().amount().toPlainString())
			.moneda(Solicitante.MonedaEnum.fromValue(created.monthlyIncome().currency().getCurrencyCode()))
			.destinoCredito(created.creditDestination())
			.creadoEn(created.createdAt().atOffset(ZoneOffset.UTC));
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

}
