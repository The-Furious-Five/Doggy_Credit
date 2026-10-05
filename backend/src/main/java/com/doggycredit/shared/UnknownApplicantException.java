package com.doggycredit.shared;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/**
 * UNKNOWN_APPLICANT: lectura de un solicitante por id que no existe o es de otro
 * tenant (404, nunca se distingue cuál). En shared porque la lanzan customer y
 * consent; APPLICANT_NOT_FOUND (422) es otra cosa: evaluar sin haber registrado.
 */
public class UnknownApplicantException extends ErrorResponseException {

	public UnknownApplicantException() {
		super(HttpStatus.valueOf(ErrorCode.UNKNOWN_APPLICANT.status()),
				ErrorCode.UNKNOWN_APPLICANT.toProblemDetail("El solicitante no existe o pertenece a otro tenant."),
				null);
	}

}
