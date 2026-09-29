package com.doggycredit.shared.internal;

import java.io.IOException;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.doggycredit.shared.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * RF-API-02.1 / RF-SEG-01.1: toda petición sin un JWT válido responde 401
 * con el mismo formato RFC 9457 que el resto de la API (ver
 * {@link SecurityProblemDetailWriter}), en vez de la página de error por
 * defecto del contenedor o el 401 sin cuerpo del recurso OAuth2 por defecto.
 */
@Component
class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final SecurityProblemDetailWriter writer;

    ProblemDetailAuthenticationEntryPoint(SecurityProblemDetailWriter writer) {
        this.writer = writer;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        writer.write(response, ErrorCode.UNAUTHENTICATED, "La solicitud no trae un token válido.");
    }
}
