package com.doggycredit.integration.internal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.doggycredit.audit.AuditTrail;
import com.doggycredit.billing.PlanCatalog;
import com.doggycredit.consent.Source;
import com.doggycredit.integration.PlanLimitReachedException;
import com.doggycredit.integration.TenantIntegrations;
import com.doggycredit.tenancy.PlanIntegrations;
import com.doggycredit.tenancy.TenantId;
import com.doggycredit.tenancy.TenantInfo;
import com.doggycredit.tenancy.TenantRegistry;

/**
 * B-06: catálogo de 6 conectores con su estado por tenant y activación con el
 * límite del plan. El buró es obligatorio (siempre activo); banco y billetera
 * empiezan inactivos; open banking, facturación y garantías no tienen
 * implementación (DEBT-004).
 */
@Service
class IntegrationService implements TenantIntegrations, PlanIntegrations {

	/** A-12: quién desactivó la fila. Mientras siga así, una subida durante la gracia la recupera. */
	static final String SYSTEM_DOWNGRADE = "SYSTEM_DOWNGRADE";
	static final String SYSTEM_UPGRADE = "SYSTEM_UPGRADE";

	enum Entry {
		BURO, BANCO, WALLET, OPEN_BANKING, FACTURACION, GARANTIAS;

		Source source() {
			return switch (this) {
				case BURO -> Source.BURO;
				case BANCO -> Source.BANCO;
				case WALLET -> Source.WALLET;
				default -> null; // PROXIMAMENTE
			};
		}
	}

	enum Status {
		CONECTADO, DISPONIBLE, REQUIERE_PRO, PROXIMAMENTE
	}

	record Integration(Entry entry, Status status) {
	}

	private final TenantIntegrationRepository integrations;
	private final TenantRegistry tenants;
	private final PlanCatalog plans;
	private final AuditTrail audit;

	IntegrationService(TenantIntegrationRepository integrations, TenantRegistry tenants, PlanCatalog plans,
			AuditTrail audit) {
		this.integrations = integrations;
		this.tenants = tenants;
		this.plans = plans;
		this.audit = audit;
	}

	@Override
	@Transactional(readOnly = true)
	public Set<Source> activeSources(UUID tenantId) {
		Set<Source> active = EnumSet.of(Source.BURO);
		for (TenantIntegration row : integrations.findByTenantId(tenantId)) {
			Source source = Source.valueOf(row.source());
			if (source != Source.BURO && row.active()) {
				active.add(source);
			}
		}
		return active;
	}

	@Override
	@Transactional(readOnly = true)
	public int activeCount(UUID tenantId) {
		return activeSources(tenantId).size();
	}

	/** El buró cuenta y es obligatorio: las que sobran son las últimas del catálogo. */
	@Override
	@Transactional(readOnly = true)
	public List<String> exceeding(UUID tenantId, int maxIntegraciones) {
		return activeSources(tenantId).stream().skip(Math.max(maxIntegraciones, 1)).map(Source::name).toList();
	}

	@Override
	@Transactional
	public List<String> suspendExceeding(UUID tenantId, int maxIntegraciones) {
		List<String> exceeding = exceeding(tenantId, maxIntegraciones);
		for (TenantIntegration row : integrations.findByTenantId(tenantId)) {
			if (row.active() && exceeding.contains(row.source())) {
				row.set(false, SYSTEM_DOWNGRADE);
				integrations.save(row);
				audit.record(tenantId, "INTEGRATION_DISABLED", "INTEGRATION", null);
			}
		}
		return exceeding;
	}

	@Override
	@Transactional(readOnly = true)
	public List<String> suspended(UUID tenantId) {
		return integrations.findByTenantId(tenantId).stream().filter(TenantIntegration::suspendedByDowngrade)
			.map(TenantIntegration::source).sorted(Comparator.comparing(Source::valueOf)).toList();
	}

	@Override
	@Transactional
	public List<String> restoreSuspended(UUID tenantId, int maxIntegraciones) {
		int free = maxIntegraciones - activeSources(tenantId).size();
		List<String> restored = suspended(tenantId).stream().limit(Math.max(free, 0)).toList();
		for (TenantIntegration row : integrations.findByTenantId(tenantId)) {
			if (restored.contains(row.source())) {
				row.set(true, SYSTEM_UPGRADE);
				integrations.save(row);
				audit.record(tenantId, "INTEGRATION_ENABLED", "INTEGRATION", null);
			}
		}
		return restored;
	}

	@Transactional(readOnly = true)
	List<Integration> list(UUID tenantId) {
		Set<Source> active = activeSources(tenantId);
		boolean full = active.size() >= maxActive(tenantId);
		List<Integration> result = new ArrayList<>();
		for (Entry entry : Entry.values()) {
			Status status = entry.source() == null ? Status.PROXIMAMENTE
					: active.contains(entry.source()) ? Status.CONECTADO
					: full ? Status.REQUIERE_PRO : Status.DISPONIBLE;
			result.add(new Integration(entry, status));
		}
		return result;
	}

	/** @throws IllegalArgumentException conector sin implementación o desactivar el buró (400) */
	@Transactional
	Integration update(UUID tenantId, Entry entry, boolean activate, String by) {
		Source source = entry.source();
		if (source == null) {
			throw new IllegalArgumentException(entry + " todavía no tiene implementación (DEBT-004).");
		}
		if (source == Source.BURO && !activate) {
			throw new IllegalArgumentException("El buró es obligatorio y no se puede desactivar.");
		}
		Set<Source> active = activeSources(tenantId);
		if (activate && !active.contains(source) && active.size() >= maxActive(tenantId)) {
			throw new PlanLimitReachedException(plan(tenantId), maxActive(tenantId));
		}
		if (source != Source.BURO && active.contains(source) != activate) {
			TenantIntegration row = integrations.findByTenantIdAndSource(tenantId, source.name())
				.orElseGet(() -> new TenantIntegration(tenantId, source.name()));
			row.set(activate, by);
			integrations.save(row);
			audit.record(tenantId, activate ? "INTEGRATION_ENABLED" : "INTEGRATION_DISABLED", "INTEGRATION", null);
		}
		return list(tenantId).get(entry.ordinal());
	}

	private int maxActive(UUID tenantId) {
		return plans.requireById(plan(tenantId)).maxIntegraciones();
	}

	/** Un tenant que no está en el registro se trata como FREE: falla hacia lo más restrictivo. */
	private String plan(UUID tenantId) {
		return tenants.findById(new TenantId(tenantId)).map(TenantInfo::plan).orElse("FREE");
	}

}
