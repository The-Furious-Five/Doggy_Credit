package com.doggycredit.shared.internal;

import java.time.Instant;

import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * RF-API-02.1: inyecta {@code correlationId} y {@code timestamp} en toda
 * respuesta {@link ProblemDetail} (RFC 9457), sin importar quién la generó
 * (una excepción de negocio, Spring Security, Bean Validation, etc.).
 */
@ControllerAdvice
class ProblemDetailAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType,
            Class<? extends HttpMessageConverter<?>> converterType) {
        // MVC entrega también los cuerpos de ResponseEntity<ProblemDetail>.
        // El tipo declarado del método puede ser ResponseEntity o ErrorResponse:
        // comprobar el cuerpo real evita perder las extensiones en esos casos.
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body,
            MethodParameter returnType, MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof ProblemDetail problem) {
            String cid = MDC.get(CorrelationFilter.MDC_KEY);
            if (cid != null) {
                problem.setProperty("correlationId", cid);
            }
            problem.setProperty("timestamp", Instant.now().toString());
        }
        return body;
    }
}
