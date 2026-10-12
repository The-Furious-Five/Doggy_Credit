package com.doggycredit.tenancy.internal;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * HAB-03: registro maestro de tenant (esquema {@code tenancy}, migración
 * V2026_09_28_1100). Única fuente de verdad del tenant_id; los datos
 * iniciales se siembran desde {@code seed/tenants.yaml}.
 */
@Entity
@DynamicUpdate
@Table(schema = "tenancy", name = "tenants")
public class Tenant {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false, length = 20)
    private String nit;

    @Column(nullable = false, length = 20)
    private String tipo;

    @Column(nullable = false, length = 20)
    private String plan;

    @Column(name = "plan_anterior", length = 20)
    private String planAnterior;

    @Column(name = "gracia_desde")
    private Instant graciaDesde;

    @Column(name = "gracia_hasta")
    private Instant graciaHasta;

    // A-12: bajada programada para el cierre del período; null si no hay ninguna.
    @Column(name = "bajada_plan_destino", length = 20)
    private String bajadaPlanDestino;

    @Column(name = "bajada_efectiva_en")
    private Instant bajadaEfectivaEn;

    @Column(name = "bajada_solicitada_por")
    private String bajadaSolicitadaPor;

    @Column(name = "bajada_solicitada_en")
    private Instant bajadaSolicitadaEn;

    // @JdbcTypeCode(SqlTypes.CHAR), no solo columnDefinition: la migración
    // (V2026_09_28_1100) usa char(2)/char(3), no varchar, y Hibernate valida
    // por el código de tipo JDBC (bpchar/Types#CHAR), no por el texto del
    // columnDefinition. Sin esto, "ddl-auto: validate" rechaza el arranque —
    // antes pasaba inadvertido porque "ddl-auto: update" alteraba la columna
    // en cada arranque (DEBT-015).
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 2, columnDefinition = "char(2)")
    private String pais;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3, columnDefinition = "char(3)")
    private String moneda;

    @Column(nullable = false)
    private boolean activo;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    protected Tenant() {
    }

    UUID getId() {
        return id;
    }

    String getNombre() {
        return nombre;
    }

    String getNit() {
        return nit;
    }

    String getTipo() {
        return tipo;
    }

    String getPlan() {
        return plan;
    }

    String getPlanAnterior() {
        return planAnterior;
    }

    Instant getGraciaDesde() {
        return graciaDesde;
    }

    Instant getGraciaHasta() {
        return graciaHasta;
    }

    String getBajadaPlanDestino() {
        return bajadaPlanDestino;
    }

    Instant getBajadaEfectivaEn() {
        return bajadaEfectivaEn;
    }

    String getBajadaSolicitadaPor() {
        return bajadaSolicitadaPor;
    }

    Instant getBajadaSolicitadaEn() {
        return bajadaSolicitadaEn;
    }

    String getPais() {
        return pais;
    }

    String getMoneda() {
        return moneda;
    }

    boolean isActivo() {
        return activo;
    }

    void setPlan(String plan) {
        this.plan = plan;
    }

    void setPlanAnterior(String planAnterior) {
        this.planAnterior = planAnterior;
    }

    void setGraciaHasta(Instant graciaHasta) {
        this.graciaHasta = graciaHasta;
    }

    /** A-12: deja la bajada lista para el cierre del período; reemplaza la que hubiera. */
    void programarBajada(String planDestino, Instant efectivaEn, String solicitadaPor, Instant solicitadaEn) {
        this.bajadaPlanDestino = planDestino;
        this.bajadaEfectivaEn = efectivaEn;
        this.bajadaSolicitadaPor = solicitadaPor;
        this.bajadaSolicitadaEn = solicitadaEn;
    }

    void cancelarBajada() {
        programarBajada(null, null, null, null);
    }

    /** A-12: el tenant pasa al plan inferior y empieza la gracia; no borra nada. */
    void bajarA(String planDestino, Instant desde, Instant hasta) {
        this.planAnterior = this.plan;
        this.plan = planDestino;
        this.graciaDesde = desde;
        this.graciaHasta = hasta;
        cancelarBajada();
    }

    /** A-11: subir cancela la bajada programada y termina la gracia vigente. */
    void subirA(String planDestino) {
        this.plan = planDestino;
        this.planAnterior = null;
        this.graciaDesde = null;
        this.graciaHasta = null;
        cancelarBajada();
    }

    void setNombre(String nombre) {
        this.nombre = nombre;
    }

    void setActivo(boolean activo) {
        this.activo = activo;
    }
}
