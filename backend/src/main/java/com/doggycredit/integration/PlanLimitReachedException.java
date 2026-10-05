package com.doggycredit.integration;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import com.doggycredit.shared.ErrorCode;

public class PlanLimitReachedException extends ErrorResponseException {

	public PlanLimitReachedException(String plan, int limit) {
		super(HttpStatus.valueOf(ErrorCode.PLAN_LIMIT_REACHED.status()),
				ErrorCode.PLAN_LIMIT_REACHED.toProblemDetail(
						"El plan " + plan + " permite un máximo de " + limit + " integraciones activas."),
				null);
	}

}
