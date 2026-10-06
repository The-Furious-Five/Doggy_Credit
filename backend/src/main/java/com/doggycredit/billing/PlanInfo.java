package com.doggycredit.billing;

/**
 * Public representation of a subscription plan.
 * LOS-65
 */
public record PlanInfo(
    String id,
    String nombre,
    int evaluacionesMes,
    int maxUsuarios,
    int maxIntegraciones,
    boolean scoringAvanzado,
    boolean apiProduccion,
    boolean webhooks,
    boolean analytics,
    boolean recomendaciones
) {}
