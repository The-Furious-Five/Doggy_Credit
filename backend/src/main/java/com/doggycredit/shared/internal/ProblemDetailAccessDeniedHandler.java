package com.doggycredit.shared.internal;

import java.io.IOException;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.doggycredit.shared.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * RF-API-02.1 / RF-SEG-01.1: hoy la única fuente de {@link AccessDeniedException}
 * en la cadena de seguridad es {@code TenantClaimFilter} (tenant_id ausente
 * o mal formado), así que responde siempre INVALID_TENANT_CLAIM. Si más
 * adelante se agrega autorización por rol (p. ej. {@code @PreAuthorize}),
 * este manejador deja de ser específico y hay que revisarlo — no hay
 * todavía forma de distinguir el motivo del rechazo desde aquí.
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
