package com.doggycredit.billing;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;
import com.doggycredit.shared.ErrorCode;

public class QuotaExceededException extends ErrorResponseException {
    public QuotaExceededException(int limit) {
        super(HttpStatus.valueOf(ErrorCode.QUOTA_EXCEEDED.status()),
                ErrorCode.QUOTA_EXCEEDED.toProblemDetail(
                        "Se alcanzó el límite de " + limit + " evaluaciones mensuales del plan."),
                null);
    }
}
