package com.doggycredit.analytics.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.ErrorResponseException;

import com.doggycredit.analytics.ModelDrift;
import com.doggycredit.billing.Feature;
import com.doggycredit.billing.PlanGate;
import com.doggycredit.recommendation.NeedCategory;
import com.doggycredit.shared.ErrorCode;
import com.doggycredit.shared.SensitiveDataPolicy;
import com.doggycredit.shared.SensitiveDataPolicy.Masker;
import com.doggycredit.shared.TenantContext;
import com.doggycredit.shared.contract.model.AnalyticsAceptacionProducto;
import com.doggycredit.shared.contract.model.AnalyticsBanda;
import com.doggycredit.shared.contract.model.AnalyticsModelo;
import com.doggycredit.shared.contract.model.AnalyticsOverride;
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
    private final PlanGate planGate;
    private final ModelDrift drift;
    private final SensitiveDataPolicy sensitiveData;

    AnalyticsSummaryService(AnalyticsSummaryRepository repository, TenantRegistry tenants,
            PlanGate planGate, ModelDrift drift, SensitiveDataPolicy sensitiveData) {
        this.repository = repository;
        this.tenants = tenants;
        this.planGate = planGate;
        this.drift = drift;
        this.sensitiveData = sensitiveData;
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
        boolean analyticsAvailable = planGate.isAvailable(tenant.plan(), Feature.ANALYTICS);
        var start = from.atStartOfDay(ZONE).toInstant();
        var end = until.plusDays(1).atStartOfDay(ZONE).toInstant();
        var rows = repository.basic(tenantId, start, end);
        var total = rows.stream().filter(AnalyticsSummaryRepository.Basic::total).findFirst().orElseThrow();
        var response = new AnalyticsResumen().desde(from).hasta(until).moneda(tenant.moneda())
                .analiticaAvanzada(analyticsAvailable).evaluaciones(total.evaluations()).scorePromedio(total.averageScore())
                .integracionesActivas(repository.activeIntegrations(tenantId))
                .porcentajeConRecomendacion(percent(total.recommended(), total.evaluations()))
                .necesidades(Arrays.stream(NeedCategory.values()).map(category -> {
                    long count = rows.stream().filter(row -> !row.total() && category.name().equals(row.category()))
                            .mapToLong(AnalyticsSummaryRepository.Basic::evaluations).sum();
                    return new AnalyticsNecesidad().categoria(AnalyticsNecesidad.CategoriaEnum.fromValue(category.name()))
                            .cantidad(count).porcentaje(percent(count, total.evaluations()));
                }).toList());
        if (analyticsAvailable) {
            var premiumRows = repository.premium(tenantId, start, end);
            var premiumTotal = premiumRows.stream().filter(AnalyticsSummaryRepository.Premium::total).findFirst().orElseThrow();
            Masker mask = sensitiveData.forCurrentUser();
            response.pro(new AnalyticsPro().conversionPorcentaje(percent(premiumTotal.preapproved(), total.evaluations()))
                    .tiempoDecisionMedianaMs(premiumTotal.medianMs()).montoRecomendado(mask.amount(premiumTotal.amount()))
                    .recomendacionesPorProducto(premiumRows.stream().filter(row -> !row.total() && row.productType() != null)
                            .map(row -> new AnalyticsProducto().tipo(AnalyticsProducto.TipoEnum.fromValue(row.productType()))
                                    .cantidad(row.evaluations()).porcentaje(percent(row.evaluations(), total.recommended()))
                                    .montoRecomendado(mask.amount(row.amount()))).toList())
                    .modelos(drift.latest(tenantId).stream().map(model -> new AnalyticsModelo().version(model.modelVersion())
                            .psi(model.psi()).estado(AnalyticsModelo.EstadoEnum.fromValue(model.level().name()))
                            .desde(model.windowStart().atOffset(ZoneOffset.UTC)).hasta(model.windowEnd().atOffset(ZoneOffset.UTC))
                            .muestrasActuales(model.actualCount()).muestrasBase(model.baselineCount())).toList()));
            portfolio(response.getPro(), tenantId, start, end);
        }
        return response.plan(tenant.plan());
    }

    /**
     * C-14 (LOS-130), RF-ANA-02: KPIs de cartera. Siempre las cinco bandas y los tres tipos de
     * producto, con 0 si no hay casos; la base de bandas y override es la misma.
     */
    private void portfolio(AnalyticsPro pro, UUID tenantId, Instant start, Instant end) {
        Map<String, Long> bands = repository.bands(tenantId, start, end);
        long decided = bands.values().stream().mapToLong(Long::longValue).sum();
        long overrides = repository.overrides(tenantId, start, end);
        var acceptance = repository.acceptance(tenantId, start, end);
        pro.distribucionBandas(Arrays.stream(AnalyticsBanda.BandaEnum.values()).map(band -> {
                    long count = bands.getOrDefault(band.getValue(), 0L);
                    return new AnalyticsBanda().banda(band).cantidad(count).porcentaje(percent(count, decided));
                }).toList())
                .tasaOverride(new AnalyticsOverride().overrides(overrides).evaluaciones(decided)
                        .porcentaje(percent(overrides, decided)))
                .aceptacionPorProducto(Arrays.stream(AnalyticsAceptacionProducto.TipoEnum.values()).map(type -> {
                    var row = acceptance.stream().filter(a -> type.getValue().equals(a.productType())).findFirst()
                            .orElse(new AnalyticsSummaryRepository.Acceptance(type.getValue(), 0, 0));
                    return new AnalyticsAceptacionProducto().tipo(type).recomendados(row.recommended())
                            .aceptados(row.accepted()).porcentaje(percent(row.accepted(), row.recommended()));
                }).toList());
    }

    private static Double percent(long count, long total) {
        return total == 0 ? 0.0 : BigDecimal.valueOf(count).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP).doubleValue();
    }
}
