package com.doggycredit.billing;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * A-02 (LOS-65): fuente única de lo que el plan vigente de un tenant permite. Se
 * resuelve en cada llamada desde la base (plan en {@code tenancy.tenants}, límites y
 * funciones en {@code billing.plans}): un cambio de plan se nota sin volver a
 * iniciar sesión (A-11).
 */
public interface Entitlements {

    /** @throws IllegalArgumentException si el tenant o su plan no existen */
    Granted of(UUID tenantId);

    /**
     * Límites en 0 = sin límite. {@code billing.plans} guarda 999 integraciones para
     * "todas las del catálogo"; aquí se publica como 0 para que la consola y la landing
     * digan lo mismo ("ilimitadas").
     */
    record Granted(String plan, String nombre, int evaluacionesMes, int maxUsuarios, int maxIntegraciones,
            Set<Feature> features) {

        static final int CATALOG_WIDE = 999;

        public boolean allows(Feature feature) {
            return features.contains(feature);
        }

        public static Granted of(PlanInfo plan) {
            EnumSet<Feature> features = EnumSet.noneOf(Feature.class);
            if (plan.scoringAvanzado()) features.add(Feature.SCORING_AVANZADO);
            if (plan.apiProduccion()) features.add(Feature.API_PRODUCCION);
            if (plan.webhooks()) features.add(Feature.WEBHOOKS);
            if (plan.analytics()) features.add(Feature.ANALYTICS);
            if (plan.recomendaciones()) features.add(Feature.RECOMENDACIONES);
            return new Granted(plan.id(), plan.nombre(), plan.evaluacionesMes(), plan.maxUsuarios(),
                    plan.maxIntegraciones() >= CATALOG_WIDE ? 0 : plan.maxIntegraciones(), Set.copyOf(features));
        }

    }

}
