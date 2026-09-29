package com.doggycredit.assessment;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import com.doggycredit.shared.ErrorCode;

public class IdempotencyKeyReusedException extends ErrorResponseException {

	public IdempotencyKeyReusedException() {
		super(HttpStatus.valueOf(ErrorCode.IDEMPOTENCY_KEY_REUSED.status()),
				ErrorCode.IDEMPOTENCY_KEY_REUSED.toProblemDetail(
						"La Idempotency-Key ya se usó con una solicitud diferente."), null);
	}

}
