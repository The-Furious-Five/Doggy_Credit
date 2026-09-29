package com.doggycredit.shared.internal;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * RF-API-02.1: filtro único de correlación. Lee o genera el id de
 * correlación (W3C trace-id expuesto como X-Correlation-Id), lo pone
 * en el MDC para que aparezca en cada línea de log y lo devuelve al
 * cliente en el header de respuesta.
 * <p>
 * No es {@code @Component}: se registra como bean en
 * {@link IdentitySecurityConfiguration} para evitar doble registro y
 * problemas de proxy con {@code GenericFilterBean}.
 */
public final class CorrelationFilter extends OncePerRequestFilter {

    static final String MDC_KEY = "correlationId";

    /**
     * El header lo controla el cliente y termina en cada línea de log y en la
     * respuesta: sin límite, alguien podría inyectar saltos de línea (líneas
     * de log falsas) o un valor enorme. Cubre UUID y trace-id W3C.
     */
    private static final java.util.regex.Pattern VALID_ID =
            java.util.regex.Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private final String headerName;

    public CorrelationFilter(String headerName) {
        this.headerName = headerName;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = request.getHeader(headerName);
        if (correlationId == null || !VALID_ID.matcher(correlationId).matches()) {
            correlationId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(headerName, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
