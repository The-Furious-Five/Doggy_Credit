package com.doggycredit.shared.internal;

import java.io.IOException;
import java.time.Instant;

import org.slf4j.MDC;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

import com.doggycredit.shared.ErrorCode;

import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * RF-API-02.1: la misma forma de {@link ProblemDetail} (RFC 9457, con
 * {@code correlationId} y {@code timestamp}) que {@link ProblemDetailAdvice}
 * les da a las excepciones de negocio, pero para los rechazos de Spring
 * Security. {@code AuthenticationEntryPoint}/{@code AccessDeniedHandler}
 * escriben directo al {@link HttpServletResponse}, fuera del pipeline de
 * conversión de Spring MVC — por eso {@code ProblemDetailAdvice} (un
 * {@code ResponseBodyAdvice}) nunca los alcanza, y hace falta este
 * duplicado mínimo en vez de reusarlo.
 */
@Component
class SecurityProblemDetailWriter {

    private final ObjectMapper mapper;

    SecurityProblemDetailWriter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    void write(HttpServletResponse response, ErrorCode code, String detail) throws IOException {
        ProblemDetail problem = code.toProblemDetail(detail);
        String correlationId = MDC.get(CorrelationFilter.MDC_KEY);
        if (correlationId != null) {
            problem.setProperty("correlationId", correlationId);
        }
        problem.setProperty("timestamp", Instant.now().toString());

        response.setStatus(code.status());
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getOutputStream(), problem);
    }
}
