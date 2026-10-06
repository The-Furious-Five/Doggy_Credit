package com.doggycredit.analytics.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.ErrorResponseException;

import com.doggycredit.analytics.ModelDrift;
import com.doggycredit.billing.PlanCatalog;
import com.doggycredit.recommendation.NeedCategory;
import com.doggycredit.shared.ErrorCode;
import com.doggycredit.shared.TenantContext;
import com.doggycredit.shared.contract.model.AnalyticsModelo;
import com.doggycredit.shared.contract.model.AnalyticsNecesidad;
import com.doggycredit.shared.contract.model.AnalyticsPro;
import com.doggycredit.shared.contract.model.AnalyticsProducto;
import com.doggycredit.shared.contract.model.AnalyticsResumen;
import com.doggycredit.tenancy.TenantId;
import com.doggycredit.tenancy.TenantRegistry;

@Service
class AnalyticsSummaryService {

    private static final ZoneId ZONE = ZoneId.of("America/La_Paz");
    private final AnalyticsSummaryRepository repository;
    private final TenantRegistry tenants;
    private final PlanCatalog plans;
    private final ModelDrift drift;

    AnalyticsSummaryService(AnalyticsSummaryRepository repository, TenantRegistry tenants,
            PlanCatalog plans, ModelDrift drift) {
        this.repository = repository;
        this.tenants = tenants;
        this.plans = plans;
        this.drift = drift;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AnalyticsResumen summarize(LocalDate desde, LocalDate hasta) {
        LocalDate until = hasta == null ? LocalDate.now(ZONE) : hasta;
        LocalDate from = desde == null ? until.withDayOfMonth(1) : desde;
        if (from.isAfter(until) || from.getYear() < 1 || until.getYear() > 9998) {
            throw new ErrorResponseException(HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION_ERROR.toProblemDetail("Desde debe ser anterior o igual a hasta; usa fechas entre los años 1 y 9998."), null);
        }
        var tenantId = Objects.requireNonNull(TenantContext.currentOrNone(), "tenant autenticado");
        var tenant = tenants.requireById(TenantId.of(tenantId));
        var plan = plans.requireById(tenant.plan());
        var start = from.atStartOfDay(ZONE).toInstant();
        var end = until.plusDays(1).atStartOfDay(ZONE).toInstant();
        var rows = repository.basic(tenantId, start, end);
        var total = rows.stream().filter(AnalyticsSummaryRepository.Basic::total).findFirst().orElseThrow();
        var response = new AnalyticsResumen().desde(from).hasta(until).plan(plan.id()).moneda(tenant.moneda())
                .analiticaAvanzada(plan.analytics()).evaluaciones(total.evaluations()).scorePromedio(total.averageScore())
                .integracionesActivas(repository.activeIntegrations(tenantId))
                .porcentajeConRecomendacion(percent(total.recommended(), total.evaluations()))
                .necesidades(Arrays.stream(NeedCategory.values()).map(category -> {
                    long count = rows.stream().filter(row -> !row.total() && category.name().equals(row.category()))
                            .mapToLong(AnalyticsSummaryRepository.Basic::evaluations).sum();
                    return new AnalyticsNecesidad().categoria(AnalyticsNecesidad.CategoriaEnum.fromValue(category.name()))
                            .cantidad(count).porcentaje(percent(count, total.evaluations()));
                }).toList());
        if (plan.analytics()) {
            var premiumRows = repository.premium(tenantId, start, end);
            var premiumTotal = premiumRows.stream().filter(AnalyticsSummaryRepository.Premium::total).findFirst().orElseThrow();
            response.pro(new AnalyticsPro().conversionPorcentaje(percent(premiumTotal.preapproved(), total.evaluations()))
                    .tiempoDecisionMedianaMs(premiumTotal.medianMs()).montoRecomendado(premiumTotal.amount())
                    .recomendacionesPorProducto(premiumRows.stream().filter(row -> !row.total() && row.productType() != null)
                            .map(row -> new AnalyticsProducto().tipo(AnalyticsProducto.TipoEnum.fromValue(row.productType()))
                                    .cantidad(row.evaluations()).porcentaje(percent(row.evaluations(), total.recommended()))
                                    .montoRecomendado(row.amount())).toList())
                    .modelos(drift.latest(tenantId).stream().map(model -> new AnalyticsModelo().version(model.modelVersion())
                            .psi(model.psi()).estado(AnalyticsModelo.EstadoEnum.fromValue(model.level().name()))
                            .desde(model.windowStart().atOffset(ZoneOffset.UTC)).hasta(model.windowEnd().atOffset(ZoneOffset.UTC))
                            .muestrasActuales(model.actualCount()).muestrasBase(model.baselineCount())).toList()));
        }
        return response;
    }

    private static Double percent(long count, long total) {
        return total == 0 ? 0.0 : BigDecimal.valueOf(count).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP).doubleValue();
    }
}
