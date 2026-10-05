package com.doggycredit.billing.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

interface MonthlyUsageRepository extends JpaRepository<MonthlyUsage, MonthlyUsage.MonthlyUsageId> {
    // We might need a method to find by tenantId and yearMonth if not using findById
}
