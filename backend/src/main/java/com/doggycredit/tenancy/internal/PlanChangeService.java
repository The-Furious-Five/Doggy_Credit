package com.doggycredit.tenancy.internal;

import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.ErrorResponseException;

import com.doggycredit.audit.AuditTrail;
import com.doggycredit.billing.PlanCatalog;
import com.doggycredit.shared.ErrorCode;
import com.doggycredit.shared.TenantContext;
import com.doggycredit.tenancy.PlanIntegrations;
import com.doggycredit.tenancy.TenantId;
import com.doggycredit.tenancy.TenantNotFoundException;

/**
 * A-11 / A-12 (RF-PLN-04 / RF-PLN-05): cambios de plan. El plan vive solo en
 * {@code tenancy.tenants} y las compuertas lo leen en cada petición, así que un
 * cambio se nota sin volver a iniciar sesión: no viaja en el token.
 * <ul>
 * <li>El operador sube o baja en el momento ({@code PATCH /admin/tenants/{id}}).</li>
 * <li>El administrador del tenant programa la bajada para el cierre del período
 * (mes calendario en UTC, el mismo de la cuota mensual).</li>
 * <li>Toda bajada abre 30 días de gracia: las integraciones que exceden el plan
 * nuevo quedan desactivadas, no borradas, y nada se purga antes de
 * {@code gracia.hasta}. Subir durante la gracia recupera lo desactivado.</li>
 * </ul>
 */
@Service
class PlanChangeService {

    static final Duration GRACE = Duration.ofDays(30);

    private static final Logger log = LoggerFactory.getLogger(PlanChangeService.class);
    private static final List<String> RANK = List.of("FREE", "PRO", "ENTERPRISE");
    private static final String RESOURCE = "TENANT";

    record Scheduled(String planDestino, Instant efectivaEn, List<String> integracionesADesactivar,
            String solicitadaPor, Instant solicitadaEn) {
    }

    record Grace(String planAnterior, Instant desde, Instant hasta, List<String> integracionesDesactivadas) {
    }

    /** {@code bajada} y {@code gracia} son null cuando no hay ninguna en curso. */
    record State(String plan, Instant periodoInicio, Instant periodoFin, Scheduled bajada, Grace gracia) {
    }

    private final TenantRepository tenants;
    private final PlanCatalog plans;
    private final PlanIntegrations integrations;
    private final AuditTrail audit;
    private final TransactionTemplate transactions;

    PlanChangeService(TenantRepository tenants, PlanCatalog plans, PlanIntegrations integrations, AuditTrail audit,
            TransactionTemplate transactions) {
        this.tenants = tenants;
        this.plans = plans;
        this.integrations = integrations;
        this.audit = audit;
        this.transactions = transactions;
    }

    State state(UUID tenantId) {
        return scoped(tenantId, () -> state(require(tenantId), Instant.now()));
    }

    /** @throws ErrorResponseException 409 PLAN_CHANGE_NOT_ALLOWED si el destino no es inferior al plan actual */
    State scheduleDowngrade(UUID tenantId, String planDestino, String solicitadaPor) {
        return scoped(tenantId, () -> {
            Tenant tenant = require(tenantId);
            if (rank(planDestino) >= rank(tenant.getPlan())) {
                throw new ErrorResponseException(HttpStatus.CONFLICT, ErrorCode.PLAN_CHANGE_NOT_ALLOWED.toProblemDetail(
                        "El plan " + planDestino + " no es inferior al plan actual " + tenant.getPlan() + "."), null);
            }
            Instant now = Instant.now();
            tenant.programarBajada(planDestino, periodEnd(now), solicitadaPor, now);
            tenants.save(tenant);
            audit.record(tenantId, "PLAN_DOWNGRADE_SCHEDULED", RESOURCE, tenantId);
            return state(tenant, now);
        });
    }

    /** Idempotente; no corta una gracia ya iniciada. */
    State cancelDowngrade(UUID tenantId) {
        return scoped(tenantId, () -> {
            Tenant tenant = require(tenantId);
            if (tenant.getBajadaPlanDestino() != null) {
                tenant.cancelarBajada();
                tenants.save(tenant);
                audit.record(tenantId, "PLAN_DOWNGRADE_CANCELLED", RESOURCE, tenantId);
            }
            return state(tenant, Instant.now());
        });
    }

    /**
     * El operador cambia el plan en el momento, en cualquier sentido. Corre a nombre
     * del tenant afectado, no del operador: sus integraciones y su bitácora están bajo RLS.
     */
    void changeNow(UUID tenantId, String plan) {
        scoped(tenantId, () -> {
            Tenant tenant = require(tenantId);
            int change = rank(plan) - rank(tenant.getPlan());
            if (change > 0) {
                upgrade(tenant, plan, Instant.now());
            }
            else if (change < 0) {
                downgrade(tenant, plan, Instant.now());
            }
            return null;
        });
    }

    /** Aplica las bajadas programadas cuyo período ya cerró; devuelve cuántas aplicó. */
    int applyDue(Instant now) {
        int applied = 0;
        for (Tenant due : tenants.findByBajadaEfectivaEnLessThanEqual(now)) {
            UUID id = due.getId();
            try {
                applied += scoped(id, () -> applyScheduled(id, now));
            }
            catch (RuntimeException e) {
                // Una bajada que falla no frena las demás: se reintenta en la siguiente pasada.
                log.error("No se pudo aplicar la bajada de plan programada del tenant {}", id, e);
            }
        }
        return applied;
    }

    private int applyScheduled(UUID tenantId, Instant now) {
        Tenant tenant = require(tenantId);
        if (tenant.getBajadaEfectivaEn() == null || tenant.getBajadaEfectivaEn().isAfter(now)) {
            return 0; // cancelada o reprogramada entre la consulta y esta transacción
        }
        downgrade(tenant, tenant.getBajadaPlanDestino(), now);
        return 1;
    }

    private void upgrade(Tenant tenant, String plan, Instant now) {
        String previous = tenant.getPlan();
        boolean inGrace = tenant.getGraciaHasta() != null && tenant.getGraciaHasta().isAfter(now);
        tenant.subirA(plan);
        tenants.save(tenant);
        if (inGrace) {
            integrations.restoreSuspended(tenant.getId(), plans.requireById(plan).maxIntegraciones());
        }
        audit.record(tenant.getId(), "PLAN_UPGRADED", RESOURCE, tenant.getId());
        log.info("Plan del tenant {}: {} -> {}", tenant.getId(), previous, plan);
    }

    private void downgrade(Tenant tenant, String plan, Instant now) {
        String previous = tenant.getPlan();
        tenant.bajarA(plan, now, now.plus(GRACE));
        tenants.save(tenant);
        List<String> suspended = integrations.suspendExceeding(tenant.getId(),
                plans.requireById(plan).maxIntegraciones());
        audit.record(tenant.getId(), "PLAN_DOWNGRADED", RESOURCE, tenant.getId());
        log.info("Plan del tenant {}: {} -> {}, gracia hasta {}, integraciones desactivadas {}", tenant.getId(),
                previous, plan, tenant.getGraciaHasta(), suspended);
    }

    private State state(Tenant tenant, Instant now) {
        Scheduled scheduled = tenant.getBajadaPlanDestino() == null ? null
                : new Scheduled(tenant.getBajadaPlanDestino(), tenant.getBajadaEfectivaEn(),
                        integrations.exceeding(tenant.getId(),
                                plans.requireById(tenant.getBajadaPlanDestino()).maxIntegraciones()),
                        tenant.getBajadaSolicitadaPor(), tenant.getBajadaSolicitadaEn());
        Grace grace = tenant.getGraciaHasta() == null || !tenant.getGraciaHasta().isAfter(now) ? null
                : new Grace(tenant.getPlanAnterior(), tenant.getGraciaDesde(), tenant.getGraciaHasta(),
                        integrations.suspended(tenant.getId()));
        return new State(tenant.getPlan(), periodEnd(now).atOffset(ZoneOffset.UTC).minusMonths(1).toInstant(),
                periodEnd(now), scheduled, grace);
    }

    /** Inicio del mes siguiente en UTC (exclusivo), como la cuota de {@code UsageMeterImpl}. */
    private static Instant periodEnd(Instant now) {
        return YearMonth.from(now.atOffset(ZoneOffset.UTC)).plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC)
            .toInstant();
    }

    private static int rank(String plan) {
        return RANK.indexOf(plan);
    }

    private Tenant require(UUID tenantId) {
        return tenants.findById(tenantId).orElseThrow(() -> new TenantNotFoundException(TenantId.of(tenantId)));
    }

    private <T> T scoped(UUID tenantId, Supplier<T> action) {
        UUID previous = TenantContext.currentOrNone();
        TenantContext.set(tenantId);
        try {
            // El contexto debe existir antes de abrir la conexión: de ahí sale el tenant de la RLS.
            return transactions.execute(tx -> action.get());
        }
        finally {
            if (previous == null) {
                TenantContext.clear();
            }
            else {
                TenantContext.set(previous);
            }
        }
    }

}
