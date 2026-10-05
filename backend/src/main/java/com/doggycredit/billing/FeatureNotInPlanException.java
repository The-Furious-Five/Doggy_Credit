package com.doggycredit.billing;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;
import com.doggycredit.shared.ErrorCode;

public class FeatureNotInPlanException extends ErrorResponseException {
    public FeatureNotInPlanException(String feature, String plan) {
        super(HttpStatus.valueOf(ErrorCode.FEATURE_NOT_IN_PLAN.status()),
                ErrorCode.FEATURE_NOT_IN_PLAN.toProblemDetail(
                        "La función '" + feature + "' no está disponible en el plan " + plan + "."),
                null);
    }
}
