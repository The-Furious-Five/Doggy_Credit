package com.doggycredit.consent;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * CONSENT_REQUIRED de contracts/errors/catalog.yaml: no hay consentimiento
 * vigente para la fuente. Se renderiza como RFC 9457 con el soporte nativo
 * de Spring; el manejador único y el correlationId llegan con RF-API-02.1.
 */
public class ConsentRequiredException extends ErrorResponseException {

	public ConsentRequiredException(Source source) {
		super(HttpStatus.UNPROCESSABLE_CONTENT, problem(source), null);
	}

	private static ProblemDetail problem(Source source) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT,
				"No hay consentimiento vigente para la fuente " + source + ".");
		problem.setType(URI.create("https://docs.doggycredit.example/errors/consent-required"));
		problem.setTitle("Consentimiento requerido");
		problem.setProperty("code", "CONSENT_REQUIRED");
		return problem;
	}

}
