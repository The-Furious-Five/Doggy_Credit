package com.doggycredit.billing;

import java.time.YearMonth;
import java.util.Map;
import java.util.UUID;

/** Lectura de contadores entre tenants para PLATFORM_ADMIN; no expone datos de evaluaciones. */
public interface OperatorUsageQuery {

    Map<UUID, Integer> evaluationsForMonth(YearMonth month);
}
