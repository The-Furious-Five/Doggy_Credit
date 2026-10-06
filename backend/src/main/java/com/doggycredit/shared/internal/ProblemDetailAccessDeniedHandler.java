package com.doggycredit.shared.internal;

import java.io.IOException;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.doggycredit.shared.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * RF-API-02.1 / RF-SEG-01.1: responde INVALID_TENANT_CLAIM cuando el filtro
 * JWT rechaza un tenant ausente o mal formado. Los rechazos por rol usan
 * {@link com.doggycredit.shared.RoleGuard} y conservan INSUFFICIENT_ROLE.
 */
@Component
public class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {

    private final SecurityProblemDetailWriter writer;

    ProblemDetailAccessDeniedHandler(SecurityProblemDetailWriter writer) {
        this.writer = writer;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        writer.write(response, ErrorCode.INVALID_TENANT_CLAIM,
                "El tenant_id del token falta o no es un UUID válido.");
    }
}
