package com.doggycredit.tenancy.internal;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.doggycredit.audit.AuditTrail;
import com.doggycredit.shared.TenantContext;
import com.doggycredit.tenancy.TenantId;
import com.doggycredit.tenancy.TenantInfo;
import com.doggycredit.tenancy.TenantNotFoundException;
import com.doggycredit.tenancy.TenantRegistry;

/** E-14: estado y auditoría se confirman juntos bajo el RLS del tenant afectado. */
@Service
class TenantStatusService {

    private final TenantRepository tenants;
    private final TenantRegistry registry;
    private final AuditTrail audit;
    private final TransactionTemplate transactions;

    TenantStatusService(TenantRepository tenants, TenantRegistry registry, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        this.tenants = tenants;
        this.registry = registry;
        this.audit = audit;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    TenantInfo setActive(UUID tenantId, boolean active) {
        UUID previous = TenantContext.currentOrNone();
        TenantContext.set(tenantId);
        try {
            // Abrir la conexión después de fijar el contexto, nunca con el tenant del operador.
            return transactions.execute(tx -> {
                Tenant tenant = tenants.findForUpdate(tenantId)
                        .orElseThrow(() -> new TenantNotFoundException(TenantId.of(tenantId)));
                if (tenant.isActivo() == active) {
                    return registry.requireById(TenantId.of(tenantId));
                }
                TenantInfo result = registry.update(TenantId.of(tenantId), null, active);
                audit.record(tenantId, active ? "TENANT_REACTIVATED" : "TENANT_SUSPENDED", "TENANT", tenantId);
                return result;
            });
        } finally {
            if (previous == null) {
                TenantContext.clear();
            } else {
                TenantContext.set(previous);
            }
        }
    }
}
