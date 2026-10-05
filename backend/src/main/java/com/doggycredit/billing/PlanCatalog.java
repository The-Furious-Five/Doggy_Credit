package com.doggycredit.billing;

import java.util.Optional;

/**
 * Public interface to access plan catalog.
 * LOS-65
 */
public interface PlanCatalog {
    Optional<PlanInfo> findById(String planId);
    PlanInfo requireById(String planId);
}
