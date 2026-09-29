package com.doggycredit.assessment;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import com.doggycredit.shared.ErrorCode;

public class AssessmentNotFoundException extends ErrorResponseException {

	public AssessmentNotFoundException() {
		super(HttpStatus.valueOf(ErrorCode.ASSESSMENT_NOT_FOUND.status()),
				ErrorCode.ASSESSMENT_NOT_FOUND.toProblemDetail(
						"La evaluación no existe o pertenece a otro tenant."), null);
	}

}
