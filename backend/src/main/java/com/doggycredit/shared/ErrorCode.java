package com.doggycredit.shared;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * Catálogo único de códigos de error (contracts/errors/catalog.yaml).
 * RF-API-02.1: todo error 4xx/5xx usa un code de este enum.
 */
public enum ErrorCode {
    CONSENT_REQUIRED("consent-required", 422, "Consentimiento requerido"),
    QUOTA_EXCEEDED("quota-exceeded", 429, "Cuota mensual agotada"),
    RATE_LIMITED("rate-limited", 429, "Límite de solicitudes por minuto excedido"),
    FEATURE_NOT_IN_PLAN("feature-not-in-plan", 403, "Función no disponible en el plan actual"),
    PLAN_LIMIT_REACHED("plan-limit-reached", 409, "Límite del plan alcanzado"),
    PROVIDER_UNAVAILABLE("provider-unavailable", 502, "Fuente externa no disponible"),
    IDEMPOTENCY_KEY_REUSED("idempotency-key-reused", 422, "Clave de idempotencia reutilizada con un cuerpo distinto"),
    TENANT_NOT_FOUND("tenant-not-found", 403, "Tenant no registrado"),
    TENANT_SUSPENDED("tenant-suspended", 403, "Tenant suspendido"),
    UNAUTHENTICATED("unauthenticated", 401, "Autenticación requerida"),
    INVALID_TENANT_CLAIM("invalid-tenant-claim", 403, "Reclamo de tenant inválido"),
    VALIDATION_ERROR("validation-error", 400, "Error de validación"),
    APPLICANT_ALREADY_EXISTS("applicant-already-exists", 409, "Solicitante ya registrado"),
    APPLICANT_NOT_FOUND("applicant-not-found", 422, "Solicitante no registrado"),
    ASSESSMENT_NOT_FOUND("assessment-not-found", 404, "Evaluación no encontrada"),
    CONSENT_NOT_FOUND("consent-not-found", 404, "Consentimiento no encontrado"),
    UNKNOWN_APPLICANT("unknown-applicant", 404, "Solicitante no encontrado"),
    INSUFFICIENT_ROLE("insufficient-role", 403, "Rol insuficiente"),
    API_KEY_NOT_FOUND("api-key-not-found", 404, "Clave de API no encontrada"),
    PRODUCT_NOT_FOUND("product-not-found", 404, "Producto no encontrado"),
    POLICY_NOT_FOUND("policy-not-found", 404, "Regla no encontrada"),
    CONCURRENT_UPDATE("concurrent-update", 409, "Cambio simultáneo"),
    PLAN_CHANGE_NOT_ALLOWED("plan-change-not-allowed", 409, "Cambio de plan no permitido");

    private static final String BASE_URI = "https://docs.doggycredit.example/errors/";

    private final String type;
    private final int status;
    private final String title;

    ErrorCode(String type, int status, String title) {
        this.type = type;
        this.status = status;
        this.title = title;
    }

    public ProblemDetail toProblemDetail(String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.valueOf(status), detail);
        problem.setType(URI.create(BASE_URI + type));
        problem.setTitle(title);
        problem.setProperty("code", name());
        return problem;
    }

    public int status() { return status; }
    public String typeUri() { return BASE_URI + type; }
    public String title() { return title; }
}
