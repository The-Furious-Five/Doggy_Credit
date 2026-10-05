package com.doggycredit.consent;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import com.doggycredit.shared.ErrorCode;

public class ConsentNotFoundException extends ErrorResponseException {

	public ConsentNotFoundException() {
		super(HttpStatus.valueOf(ErrorCode.CONSENT_NOT_FOUND.status()),
				ErrorCode.CONSENT_NOT_FOUND.toProblemDetail("El consentimiento no existe o pertenece a otro tenant."),
				null);
	}

}
