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

    void setNombre(String nombre) {
        this.nombre = nombre;
    }

    void setActivo(boolean activo) {
        this.activo = activo;
    }
}
