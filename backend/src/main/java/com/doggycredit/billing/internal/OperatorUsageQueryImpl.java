package com.doggycredit.billing.internal;

import java.time.YearMonth;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.doggycredit.billing.OperatorUsageQuery;
import com.doggycredit.shared.RoleGuard;

@Service
class OperatorUsageQueryImpl implements OperatorUsageQuery {

    private final JdbcTemplate jdbc;

    OperatorUsageQueryImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Integer> evaluationsForMonth(YearMonth month) {
        RoleGuard.require("PLATFORM_ADMIN");
        return jdbc.query("select tenant_id, evaluaciones from billing.operator_monthly_usage(?)",
                (rs, row) -> Map.entry(rs.getObject("tenant_id", UUID.class), rs.getInt("evaluaciones")),
                month.toString()).stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
