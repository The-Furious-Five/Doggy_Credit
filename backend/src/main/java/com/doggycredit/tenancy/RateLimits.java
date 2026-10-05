package com.doggycredit.tenancy;

import java.util.UUID;

/**
 * A-07: límite de solicitudes por minuto de cada tenant, según su plan
 * (Free 30, Pro 300, Enterprise 1.000; informe de arquitectura, PL-05). Lo
 * aplica la API a toda petición autenticada; esta interfaz expone el consumo
 * para {@code GET /tenant/actual} y la barra «Solicitudes por minuto».
 */
public interface RateLimits {

	RateLimitUsage usage(UUID tenantId);

	/** @param usedThisMinute solicitudes consumidas en la ventana de un minuto en curso. */
	record RateLimitUsage(int limitPerMinute, int usedThisMinute) {
	}

}
