package com.doggycredit.customer;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import com.doggycredit.shared.ErrorCode;

public class ApplicantAlreadyExistsException extends ErrorResponseException {

	public ApplicantAlreadyExistsException() {
		super(HttpStatus.valueOf(ErrorCode.APPLICANT_ALREADY_EXISTS.status()),
				ErrorCode.APPLICANT_ALREADY_EXISTS.toProblemDetail(
						"El documento ya corresponde a un solicitante del tenant."), null);
	}

}
