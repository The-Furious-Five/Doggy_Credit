package com.doggycredit.analytics.internal;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.ErrorResponseException;

import com.doggycredit.shared.ErrorCode;
import com.doggycredit.shared.RoleGuard;
import com.doggycredit.shared.contract.model.CodigoPlan;
import com.doggycredit.shared.contract.model.MetricasOperador;
import com.doggycredit.shared.contract.model.TenantsPorPlan;
import com.doggycredit.tenancy.TenantInfo;
import com.doggycredit.tenancy.TenantRegistry;

/**
 * D-14 (RF-ANA-04): métricas de negocio de la plataforma. La foto (tenants por plan, activos y
 * suspendidos) sale del registro; lo del período, de las funciones de lectura de ADR-029. Solo
 * devuelve conteos: ningún nombre, NIT ni id de tenant sale de aquí.
 */
@Service
class OperatorMetricsService {

    private final OperatorMetricsRepository repository;
    private final TenantRegistry tenants;

    OperatorMetricsService(OperatorMetricsRepository repository, TenantRegistry tenants) {
        this.repository = repository;
        this.tenants = tenants;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public MetricasOperador summarize(LocalDate desde, LocalDate hasta) {
        RoleGuard.require("PLATFORM_ADMIN");
        // Por defecto el mes calendario UTC de la fecha que llegue, o del día de hoy.
        LocalDate until = hasta != null ? hasta
                : YearMonth.from(desde != null ? desde : LocalDate.now(ZoneOffset.UTC)).atEndOfMonth();
        LocalDate from = desde != null ? desde : until.withDayOfMonth(1);
        if (from.isAfter(until) || from.getYear() < 1 || until.getYear() > 9998) {
            throw new ErrorResponseException(HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION_ERROR.toProblemDetail("Desde debe ser anterior o igual a hasta; usa fechas entre los años 1 y 9998."), null);
        }
        var start = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        var end = until.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        var registered = tenants.findAll();
        Map<UUID, String> currentPlan = registered.stream().collect(Collectors.toMap(TenantInfo::id, TenantInfo::plan));
        var evaluations = repository.evaluations(start, end);
        Map<String, Long> evaluationsByPlan = evaluations.stream().filter(row -> currentPlan.containsKey(row.tenant()))
                .collect(Collectors.groupingBy(row -> currentPlan.get(row.tenant()),
                        Collectors.summingLong(OperatorMetricsRepository.TenantEvaluations::evaluations)));
        // Conversión = subida desde Gratis a un plan pago (PRO o ENTERPRISE); el CHECK impide FREE → FREE.
        long conversions = repository.planChanges(start, end).stream()
                .filter(change -> "FREE".equals(change.from()))
                .mapToLong(OperatorMetricsRepository.PlanChanges::count).sum();
        long active = registered.stream().filter(TenantInfo::activo).count();

        return new MetricasOperador().desde(from).hasta(until)
                .tenantsPorPlan(Arrays.stream(CodigoPlan.values()).map(plan -> new TenantsPorPlan().plan(plan)
                        .cantidad(registered.stream().filter(tenant -> plan.getValue().equals(tenant.plan())).count())
                        .evaluaciones(evaluationsByPlan.getOrDefault(plan.getValue(), 0L))).toList())
                .tenantsActivos(active).tenantsSuspendidos(registered.size() - active)
                // La función agrupa por tenant: cada fila es un tenant con al menos una evaluación.
                .tenantsActivados((long) evaluations.size())
                .conversionesFreeAPro(conversions)
                .evaluaciones(evaluations.stream().mapToLong(OperatorMetricsRepository.TenantEvaluations::evaluations).sum());
    }
}
