package com.doggycredit.consent;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import com.doggycredit.shared.ErrorCode;

/**
 * CONSENT_REQUIRED de contracts/errors/catalog.yaml: no hay consentimiento
 * vigente para la fuente. Se renderiza como RFC 9457 con el soporte nativo
 * de Spring; el correlationId lo inyecta ProblemDetailAdvice (RF-API-02.1).
 */
public class ConsentRequiredException extends ErrorResponseException {

    public ConsentRequiredException(Source source) {
        super(HttpStatus.valueOf(ErrorCode.CONSENT_REQUIRED.status()),
                ErrorCode.CONSENT_REQUIRED.toProblemDetail(
                        "No hay consentimiento vigente para la fuente " + source + "."),
                null);
    }
}
