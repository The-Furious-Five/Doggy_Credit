package com.doggycredit.billing;

/**
 * LOS-68: verifies that a feature is available in the tenant's plan.
 * Throws FeatureNotInPlanException if not.
 */
public interface PlanGate {
    void requireFeature(String planId, Feature feature);
}
