package com.doggycredit.billing.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA Entity for billing.plans
 * LOS-65
 */
@Entity
@Table(schema = "billing", name = "plans")
class Plan {
    @Id
    private String id;
    private String nombre;
    @Column(name = "evaluaciones_mes")
    private int evaluacionesMes;
    @Column(name = "max_usuarios")
    private int maxUsuarios;
    @Column(name = "max_integraciones")
    private int maxIntegraciones;
    @Column(name = "scoring_avanzado")
    private boolean scoringAvanzado;
    @Column(name = "api_produccion")
    private boolean apiProduccion;
    private boolean webhooks;
    private boolean analytics;
    private boolean recomendaciones;
    private boolean activo;

    protected Plan() {
    }

    String getId() { return id; }
    String getNombre() { return nombre; }
    int getEvaluacionesMes() { return evaluacionesMes; }
    int getMaxUsuarios() { return maxUsuarios; }
    int getMaxIntegraciones() { return maxIntegraciones; }
    boolean isScoringAvanzado() { return scoringAvanzado; }
    boolean isApiProduccion() { return apiProduccion; }
    boolean isWebhooks() { return webhooks; }
    boolean isAnalytics() { return analytics; }
    boolean isRecomendaciones() { return recomendaciones; }
    boolean isActivo() { return activo; }
}
