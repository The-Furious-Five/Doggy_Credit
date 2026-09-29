package com.doggycredit.customer;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import com.doggycredit.shared.ErrorCode;

public class ApplicantNotFoundException extends ErrorResponseException {

	public ApplicantNotFoundException() {
		super(HttpStatus.valueOf(ErrorCode.APPLICANT_NOT_FOUND.status()),
				ErrorCode.APPLICANT_NOT_FOUND.toProblemDetail(
						"Debe registrar al solicitante antes de evaluarlo."), null);
	}

}
