package com.doggycredit.billing.internal;

import com.doggycredit.billing.UsageMeter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.UUID;

/**
 * Implementation of UsageMeter.
 * LOS-65
 */
@Service
class UsageMeterImpl implements UsageMeter {

    private final MonthlyUsageRepository monthlyUsageRepository;
    private final JdbcTemplate jdbc;

    UsageMeterImpl(MonthlyUsageRepository monthlyUsageRepository, JdbcTemplate jdbc) {
        this.monthlyUsageRepository = monthlyUsageRepository;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public int currentMonthEvaluations(UUID tenantId) {
        String yearMonth = YearMonth.now(java.time.ZoneOffset.UTC).toString();
        var id = new MonthlyUsage.MonthlyUsageId(tenantId, yearMonth);
        return monthlyUsageRepository.findById(id)
                .map(MonthlyUsage::getEvaluaciones)
                .orElse(0);
    }

    @Override
    @Transactional
    public boolean tryRecordEvaluation(UUID tenantId, int monthlyLimit) {
        String yearMonth = YearMonth.now(java.time.ZoneOffset.UTC).toString();
        return !jdbc.query("""
                insert into billing.monthly_usage (tenant_id, year_month, evaluaciones)
                values (?, ?, 1)
                on conflict (tenant_id, year_month) do update
                   set evaluaciones = billing.monthly_usage.evaluaciones + 1
                 where ? = 0 or billing.monthly_usage.evaluaciones < ?
                returning evaluaciones
                """, (rs, rowNum) -> rs.getInt(1), tenantId, yearMonth, monthlyLimit, monthlyLimit).isEmpty();
    }
}
