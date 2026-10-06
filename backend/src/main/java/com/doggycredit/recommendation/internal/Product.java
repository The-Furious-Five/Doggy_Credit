package com.doggycredit.recommendation.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Producto del catálogo del tenant (tabla recommendation.products, C-02). */
@Entity
@Table(schema = "recommendation", name = "products")
class Product {

	@Id
	private UUID id;

	// SPIKE-1: discriminador de Hibernate; la RLS filtra además en la base de datos.
	@org.hibernate.annotations.TenantId
	@Column(name = "tenant_id", nullable = false)
	private UUID tenantId;

	@Column(name = "product_type", nullable = false)
	private String productType;

	@Column(nullable = false, length = 3)
	private String currency;

	@Column(name = "min_amount", nullable = false, precision = 19, scale = 2)
	private BigDecimal minAmount;

	@Column(name = "max_amount", nullable = false, precision = 19, scale = 2)
	private BigDecimal maxAmount;

	@Column(name = "min_term_months", nullable = false)
	private int minTermMonths;

	@Column(name = "max_term_months", nullable = false)
	private int maxTermMonths;

	@Column(name = "annual_rate", nullable = false, precision = 5, scale = 2)
	private BigDecimal annualRate;

	@Column(name = "min_band", nullable = false, length = 1)
	private String minBand;

	@Column(nullable = false)
	private boolean active;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	private long version;

	protected Product() {
	}

	/** Lo que el administrador puede cambiar: el interruptor y los rangos (no la tasa ni la banda). */
	void update(boolean active, BigDecimal minAmount, BigDecimal maxAmount, int minTermMonths, int maxTermMonths) {
		this.active = active;
		this.minAmount = minAmount;
		this.maxAmount = maxAmount;
		this.minTermMonths = minTermMonths;
		this.maxTermMonths = maxTermMonths;
		this.updatedAt = Instant.now();
	}

	UUID id() {
		return id;
	}

	String productType() {
		return productType;
	}

	String currency() {
		return currency;
	}

	BigDecimal minAmount() {
		return minAmount;
	}

	BigDecimal maxAmount() {
		return maxAmount;
	}

	int minTermMonths() {
		return minTermMonths;
	}

	int maxTermMonths() {
		return maxTermMonths;
	}

	BigDecimal annualRate() {
		return annualRate;
	}

	String minBand() {
		return minBand;
	}

	boolean active() {
		return active;
	}

}
