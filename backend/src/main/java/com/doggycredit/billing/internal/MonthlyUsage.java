package com.doggycredit.billing.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA Entity for billing.monthly_usage
 * LOS-65
 */
@Entity
@Table(schema = "billing", name = "monthly_usage")
@IdClass(MonthlyUsage.MonthlyUsageId.class)
class MonthlyUsage {

    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Id
    @Column(name = "year_month", length = 7)
    private String yearMonth;

    private int evaluaciones;

    protected MonthlyUsage() {}

    MonthlyUsage(UUID tenantId, String yearMonth) {
        this.tenantId = tenantId;
        this.yearMonth = yearMonth;
        this.evaluaciones = 0;
    }

    UUID getTenantId() { return tenantId; }
    String getYearMonth() { return yearMonth; }
    int getEvaluaciones() { return evaluaciones; }

    void incrementEvaluaciones() {
        this.evaluaciones++;
    }

    static class MonthlyUsageId implements Serializable {
        private UUID tenantId;
        private String yearMonth;

        public MonthlyUsageId() {}

        public MonthlyUsageId(UUID tenantId, String yearMonth) {
            this.tenantId = tenantId;
            this.yearMonth = yearMonth;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MonthlyUsageId that)) return false;
            return Objects.equals(tenantId, that.tenantId) &&
                   Objects.equals(yearMonth, that.yearMonth);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tenantId, yearMonth);
        }
    }
}
