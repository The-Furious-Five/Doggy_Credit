package com.doggycredit.tenancy;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import com.doggycredit.shared.ErrorCode;

/**
 * HAB-03: el token trae un tenant_id que no está en el registro maestro.
 * Se renderiza como RFC 9457; el correlationId lo inyecta ProblemDetailAdvice.
 */
public class TenantNotFoundException extends ErrorResponseException {

    public TenantNotFoundException(TenantId id) {
        super(HttpStatus.valueOf(ErrorCode.TENANT_NOT_FOUND.status()),
                ErrorCode.TENANT_NOT_FOUND.toProblemDetail(
                        "El tenant " + id + " no está registrado."),
                null);
    }
}
